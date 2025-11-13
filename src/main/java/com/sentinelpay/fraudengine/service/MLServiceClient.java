package com.sentinelpay.fraudengine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.Random;

@Service
public class MLServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(MLServiceClient.class);
    private final ReactiveCircuitBreaker mlCircuitBreaker;
    private final Random random = new Random();

    public MLServiceClient(ReactiveCircuitBreakerFactory circuitBreakerFactory) {
        this.mlCircuitBreaker = circuitBreakerFactory.create("mlService");
    }

    public Mono<Float> getRiskScore(Map<String, Object> features) {
        return mlCircuitBreaker.run(
                Mono.fromCallable(() -> {
                    logger.debug("ML Service: Simulating risk score for features: {}", features.keySet());

                    // Simulate ML processing delay
                    try {
                        Thread.sleep(random.nextInt(50)); // 0-50ms delay
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // Simulate ML model prediction - return Float, not Double
                    float baseScore = random.nextFloat() * 0.6f; // 0.0 - 0.6

                    // Adjust based on features
                    double amount = (Double) features.get("amount");
                    if (amount > 1000) baseScore += 0.2f;
                    if (amount > 5000) baseScore += 0.3f;

                    float merchantRisk = (Float) features.get("merchant_risk");
                    baseScore += merchantRisk * 0.3f;

                    float ipRisk = (Float) features.get("ip_risk");
                    baseScore += ipRisk * 0.2f;

                    float deviceRisk = (Float) features.get("device_risk");
                    baseScore += deviceRisk * 0.1f;

                    long velocity = (Long) features.get("velocity_1h");
                    if (velocity > 5) baseScore += 0.1f;
                    if (velocity > 10) baseScore += 0.2f;

                    // Ensure score is between 0 and 1
                    float finalScore = Math.min(Math.max(baseScore, 0.0f), 1.0f);

                    logger.debug("ML Service: Returning risk score: {}", finalScore);
                    return finalScore;
                }),
                throwable -> {
                    logger.warn("ML Service: Circuit breaker fallback, using default score");
                    return Mono.just(0.5f); // Return Float, not Double
                }
        );
    }
}