package com.sentinelpay.fraudengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpay.fraudengine.dto.TransactionRequest;
import com.sentinelpay.fraudengine.dto.TransactionResponse;
import com.sentinelpay.fraudengine.repository.TransactionEntity;
import com.sentinelpay.fraudengine.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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

    public FraudService(
            TransactionRepository transactionRepository,
            ReactiveRedisTemplate<String, String> redisTemplate,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.transactionRepository = transactionRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public Mono<TransactionResponse> evaluate(TransactionRequest request) {
        Instant now = Instant.now();
        Instant bucketHour = now.truncatedTo(ChronoUnit.HOURS);

        return checkVelocity(request.userId())
                .map(velocity -> computeRiskScore(request, velocity))
                .flatMap(riskScore -> {
                    String decision = riskScore > 0.8f ? "BLOCK" : riskScore > 0.5f ? "REVIEW" : "ALLOW";

                    String featuresJson;
                    try {
                        featuresJson = objectMapper.writeValueAsString(request.deviceInfo());
                    } catch (Exception e) {
                        logger.warn("Failed to serialize deviceInfo, using empty JSON", e);
                        featuresJson = "{}";
                    }

                    // ✅ FIXED: Don't set transactionId or createdAt - let DB generate them
                    TransactionEntity entity = TransactionEntity.builder()
                            .userId(UUID.fromString(request.userId()))
                            .bucketHour(bucketHour)
                            .amount(request.amount())
                            .currency(request.currency())
                            .merchantId(request.merchantId())
                            .riskScore(riskScore)
                            .decision(decision)
                            .features(featuresJson)
                            // .transactionId(null)  ← Don't set - DB auto-generates
                            // .createdAt(null)      ← Don't set - DB uses DEFAULT now()
                            .build();

                    return transactionRepository.save(entity)
                            .flatMap(savedEntity -> {
                                TransactionResponse response = new TransactionResponse(
                                        savedEntity.getTransactionId(),
                                        request.userId(),
                                        request.amount(),
                                        decision,
                                        riskScore,
                                        savedEntity.getCreatedAt() != null ? savedEntity.getCreatedAt() : now  // Use DB timestamp
                                );

                                return publishToKafka(savedEntity)
                                        .thenReturn(response);
                            });
                })
                .doOnSuccess(r -> logger.info("Transaction {} evaluated: {}", r.transactionId(), r.decision()))
                .doOnError(e -> logger.error("Error evaluating transaction: {}", e.getMessage()));
    }

    private Mono<Long> checkVelocity(String userId) {
        String key = VELOCITY_KEY.formatted(userId);

        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1L) {
                        return redisTemplate.expire(key, Duration.ofHours(1))
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .doOnError(e -> logger.warn("Redis velocity check failed for user {}: {}", userId, e.getMessage()))
                .onErrorReturn(1L);
    }

    private float computeRiskScore(TransactionRequest request, Long velocity) {
        float score = 0.0f;

        // Velocity
        if (velocity > 10) score += 0.6f;
        else if (velocity > 5) score += 0.3f;

        // Amount
        if (request.amount().compareTo(BigDecimal.valueOf(1000)) > 0) score += 0.4f;

        // Merchant
        if ("high-risk-merchant".equals(request.merchantId())) score += 0.5f;

        // IP
        if (request.ipAddress().startsWith("192.168")) score -= 0.1f;

        return Math.min(Math.max(score, 0.0f), 1.0f);
    }

    private Mono<Void> publishToKafka(TransactionEntity entity) {
        if (entity.getTransactionId() == null) {
            logger.warn("Skipping Kafka publish: transactionId is null");
            return Mono.empty();
        }

        String key = entity.getTransactionId().toString();

        return Mono.create(sink -> {
            try {
                CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(TOPIC, key, entity);

                future.whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("Kafka publish failed for key {}: {}", key, throwable.getMessage());
                    } else {
                        logger.debug("Published to Kafka: {}", key);
                    }
                    sink.success(); // Always complete successfully - fire and forget
                });
            } catch (Exception e) {
                logger.error("Unexpected error during Kafka publish for key {}: {}", key, e.getMessage());
                sink.success(); // Still complete successfully - don't block the flow
            }
        });
    }
}