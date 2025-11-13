package com.sentinelpay.fraudengine.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface RuleRepository extends ReactiveCrudRepository<RuleEntity, UUID> {
    Flux<RuleEntity> findByEnabledTrue();
    Mono<RuleEntity> findByName(String name);
}