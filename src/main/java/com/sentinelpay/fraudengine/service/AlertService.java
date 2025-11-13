package com.sentinelpay.fraudengine.service;

import com.sentinelpay.fraudengine.repository.TransactionEntity;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlertService {
    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

    private final WebClient webClient;  // Generic WebClient without base URL

    @Value("${alerts.slack.webhook:}")
    private String slackWebhookUrl;

    @Value("${alerts.email.endpoint:}")
    private String emailEndpoint;

    public Mono<Void> sendFraudAlert(TransactionEntity transaction) {
        if (transaction.getRiskScore() > 0.8) {
            return sendImmediateHighRiskAlert(transaction);
        } else if (transaction.getRiskScore() > 0.6) {
            return sendMediumRiskAlert(transaction);
        }
        return Mono.empty();
    }

    private Mono<Void> sendImmediateHighRiskAlert(TransactionEntity transaction) {
        logger.warn("🚨 HIGH RISK FRAUD ALERT - Transaction: {}, Score: {}, User: {}",
                transaction.getTransactionId(), transaction.getRiskScore(), transaction.getUserId());

        return Mono.when(
                sendSlackAlert(transaction, "🔴 HIGH RISK"),
                sendEmailAlert(transaction, "HIGH_RISK"),
                logToSecuritySystem(transaction)
        ).then();
    }

    private Mono<Void> sendMediumRiskAlert(TransactionEntity transaction) {
        logger.info("⚠️ MEDIUM RISK ALERT - Transaction: {}, Score: {}, User: {}",
                transaction.getTransactionId(), transaction.getRiskScore(), transaction.getUserId());

        return sendSlackAlert(transaction, "🟡 MEDIUM RISK").then();
    }

    private Mono<Void> sendSlackAlert(TransactionEntity transaction, String severity) {
        if (slackWebhookUrl == null || slackWebhookUrl.isEmpty()) {
            logger.debug("Slack webhook not configured, skipping Slack alert");
            return Mono.empty();
        }

        Map<String, Object> slackMessage = new HashMap<>();
        slackMessage.put("text", severity + " Fraud Detected - Transaction: " + transaction.getTransactionId());

        Map<String, Object> attachment = new HashMap<>();
        attachment.put("color", severity.contains("HIGH") ? "danger" : "warning");
        attachment.put("fields", new Object[]{
                Map.of("title", "Transaction ID", "value", transaction.getTransactionId(), "short", true),
                Map.of("title", "User ID", "value", transaction.getUserId(), "short", true),
                Map.of("title", "Amount", "value", transaction.getAmount(), "short", true),
                Map.of("title", "Risk Score", "value", transaction.getRiskScore(), "short", true),
                Map.of("title", "Decision", "value", transaction.getDecision(), "short", true),
                Map.of("title", "Merchant", "value", transaction.getMerchantId(), "short", true)
        });

        slackMessage.put("attachments", new Object[]{attachment});

        return webClient.post()
                .uri(slackWebhookUrl)  // Use full URL from configuration
                .bodyValue(slackMessage)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> logger.debug("Slack alert sent successfully"))
                .doOnError(error -> logger.error("Failed to send Slack alert: {}", error.getMessage()))
                .then();
    }

    private Mono<Void> sendEmailAlert(TransactionEntity transaction, String alertLevel) {
        if (emailEndpoint == null || emailEndpoint.isEmpty()) {
            logger.debug("Email endpoint not configured, skipping email alert");
            return Mono.empty();
        }

        Map<String, Object> emailData = Map.of(
                "to", "fraud-team@sentinelpay.com",
                "subject", "Fraud Alert - " + alertLevel + " - Transaction: " + transaction.getTransactionId(),
                "transaction", Map.of(
                        "id", transaction.getTransactionId(),
                        "userId", transaction.getUserId(),
                        "amount", transaction.getAmount(),
                        "riskScore", transaction.getRiskScore(),
                        "decision", transaction.getDecision(),
                        "merchant", transaction.getMerchantId()
                ),
                "alertLevel", alertLevel,
                "timestamp", Instant.now()
        );

        return webClient.post()
                .uri(emailEndpoint)  // Use full URL from configuration
                .bodyValue(emailData)
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(error -> logger.error("Failed to send email alert: {}", error.getMessage()))
                .then();
    }

    private Mono<Void> logToSecuritySystem(TransactionEntity transaction) {
        // Log to security information and event management (SIEM) system
        logger.info("SECURITY_EVENT: FRAUD_DETECTED - transaction_id: {}, user_id: {}, risk_score: {}, amount: {}",
                transaction.getTransactionId(), transaction.getUserId(), transaction.getRiskScore(), transaction.getAmount());
        return Mono.empty();
    }
}