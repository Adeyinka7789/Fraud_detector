package com.sentinelpay.fraudengine.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
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
    Flux<TransactionEntity> findRecentByUser(UUID userId, Instant start);
}