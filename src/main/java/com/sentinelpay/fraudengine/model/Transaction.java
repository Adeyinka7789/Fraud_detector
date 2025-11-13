package com.sentinelpay.fraudengine.model;

import lombok.Builder;
import lombok.Value;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
@Builder(toBuilder = true)
public class Transaction {
    UUID transactionId;
    BigDecimal amount;
    String currency;
    String merchantId;
    String userId;
    DeviceInfo deviceInfo;
    Location location;
    Instant timestamp;
    PaymentMethod paymentMethod;
    List<HistoricalTransaction> userHistory;
    Map<String, Object> contextualFeatures;
}