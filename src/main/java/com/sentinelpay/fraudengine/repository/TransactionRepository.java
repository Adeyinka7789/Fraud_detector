package com.sentinelpay.fraudengine.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface TransactionRepository extends ReactiveCrudRepository<TransactionEntity, UUID> {

    @Query("""
        SELECT * FROM transactions 
        WHERE user_id = :userId 
          AND bucket_hour >= :start 
        ORDER BY bucket_hour DESC 
        LIMIT 100
        """)
    Flux<TransactionEntity> findRecentByUser(@Param("userId") UUID userId, @Param("start") Instant start);

    // Counts the number of transactions since a specific timestamp (for velocity checks)
    @Query("SELECT COUNT(*) FROM transactions WHERE user_id = :userId AND timestamp >= :since")
    Mono<Long> countTransactionsSince(@Param("userId") UUID userId, @Param("since") Instant since);

    // Sums the transaction amounts since a specific timestamp, returning 0 if no transactions are found
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE user_id = :userId AND timestamp >= :since")
    Mono<BigDecimal> sumAmountSince(@Param("userId") UUID userId, @Param("since") Instant since);

    // Find a transaction by its unique transaction ID
    Mono<TransactionEntity> findByTransactionId(UUID transactionId);

    @Query("""
        SELECT * FROM transactions 
        WHERE (:userId IS NULL OR user_id = :userId::uuid)
          AND (:decision IS NULL OR decision = :decision)
          AND (:fromDate IS NULL OR created_at >= :fromDate)
          AND (:toDate IS NULL OR created_at <= :toDate)
        ORDER BY created_at DESC 
        LIMIT :limit
        """)
    Flux<TransactionEntity> searchTransactions(
            @Param("userId") String userId,
            @Param("decision") String decision,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("limit") Integer limit
    );

    @Query("SELECT COUNT(*) FROM transactions")
    Mono<Long> countAll();

    // Counts transactions based on the final fraud decision (e.g., 'ACCEPT', 'REJECT', 'REVIEW')
    @Query("SELECT COUNT(*) FROM transactions WHERE decision = :decision")
    Mono<Long> countByDecision(@Param("decision") String decision);


    // Finds all distinct IP addresses associated with a user
    @Query("SELECT DISTINCT ip_address FROM transactions WHERE user_id = :userId")
    Flux<String> findUserLocations(@Param("userId") UUID userId);

    // Calculates the average transaction amount for a specific user
    @Query("SELECT AVG(amount) FROM transactions WHERE user_id = :userId")
    Mono<Double> findAverageAmount(@Param("userId") UUID userId);
}