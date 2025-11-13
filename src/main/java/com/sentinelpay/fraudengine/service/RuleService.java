package com.sentinelpay.fraudengine.service;

import com.sentinelpay.fraudengine.dto.RuleRequest;
import com.sentinelpay.fraudengine.dto.RuleResponse;
import com.sentinelpay.fraudengine.repository.RuleEntity;
import com.sentinelpay.fraudengine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RuleService {
    private final RuleRepository ruleRepository;

    public Mono<RuleResponse> createRule(RuleRequest request) {
        RuleEntity entity = RuleEntity.builder()
                // REMOVE: .id(UUID.randomUUID()) - Let database generate the ID
                .name(request.name())
                .description(request.description())
                .condition(request.condition())
                .score(request.score())
                .enabled(request.enabled() != null ? request.enabled() : true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return ruleRepository.save(entity)
                .map(this::toResponse);
    }

    public Mono<RuleResponse> enableRule(String id) {
        return ruleRepository.findById(UUID.fromString(id))
                .flatMap(rule -> {
                    rule.setEnabled(true);
                    rule.setUpdatedAt(Instant.now());
                    return ruleRepository.save(rule);
                })
                .map(this::toResponse);
    }

    public Mono<RuleResponse> disableRule(String id) {
        return ruleRepository.findById(UUID.fromString(id))
                .flatMap(rule -> {
                    rule.setEnabled(false);
                    rule.setUpdatedAt(Instant.now());
                    return ruleRepository.save(rule);
                })
                .map(this::toResponse);
    }

    public Flux<RuleResponse> getAllRules() {
        return ruleRepository.findAll()
                .map(this::toResponse);
    }

    public Flux<RuleResponse> getActiveRules() {
        return ruleRepository.findByEnabledTrue()
                .map(this::toResponse);
    }

    public Mono<RuleResponse> updateRule(String id, RuleRequest request) {
        return ruleRepository.findById(UUID.fromString(id))
                .flatMap(rule -> {
                    rule.setName(request.name());
                    rule.setDescription(request.description());
                    rule.setCondition(request.condition());
                    rule.setScore(request.score());
                    if (request.enabled() != null) {
                        rule.setEnabled(request.enabled());
                    }
                    rule.setUpdatedAt(Instant.now());
                    return ruleRepository.save(rule);
                })
                .map(this::toResponse);
    }

    public Mono<Void> deleteRule(String id) {
        return ruleRepository.deleteById(UUID.fromString(id));
    }

    private RuleResponse toResponse(RuleEntity entity) {
        return new RuleResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCondition(),
                entity.getScore(),
                entity.getEnabled(),
                entity.getCreatedAt()
        );
    }
}