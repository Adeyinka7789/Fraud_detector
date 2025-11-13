package com.sentinelpay.fraudengine.dto;

public record MetricsResponse(
        Long totalTransactions,
        Long blockedTransactions,
        Long reviewTransactions,
        Long allowedTransactions,
        Double fraudRate
) { }
