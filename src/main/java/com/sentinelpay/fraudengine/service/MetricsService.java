// MetricsService.java
package com.sentinelpay.fraudengine.service;

import com.sentinelpay.fraudengine.dto.MetricsResponse;
import com.sentinelpay.fraudengine.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MetricsService {
    private final TransactionRepository transactionRepository;

    public Mono<MetricsResponse> getMetrics() {
        return Mono.zip(
                transactionRepository.countAll(),
                transactionRepository.countByDecision("BLOCK"),
                transactionRepository.countByDecision("REVIEW"),
                transactionRepository.countByDecision("ALLOW")
        ).map(tuple -> {
            long total = tuple.getT1();
            long blocked = tuple.getT2();
            long review = tuple.getT3();
            long allowed = tuple.getT4();

            double fraudRate = total > 0 ? (double) (blocked + review) / total * 100 : 0.0;

            return new MetricsResponse(
                    total,
                    blocked,
                    review,
                    allowed,
                    Math.round(fraudRate * 100.0) / 100.0
            );
        });
    }
}