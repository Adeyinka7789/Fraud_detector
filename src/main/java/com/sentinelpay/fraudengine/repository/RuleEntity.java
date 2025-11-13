package com.sentinelpay.fraudengine.repository;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;
import java.util.UUID;

@Table("fraud_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleEntity {
    @Id
    private UUID id;
    private String name;
    private String description;
    private String condition;
    private Double score;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}