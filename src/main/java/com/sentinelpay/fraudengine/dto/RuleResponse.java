package com.sentinelpay.fraudengine.dto;

import java.time.Instant;
import java.util.UUID;

public record RuleResponse(
        UUID id,
        String name,
        String description,
        String condition,
        Double score,
        Boolean enabled,
        Instant createdAt
) {}