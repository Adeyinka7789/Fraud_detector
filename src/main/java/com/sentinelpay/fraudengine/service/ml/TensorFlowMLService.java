package com.sentinelpay.fraudengine.service.ml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Service
public class TensorFlowMLService {
    private static final Logger logger = LoggerFactory.getLogger(TensorFlowMLService.class);

    public TensorFlowMLService() {
        logger.info("TensorFlow ML Service initialized (mock mode)");
    }

    public Mono<Float> predictFraudRisk(Map<String, Object> features) {
        // For now, use a sophisticated mock that will be replaced with real TensorFlow
        return Mono.fromCallable(() -> {
            logger.debug("Using enhanced mock ML prediction");

            // Extract features
            double amount = (Double) features.get("amount");
            long velocity = (Long) features.get("velocity_1h");
            float merchantRisk = (Float) features.get("merchant_risk");
            float ipRisk = (Float) features.get("ip_risk");
            float deviceRisk = (Float) features.get("device_risk");
            int hour = (Integer) features.get("hour_of_day");
            int dayOfWeek = (Integer) features.get("day_of_week");

            // Simulate a "neural network-like" calculation
            double baseScore = 0.0;

            // Amount contribution (sigmoid-like)
            baseScore += 1.0 / (1.0 + Math.exp(-(amount - 2000) / 1000)) * 0.3;

            // Velocity contribution
            baseScore += Math.min(velocity / 10.0 * 0.2, 0.2);

            // Risk scores
            baseScore += merchantRisk * 0.3;
            baseScore += ipRisk * 0.2;
            baseScore += deviceRisk * 0.1;

            // Time-based patterns (weekends and nights are riskier)
            boolean isWeekend = dayOfWeek >= 5; // 5=Saturday, 6=Sunday
            boolean isNight = hour < 6 || hour > 22;

            if (isWeekend) baseScore += 0.1;
            if (isNight) baseScore += 0.1;

            // Ensure score is between 0 and 1
            float finalScore = (float) Math.min(Math.max(baseScore, 0.0), 1.0);

            logger.debug("Enhanced mock prediction: {}", finalScore);
            return finalScore;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}