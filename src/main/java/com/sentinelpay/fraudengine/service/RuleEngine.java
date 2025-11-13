package com.sentinelpay.fraudengine.service;

import com.sentinelpay.fraudengine.dto.TransactionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RuleEngine {

    private static final Logger logger = LoggerFactory.getLogger(RuleEngine.class);

    private final ReactiveCircuitBreaker ruleCircuitBreaker;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    // Rule configurations (could be moved to database)
    private final Map<String, Object> ruleConfig = Map.of(
            "high_amount_threshold", 1000.0,
            "velocity_threshold_1h", 10L,
            "velocity_threshold_24h", 50L,
            "risky_merchants", List.of("high-risk-merchant", "casino", "crypto-exchange")
    );

    public RuleEngine(ReactiveCircuitBreakerFactory circuitBreakerFactory,
                      ReactiveRedisTemplate<String, String> redisTemplate) {
        this.ruleCircuitBreaker = circuitBreakerFactory.create("ruleEngine");
        this.redisTemplate = redisTemplate;
    }

    public Mono<RuleResult> evaluateRules(TransactionRequest request) {
        logger.debug("RuleEngine: Starting evaluation for user: {}", request.userId());

        return ruleCircuitBreaker.run(
                Mono.zip(
                                checkAmountRule(request),
                                checkVelocityRule(request),
                                checkMerchantRule(request),
                                checkGeoRule(request)
                        )
                        .map(tuple -> {
                            List<Rule> triggeredRules = new ArrayList<>();
                            float totalScore = 0.0f;

                            Rule amountRule = tuple.getT1();
                            Rule velocityRule = tuple.getT2();
                            Rule merchantRule = tuple.getT3();
                            Rule geoRule = tuple.getT4();

                            // Log individual rule results
                            logger.debug("Rule Results - Amount: {} (score: {}), Velocity: {} (score: {}), Merchant: {} (score: {}), Geo: {} (score: {})",
                                    amountRule.name(), amountRule.score(),
                                    velocityRule.name(), velocityRule.score(),
                                    merchantRule.name(), merchantRule.score(),
                                    geoRule.name(), geoRule.score());

                            if (amountRule.triggered()) {
                                triggeredRules.add(amountRule);
                                totalScore += amountRule.score();
                            }
                            if (velocityRule.triggered()) {
                                triggeredRules.add(velocityRule);
                                totalScore += velocityRule.score();
                            }
                            if (merchantRule.triggered()) {
                                triggeredRules.add(merchantRule);
                                totalScore += merchantRule.score();
                            }
                            if (geoRule.triggered()) {
                                triggeredRules.add(geoRule);
                                totalScore += geoRule.score();
                            }

                            logger.debug("Total rule score: {}, Triggered rules: {}", totalScore, triggeredRules.size());
                            return new RuleResult(triggeredRules, Math.min(totalScore, 1.0f));
                        })
                        .timeout(Duration.ofMillis(50)) // Increased from 20ms to 50ms
                        .doOnSuccess(result -> logger.debug("RuleEngine: Completed with score {}", result.totalScore()))
                        .doOnError(error -> logger.debug("RuleEngine: Error - {}", error.getMessage())),
                throwable -> {
                    logger.warn("Rule engine circuit breaker fallback triggered: {}", throwable.getMessage());
                    return Mono.just(new RuleResult(List.of(), 0.0f));
                }
        );
    }

    private Mono<Rule> checkAmountRule(TransactionRequest request) {
        double threshold = (Double) ruleConfig.get("high_amount_threshold");
        boolean triggered = request.amount().compareTo(BigDecimal.valueOf(threshold)) > 0;
        return Mono.just(new Rule("HIGH_AMOUNT", "Transaction exceeds amount threshold",
                triggered, triggered ? 0.4f : 0.0f));
    }

    private Mono<Rule> checkVelocityRule(TransactionRequest request) {
        String key1h = "velocity:1h:" + request.userId();
        String key24h = "velocity:24h:" + request.userId();

        return Mono.zip(
                redisTemplate.opsForValue().get(key1h).defaultIfEmpty("0").map(Long::parseLong),
                redisTemplate.opsForValue().get(key24h).defaultIfEmpty("0").map(Long::parseLong)
        ).map(counts -> {
            long count1h = counts.getT1();
            long count24h = counts.getT2();
            long threshold1h = (Long) ruleConfig.get("velocity_threshold_1h");
            long threshold24h = (Long) ruleConfig.get("velocity_threshold_24h");

            boolean triggered = count1h > threshold1h || count24h > threshold24h;
            float score = 0.0f;
            if (count1h > threshold1h * 2) score += 0.6f;
            else if (count1h > threshold1h) score += 0.3f;

            return new Rule("HIGH_VELOCITY",
                    String.format("Velocity check: 1h=%d, 24h=%d", count1h, count24h),
                    triggered, score);
        });
    }

    private Mono<Rule> checkMerchantRule(TransactionRequest request) {
        @SuppressWarnings("unchecked")
        List<String> riskyMerchants = (List<String>) ruleConfig.get("risky_merchants");
        boolean triggered = riskyMerchants.contains(request.merchantId().toLowerCase());
        return Mono.just(new Rule("RISKY_MERCHANT", "Merchant in high-risk category",
                triggered, triggered ? 0.5f : 0.0f));
    }

    private Mono<Rule> checkGeoRule(TransactionRequest request) {
        // Simple geo-rule: internal IPs are safer
        boolean isInternalIP = request.ipAddress().startsWith("192.168") ||
                request.ipAddress().startsWith("10.") ||
                request.ipAddress().startsWith("172.16");
        return Mono.just(new Rule("GEO_LOCATION",
                isInternalIP ? "Internal network IP" : "External IP",
                !isInternalIP, isInternalIP ? -0.1f : 0.1f));
    }

    // Rule and RuleResult classes
    public record Rule(String name, String description, boolean triggered, float score) {}

    public record RuleResult(List<Rule> triggeredRules, float totalScore) {
        public boolean isTriggered() {
            return totalScore > 0.3f;
        }
    }
}