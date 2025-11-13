package com.sentinelpay.fraudengine.dto;

public record RuleRequest(
        String name,
        String description,
        String condition,
        Double score,
        Boolean enabled
) {}