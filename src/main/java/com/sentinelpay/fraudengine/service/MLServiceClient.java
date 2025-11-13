package com.sentinelpay.fraudengine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sentinelpay.fraudengine.service.ml.TensorFlowMLService;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Random;

// MLServiceClient.java - UPDATED
@Service
public class MLServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(MLServiceClient.class);
    private final ReactiveCircuitBreaker mlCircuitBreaker;
    private final TensorFlowMLService tensorFlowService;
    private final Random random = new Random();

    public MLServiceClient(ReactiveCircuitBreakerFactory circuitBreakerFactory,
                           TensorFlowMLService tensorFlowService) {
        this.mlCircuitBreaker = circuitBreakerFactory.create("mlService");
        this.tensorFlowService = tensorFlowService;
    }

    public Mono<Float> getRiskScore(Map<String, Object> features) {
        return mlCircuitBreaker.run(
                // Use TensorFlow for real predictions
                tensorFlowService.predictFraudRisk(features)
                        .timeout(Duration.ofMillis(100)) // TF should be fast
                        .onErrorResume(error -> {
                            logger.warn("TensorFlow service failed, using fallback: {}", error.getMessage());
                            return getFallbackScore(features); // Use old mock as fallback
                        }),
                throwable -> {
                    logger.warn("ML Service circuit breaker fallback");
                    return Mono.just(0.5f);
                }
        );
    }

    // Keep the old mock as fallback
    private Mono<Float> getFallbackScore(Map<String, Object> features) {
        return Mono.fromCallable(() -> {
            logger.debug("Using fallback ML scoring for features: {}", features.keySet());

            float baseScore = random.nextFloat() * 0.6f;

            // Simple heuristic fallback
            double amount = (Double) features.get("amount");
            if (amount > 5000) baseScore += 0.3f;
            if (amount > 1000) baseScore += 0.2f;

            float merchantRisk = (Float) features.get("merchant_risk");
            baseScore += merchantRisk * 0.3f;

            float ipRisk = (Float) features.get("ip_risk");
            baseScore += ipRisk * 0.2f;

            float deviceRisk = (Float) features.get("device_risk");
            baseScore += deviceRisk * 0.1f;

            long velocity = (Long) features.get("velocity_1h");
            if (velocity > 5) baseScore += 0.1f;
            if (velocity > 10) baseScore += 0.2f;

            return Math.min(Math.max(baseScore, 0.0f), 1.0f);
        });
    }
}