package com.sentinelpay.fraudengine.repository;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Table("transactions")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntity {

    @Id
    @Column("transaction_id")
    private UUID transactionId;

    @Column("user_id")
    private UUID userId;

    @Column("amount")
    private BigDecimal amount;

    @Column("currency")
    private String currency;

    @Column("merchant_id")
    private String merchantId;

    @Column("ip_address")  // Added missing field
    private String ipAddress;

    @Column("device_info")  // Added missing field
    private String deviceInfo;

    @Column("bucket_hour")
    private Instant bucketHour;

    @Column("risk_score")
    private Float riskScore;

    @Column("decision")
    private String decision;

    @Column("features")
    private String features;

    @Column("created_at")
    private Instant createdAt;

    @Column("timestamp")
    private Instant timestamp;
}