package com.sentinelpay.fraudengine.service.ml;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference; // <--- NEW IMPORT
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ModelRetrainingService {
    private static final Logger logger = LoggerFactory.getLogger(ModelRetrainingService.class);

    private final WebClient trainingWebClient;  // Use the injected WebClient

    @Value("${ml.retraining.enabled:true}")
    private boolean retrainingEnabled;

    private LocalDateTime lastRetraining;

    /**
     * Retrain model every 24 hours
     */
    @Scheduled(fixedRate = 24 * 60 * 60 * 1000) // 24 hours
    public void scheduledRetraining() {
        if (retrainingEnabled) {
            retrainModel()
                    .subscribe(
                            success -> logger.info("Scheduled model retraining completed successfully"),
                            error -> logger.error("Scheduled model retraining failed: {}", error.getMessage())
                    );
        }
    }

    /**
     * Manual trigger for model retraining
     */
    public Mono<String> retrainModel() {
        logger.info("Starting model retraining...");

        return trainingWebClient.post()  // Use trainingWebClient, not webClient
                .uri("/retrain")  // Remove base URL since it's configured in WebClient
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> {
                    logger.info("Model retraining completed: {}", response);
                    lastRetraining = LocalDateTime.now();
                    // Trigger model reload in TensorFlow service
                })
                .doOnError(error -> logger.error("Model retraining failed: {}", error.getMessage()));
    }

    /**
     * Check if model needs retraining based on performance drift
     */
    @Scheduled(fixedRate = 60 * 60 * 1000) // 1 hour
    public void checkModelPerformance() {
        // Implement model performance monitoring
        if (retrainingEnabled) {
            getModelMetrics()
                    .subscribe(
                            metrics -> evaluateModelPerformance(metrics),
                            error -> logger.warn("Failed to check model performance: {}", error.getMessage())
                    );
        }
    }

    private void evaluateModelPerformance(Map<String, Object> metrics) {
        // Check if model needs retraining based on metrics
        Double accuracy = (Double) metrics.get("accuracy");
        if (accuracy != null && accuracy < 0.85) {
            logger.warn("Model accuracy below threshold ({}), triggering retraining", accuracy);
            retrainModel().subscribe();
        }
    }

    public LocalDateTime getLastRetraining() {
        return lastRetraining;
    }

    public Mono<Map<String, Object>> getModelMetrics() {
        return trainingWebClient.get()  // Use trainingWebClient
                .uri("/metrics")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnError(error -> logger.error("Failed to get model metrics: {}", error.getMessage()));
    }
}