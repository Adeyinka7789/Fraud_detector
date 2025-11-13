package com.sentinelpay.fraudengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpay.fraudengine.dto.TransactionRequest;
import com.sentinelpay.fraudengine.dto.TransactionResponse;
import com.sentinelpay.fraudengine.repository.TransactionEntity;
import com.sentinelpay.fraudengine.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

@Service
public class FraudService {

    private static final Logger logger = LoggerFactory.getLogger(FraudService.class);
    private final TransactionRepository transactionRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final String VELOCITY_KEY = "velocity:user:%s";
    private static final String TOPIC = "fraud.transactions";
    private final MLServiceClient mlServiceClient;
    private final RuleEngine ruleEngine;
    private final ReactiveCircuitBreaker fraudCircuitBreaker;
    private final AlertService alertService;

    public FraudService(
            TransactionRepository transactionRepository,
            ReactiveRedisTemplate<String, String> redisTemplate,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MLServiceClient mlServiceClient,
            RuleEngine ruleEngine,
            AlertService alertService,
            ReactiveCircuitBreakerFactory circuitBreakerFactory) {
        this.transactionRepository = transactionRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.mlServiceClient = mlServiceClient;
        this.ruleEngine = ruleEngine;
        this.alertService = alertService;
        this.fraudCircuitBreaker = circuitBreakerFactory.create("fraudEngine");
    }

    /**
     * Evaluates a transaction request using velocity checks, the Rule Engine, and an ML Service.
     * The entire flow is wrapped in a Circuit Breaker for resilience.
     */
    public Mono<TransactionResponse> evaluate(TransactionRequest request) {
        Instant now = Instant.now();
        Instant bucketHour = now.truncatedTo(ChronoUnit.HOURS);

        return fraudCircuitBreaker.run(
                checkVelocity(request.userId())
                        .flatMap(velocity -> {
                            // Build features for ML service
                            Map<String, Object> mlFeatures = buildMLFeatures(request, velocity);

                            return Mono.zip(
                                    // Rule Engine: Let Resilience4j handle timeout
                                    ruleEngine.evaluateRules(request),

                                    // ML Service: Let Resilience4j handle timeout
                                    mlServiceClient.getRiskScore(mlFeatures),

                                    Mono.just(velocity),
                                    Mono.just(mlFeatures)
                            );
                        })
                        .flatMap(tuple -> {
                            RuleEngine.RuleResult ruleResult = tuple.getT1();
                            Float mlRiskScore = tuple.getT2();
                            Long velocity = tuple.getT3();
                            Map<String, Object> mlFeatures = tuple.getT4();

                            // Fusion logic: Combine ML score and rule score
                            float finalRiskScore = fuseScores(mlRiskScore, (float) ruleResult.totalScore(), velocity);
                            String decision = makeDecision(finalRiskScore, ruleResult);

                            logger.debug("Starting database save and Kafka publish...");
                            long saveStartTime = System.currentTimeMillis();

                            return saveAndProcessTransaction(request, bucketHour, now,
                                    finalRiskScore, decision, mlFeatures)
                                    .doOnSuccess(r -> {
                                        long saveDuration = System.currentTimeMillis() - saveStartTime;
                                        logger.debug("Database save and Kafka publish completed in {}ms", saveDuration);
                                    });
                        })
                        .doOnSuccess(r -> logger.info("Transaction {} evaluated: {}", r.transactionId(), r.decision()))
                        .onErrorResume(error -> {
                            logger.error("Fraud evaluation failed (inner error), defaulting to REVIEW: {}", error.getMessage());
                            return createFallbackResponse(request, now);
                        }),
                // Circuit Breaker Fallback
                throwable -> {
                    logger.error("Fraud engine circuit breaker triggered, using fallback: {}", throwable.getMessage());
                    return createFallbackResponse(request, Instant.now());
                }
        );
    }

    /**
     * Helper method to persist the transaction result and publish to Kafka.
     */
    private Mono<TransactionResponse> saveAndProcessTransaction(
            TransactionRequest request,
            Instant bucketHour,
            Instant now,
            float finalRiskScore,
            String decision,
            Map<String, Object> mlFeatures) {

        String featuresJson;
        String deviceInfoJson;
        try {
            featuresJson = objectMapper.writeValueAsString(mlFeatures);
            deviceInfoJson = objectMapper.writeValueAsString(request.deviceInfo());
        } catch (Exception e) {
            logger.warn("Failed to serialize features/deviceInfo, using empty JSON", e);
            featuresJson = "{}";
            deviceInfoJson = "{}";
        }

        // Convert amount to BigDecimal to match entity
        BigDecimal amount = request.amount() instanceof BigDecimal ?
                (BigDecimal) request.amount() :
                BigDecimal.valueOf(request.amount().doubleValue());

        // Convert userId from String to UUID
        UUID userId = UUID.fromString(request.userId());

        TransactionEntity entity = TransactionEntity.builder()
                .userId(userId)
                .bucketHour(bucketHour)
                .amount(amount)
                .currency(request.currency())
                .merchantId(request.merchantId())
                .ipAddress(request.ipAddress())
                .deviceInfo(deviceInfoJson)
                .riskScore(finalRiskScore)
                .decision(decision)
                .features(featuresJson)
                .createdAt(now)
                .timestamp(now)
                .build();

        return transactionRepository.save(entity)
                .flatMap(savedEntity -> {
                    TransactionResponse response = new TransactionResponse(
                            savedEntity.getTransactionId(),
                            savedEntity.getUserId().toString(),
                            savedEntity.getAmount(),
                            decision,
                            finalRiskScore,
                            now
                    );

                    alertService.sendFraudAlert(savedEntity)
                            .subscribe();

                    // Non-blocking Kafka publish - don't wait for completion
                    publishToKafkaNonBlocking(savedEntity);
                    return Mono.just(response);
                })
                .doOnError(e -> logger.error("Error during save/publish for transaction: {}", e.getMessage()));
    }

    private Map<String, Object> buildMLFeatures(TransactionRequest request, Long velocity) {
        Map<String, Object> features = new HashMap<>();
        features.put("amount", request.amount().doubleValue());
        features.put("velocity_1h", velocity);
        features.put("merchant_risk", getMerchantRisk(request.merchantId()));
        features.put("ip_risk", getIPRisk(request.ipAddress()));
        features.put("device_risk", getDeviceRisk(request.deviceInfo()));
        features.put("hour_of_day", Instant.now().atZone(ZoneId.systemDefault()).getHour());
        features.put("day_of_week", Instant.now().atZone(ZoneId.systemDefault()).getDayOfWeek().getValue());
        return features;
    }

    /**
     * Fuses the ML score, Rule Engine score, and velocity into a single final risk score.
     */
    private float fuseScores(Float mlScore, float ruleScore, Long velocity) {
        if (mlScore == null) {
            mlScore = 0.0f;
        }

        float baseScore = (mlScore * 0.7f) + (ruleScore * 0.3f);

        if (velocity > 20) baseScore += 0.2f;
        else if (velocity > 10) baseScore += 0.1f;

        return Math.min(Math.max(baseScore, 0.0f), 1.0f);
    }

    /**
     * Determines the final decision based on the fused risk score and rule results.
     */
    private String makeDecision(float riskScore, RuleEngine.RuleResult ruleResult) {
        if (ruleResult.totalScore() > 0.7f) {
            return "BLOCK";
        }

        if (riskScore > 0.8f) return "BLOCK";
        if (riskScore > 0.5f) return "REVIEW";
        return "ALLOW";
    }

    /**
     * Simple dummy logic to assign a risk score to a merchant.
     */
    private float getMerchantRisk(String merchantId) {
        return switch (merchantId.toLowerCase()) {
            case "high-risk-merchant" -> 0.8f;
            case "casino", "crypto-exchange" -> 0.7f;
            case "premium-retailer" -> 0.1f;
            default -> 0.3f;
        };
    }

    /**
     * Simple dummy logic to assign a risk score to an IP address.
     */
    private float getIPRisk(String ipAddress) {
        if (ipAddress.startsWith("192.168") || ipAddress.startsWith("10.")) {
            return 0.1f;
        }
        if (ipAddress.startsWith("203.0.113.")) {
            return 0.8f;
        }
        return 0.4f;
    }

    /**
     * Simple dummy logic to assign a risk score based on device information.
     */
    private float getDeviceRisk(Map<String, Object> deviceInfo) {
        String browser = (String) deviceInfo.getOrDefault("browser", "unknown");
        return "unknown".equalsIgnoreCase(browser) ? 0.6f : 0.2f;
    }

    /**
     * Creates a safe default response when the fraud evaluation process fails (due to timeout or circuit breaker).
     */
    private Mono<TransactionResponse> createFallbackResponse(TransactionRequest request, Instant now) {
        UUID transactionId = UUID.randomUUID();
        UUID userId = UUID.fromString(request.userId());
        BigDecimal amount = request.amount() instanceof BigDecimal ?
                (BigDecimal) request.amount() :
                BigDecimal.valueOf(request.amount().doubleValue());

        return Mono.just(new TransactionResponse(
                transactionId,
                userId.toString(),
                amount,
                "REVIEW",
                0.5f,
                now
        ));
    }

    /**
     * Checks and increments the user's velocity count in Redis.
     */
    private Mono<Long> checkVelocity(String userId) {
        String key = VELOCITY_KEY.formatted(userId);

        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1L) {
                        return redisTemplate.expire(key, java.time.Duration.ofHours(1))
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .doOnError(e -> logger.warn("Redis velocity check failed for user {}: {}", userId, e.getMessage()))
                .onErrorReturn(1L);
    }

    /**
     * Publishes the saved transaction entity to the Kafka topic - NON-BLOCKING version.
     * This method doesn't wait for Kafka to complete, making the main flow much faster.
     */
    private void publishToKafkaNonBlocking(TransactionEntity entity) {
        if (entity.getTransactionId() == null) {
            logger.warn("Skipping Kafka publish: transactionId is null");
            return;
        }

        String key = entity.getTransactionId().toString();

        try {
            // Fire and forget - use CompletableFuture with proper exception handling
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(TOPIC, key, entity);

            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.error("Kafka publish failed for key {}: {}", key, throwable.getMessage());
                } else {
                    logger.debug("Published to Kafka: {}", key);
                }
            });
        } catch (Exception e) {
            logger.error("Unexpected error during Kafka publish for key {}: {}", key, e.getMessage());
        }
    }
}