package com.sentinelpay.fraudengine.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FraudDecision {
    ALLOW("ALLOW"),
    DENY("DENY"),
    REVIEW("REVIEW");

    private final String value;

    FraudDecision(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}