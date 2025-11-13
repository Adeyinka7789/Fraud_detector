// UserProfileService.java
package com.sentinelpay.fraudengine.service;

import com.sentinelpay.fraudengine.dto.UserProfile;
import com.sentinelpay.fraudengine.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserProfileService {
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    public Mono<UserProfile> buildUserProfile(String userId) {
        UUID userUuid = UUID.fromString(userId);
        Instant thirtyDaysAgo = Instant.now().minusSeconds(30 * 24 * 60 * 60);

        return Mono.zip(
                transactionRepository.findAverageAmount(userUuid),
                transactionRepository.findUserLocations(userUuid).collectList(),
                transactionRepository.findRecentByUser(userUuid, thirtyDaysAgo).collectList()
        ).map(tuple -> {
            Double avgAmount = tuple.getT1();
            List<String> locations = tuple.getT2();
            List<com.sentinelpay.fraudengine.repository.TransactionEntity> transactions = tuple.getT3();

            // Extract devices from transactions
            List<String> devices = extractDevices(transactions);

            // Build behavior patterns
            Map<String, Object> patterns = buildBehaviorPatterns(transactions);

            return new UserProfile(
                    userId,
                    BigDecimal.valueOf(avgAmount != null ? avgAmount : 0.0),
                    locations,
                    devices,
                    patterns,
                    Instant.now()
            );
        });
    }

    private List<String> extractDevices(List<com.sentinelpay.fraudengine.repository.TransactionEntity> transactions) {
        return transactions.stream()
                .map(transaction -> {
                    try {
                        Map deviceInfo = objectMapper.readValue(transaction.getDeviceInfo(), Map.class);
                        return (String) deviceInfo.getOrDefault("browser", "unknown");
                    } catch (Exception e) {
                        return "unknown";
                    }
                })
                .distinct()
                .toList();
    }

    private Map<String, Object> buildBehaviorPatterns(List<com.sentinelpay.fraudengine.repository.TransactionEntity> transactions) {
        Map<String, Object> patterns = new HashMap<>();

        if (transactions.isEmpty()) {
            return patterns;
        }

        // Calculate transaction frequency
        long transactionCount = transactions.size();
        patterns.put("transactionCount", transactionCount);
        patterns.put("transactionFrequency", transactionCount / 30.0); // per day

        // Calculate risk pattern
        long highRiskCount = transactions.stream()
                .filter(t -> t.getRiskScore() != null && t.getRiskScore() > 0.7)
                .count();
        patterns.put("highRiskPercentage", (double) highRiskCount / transactionCount * 100);

        // Most common decision
        Map<String, Long> decisionCounts = transactions.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        com.sentinelpay.fraudengine.repository.TransactionEntity::getDecision,
                        java.util.stream.Collectors.counting()
                ));
        patterns.put("commonDecision",
                decisionCounts.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("UNKNOWN")
        );

        return patterns;
    }
}