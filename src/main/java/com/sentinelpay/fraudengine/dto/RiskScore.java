package com.sentinelpay.fraudengine.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RiskScore {
    double score; // 0.0 to 1.0
    String modelVersion;
    String reason;

    public static final RiskScore LOW = RiskScore.builder().score(0.1).reason("low_risk").build();
    public static final RiskScore MEDIUM = RiskScore.builder().score(0.5).reason("medium_risk").build();
    public static final RiskScore HIGH = RiskScore.builder().score(0.9).reason("high_risk").build();
}