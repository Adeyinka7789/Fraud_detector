// TransactionSearchService.java
package com.sentinelpay.fraudengine.service;

import com.sentinelpay.fraudengine.dto.SearchRequest;
import com.sentinelpay.fraudengine.dto.TransactionResponse;
import com.sentinelpay.fraudengine.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class TransactionSearchService {
    private final TransactionRepository transactionRepository;

    public Flux<TransactionResponse> searchTransactions(SearchRequest request) {
        return transactionRepository.searchTransactions(
                request.userId().orElse(null),
                request.decision().orElse(null),
                request.fromDate().orElse(null),
                request.toDate().orElse(null),
                request.limit().orElse(100)
        ).map(this::toResponse);
    }

    private TransactionResponse toResponse(com.sentinelpay.fraudengine.repository.TransactionEntity entity) {
        return new TransactionResponse(
                entity.getTransactionId(),
                entity.getUserId().toString(),
                entity.getAmount(),
                entity.getDecision(),
                entity.getRiskScore(),
                entity.getCreatedAt()
        );
    }
}