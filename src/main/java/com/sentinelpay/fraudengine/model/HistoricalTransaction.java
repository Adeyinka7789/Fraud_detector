package com.sentinelpay.fraudengine.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class HistoricalTransaction {
    UUID transactionId;
    BigDecimal amount;
    String currency;
    String merchantId;
    Instant timestamp;
    String decision; // ALLOW, DENY, REVIEW
}