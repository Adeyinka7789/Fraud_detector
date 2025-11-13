package com.sentinelpay.fraudengine.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UserProfile(
        String userId,
        BigDecimal typicalAmount,
        List<String> commonLocations,
        List<String> preferredDevices,
        Map<String, Object> behaviorPatterns,
        Instant profileUpdatedAt
) {}