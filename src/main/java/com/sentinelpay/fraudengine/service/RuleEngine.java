// RuleEngine.java - UPDATED VERSION
package com.sentinelpay.fraudengine.service;

import com.sentinelpay.fraudengine.dto.TransactionRequest;
import com.sentinelpay.fraudengine.repository.RuleEntity;
import com.sentinelpay.fraudengine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class RuleEngine {
    private static final Logger logger = LoggerFactory.getLogger(RuleEngine.class);

    private final RuleRepository ruleRepository;

    public Mono<RuleResult> evaluateRules(TransactionRequest request) {
        return ruleRepository.findByEnabledTrue()
                .collectList()
                .map(rules -> {
                    logger.debug("Evaluating {} active rules", rules.size());

                    RuleResult result = new RuleResult();

                    // Extract features for rule evaluation
                    Map<String, Object> features = extractFeatures(request);

                    // Evaluate each dynamic rule
                    for (RuleEntity rule : rules) {
                        if (evaluateRuleCondition(rule, features)) {
                            result.addTriggeredRule(rule.getName(), rule.getScore());
                            logger.debug("Rule triggered: {} (score: {})", rule.getName(), rule.getScore());
                        }
                    }

                    logger.debug("Rule Results - Total score: {}, Triggered rules: {}",
                            result.totalScore(), result.triggeredRules().size());

                    return result;
                });
    }

    private Map<String, Object> extractFeatures(TransactionRequest request) {
        // Extract features that rules can evaluate against
        return Map.of(
                "amount", request.amount().doubleValue(),
                "merchantId", request.merchantId(),
                "ipAddress", request.ipAddress(),
                "deviceInfo", request.deviceInfo(),
                // Add any other features your rules might need
                "deviceRisk", calculateDeviceRisk(request.deviceInfo())
        );
    }

    private boolean evaluateRuleCondition(RuleEntity rule, Map<String, Object> features) {
        try {
            // Simple condition evaluation - you might want a more sophisticated evaluator
            String condition = rule.getCondition();

            // Basic condition evaluation (this is simplified - you might want a proper expression evaluator)
            if (condition.contains("amount >")) {
                double threshold = extractNumber(condition);
                double amount = (Double) features.get("amount");
                return amount > threshold;
            }
            else if (condition.contains("deviceRisk >")) {
                double threshold = extractNumber(condition);
                double deviceRisk = (Double) features.get("deviceRisk");
                return deviceRisk > threshold;
            }
            else if (condition.contains("merchantId ==") || condition.contains("merchantId.equals")) {
                // Merchant risk evaluation
                String merchantId = (String) features.get("merchantId");
                return isRiskyMerchant(merchantId);
            }
            else if (condition.contains("ipRisk >")) {
                // IP risk evaluation
                String ipAddress = (String) features.get("ipAddress");
                return isRiskyIP(ipAddress);
            }

            // Add more condition types as needed

            return false;
        } catch (Exception e) {
            logger.warn("Failed to evaluate rule condition: {} - {}", rule.getName(), e.getMessage());
            return false;
        }
    }

    private double calculateDeviceRisk(Map<String, Object> deviceInfo) {
        String browser = (String) deviceInfo.getOrDefault("browser", "unknown");
        return "unknown".equalsIgnoreCase(browser) ? 0.6f : 0.2f;
    }

    private double extractNumber(String condition) {
        // Extract numeric threshold from condition string
        try {
            String[] parts = condition.split(">");
            if (parts.length > 1) {
                return Double.parseDouble(parts[1].trim());
            }
        } catch (Exception e) {
            logger.warn("Failed to extract number from condition: {}", condition);
        }
        return 0.0;
    }

    private boolean isRiskyMerchant(String merchantId) {
        return switch (merchantId.toLowerCase()) {
            case "high-risk-merchant", "casino", "crypto-exchange" -> true;
            default -> false;
        };
    }

    private boolean isRiskyIP(String ipAddress) {
        return ipAddress.startsWith("203.0.113."); // Example risky IP range
    }

    public record RuleResult(Map<String, Float> triggeredRules) {
        public RuleResult() {
            this(new java.util.HashMap<>());
        }

        public void addTriggeredRule(String ruleName, Double score) {
            triggeredRules.put(ruleName, score.floatValue());
        }

        public double totalScore() {
            return triggeredRules.values().stream()
                    .mapToDouble(Float::doubleValue)
                    .sum();
        }

        public Map<String, Float> triggeredRules() {
            return new java.util.HashMap<>(triggeredRules);
        }
    }
}