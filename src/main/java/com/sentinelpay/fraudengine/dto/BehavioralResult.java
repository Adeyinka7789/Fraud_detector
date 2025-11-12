package com.sentinelpay.fraudengine.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BehavioralResult {
    double anomalyScore;
    String pattern;
    String deviceRisk;

    public static final BehavioralResult NEUTRAL = BehavioralResult.builder()
            .anomalyScore(0.0)
            .pattern("normal")
            .deviceRisk("low")
            .build();
}