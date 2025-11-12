package com.sentinelpay.fraudengine.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        String userId,
        BigDecimal amount,
        String decision,
        Float riskScore,
        Instant timestamp
) {}