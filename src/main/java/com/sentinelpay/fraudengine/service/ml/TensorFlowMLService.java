package com.sentinelpay.fraudengine.service.ml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Service
public class TensorFlowMLService {
    private static final Logger logger = LoggerFactory.getLogger(TensorFlowMLService.class);

    private final String modelPath;
    private final boolean modelLoaded;

    // Use constructor injection - more reliable than @Value on field
    public TensorFlowMLService(
            @Value("${tensorflow.model.path:src/main/resources/models/fraud_model}") String modelPath) {
        this.modelPath = modelPath;
        logger.info("🧠 TensorFlowMLService initialized with model path: {}", this.modelPath);
        this.modelLoaded = checkModelExists();

        if (modelLoaded) {
            logger.info("✅ TensorFlow model directory found at: {}", this.modelPath);
        } else {
            logger.info("🔄 Using enhanced mock ML predictions - no model files found");
        }
    }

    private boolean checkModelExists() {
        try {
            logger.debug("Checking model directory: {}", Paths.get(modelPath).toAbsolutePath());

            Path modelDir = Paths.get(modelPath);
            boolean dirExists = Files.exists(modelDir);
            boolean hasModelFile = dirExists && Files.exists(modelDir.resolve("saved_model.pb"));

            logger.debug("Model directory exists: {}, has model file: {}", dirExists, hasModelFile);
            return hasModelFile;

        } catch (Exception e) {
            logger.warn("Could not check TensorFlow model: {}", e.getMessage());
            return false;
        }
    }

    public Mono<Float> predictFraudRisk(Map<String, Object> features) {
        // For now, always use enhanced mock since we don't have actual TensorFlow model files
        // When you have real .pb files, you can switch to real TensorFlow here
        return getEnhancedMockPrediction(features);
    }

    private Mono<Float> getEnhancedMockPrediction(Map<String, Object> features) {
        return Mono.fromCallable(() -> {
            logger.debug("🤖 Enhanced mock ML prediction for {} features", features.size());

            // Extract features with null checks
            double amount = getDoubleSafe(features, "amount");
            long velocity = getLongSafe(features, "velocity_1h");
            float merchantRisk = getFloatSafe(features, "merchant_risk");
            float ipRisk = getFloatSafe(features, "ip_risk");
            float deviceRisk = getFloatSafe(features, "device_risk");
            int hour = getIntSafe(features, "hour_of_day");
            int dayOfWeek = getIntSafe(features, "day_of_week");

            logger.debug("Features - Amount: {}, Velocity: {}, MerchantRisk: {}, IPRisk: {}, DeviceRisk: {}, Hour: {}, Day: {}",
                    amount, velocity, merchantRisk, ipRisk, deviceRisk, hour, dayOfWeek);

            // Enhanced neural network simulation
            double baseScore = 0.0;

            // Non-linear amount contribution (sigmoid-like)
            baseScore += 1.0 / (1.0 + Math.exp(-(amount - 1500) / 800)) * 0.4;

            // Velocity with diminishing returns (tanh-like)
            baseScore += Math.tanh(velocity / 15.0) * 0.3;

            // Interactive features
            baseScore += merchantRisk * 0.25;
            baseScore += ipRisk * 0.2;
            baseScore += deviceRisk * 0.15;

            // Amount-risk interactions (feature crosses)
            if (amount > 5000 && merchantRisk > 0.6) baseScore += 0.15;
            if (amount > 10000 && velocity > 5) baseScore += 0.2;
            if (deviceRisk > 0.5 && ipRisk > 0.6) baseScore += 0.1;

            // Time-based patterns
            boolean isWeekend = dayOfWeek >= 5;
            boolean isNight = hour < 6 || hour > 22;
            boolean isRushHour = (hour >= 8 && hour <= 10) || (hour >= 17 && hour <= 19);

            if (isWeekend) baseScore += 0.08;
            if (isNight) baseScore += 0.12;
            if (isRushHour) baseScore -= 0.05; // Lower risk during business hours

            // Ensure score is between 0 and 1
            float finalScore = (float) Math.min(Math.max(baseScore, 0.0), 1.0);

            logger.debug("📊 Enhanced mock prediction score: {}", finalScore);
            return finalScore;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // Safe getter methods
    private double getDoubleSafe(Map<String, Object> features, String key) {
        Object value = features.get(key);
        if (value == null) {
            logger.warn("⚠️ Missing feature: {}, using default 0.0", key);
            return 0.0;
        }
        return (value instanceof Number) ? ((Number) value).doubleValue() : 0.0;
    }

    private long getLongSafe(Map<String, Object> features, String key) {
        Object value = features.get(key);
        if (value == null) {
            logger.warn("⚠️ Missing feature: {}, using default 0", key);
            return 0L;
        }
        return (value instanceof Number) ? ((Number) value).longValue() : 0L;
    }

    private float getFloatSafe(Map<String, Object> features, String key) {
        Object value = features.get(key);
        if (value == null) {
            logger.warn("⚠️ Missing feature: {}, using default 0.0f", key);
            return 0.0f;
        }
        return (value instanceof Number) ? ((Number) value).floatValue() : 0.0f;
    }

    private int getIntSafe(Map<String, Object> features, String key) {
        Object value = features.get(key);
        if (value == null) {
            logger.warn("⚠️ Missing feature: {}, using default 0", key);
            return 0;
        }
        return (value instanceof Number) ? ((Number) value).intValue() : 0;
    }

    /**
     * Get service status for monitoring
     */
    public Map<String, Object> getStatus() {
        return Map.of(
                "modelLoaded", modelLoaded,
                "modelPath", modelPath,
                "service", "enhanced_mock",
                "description", "Using sophisticated mock ML predictions"
        );
    }
}