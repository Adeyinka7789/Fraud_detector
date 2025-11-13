package com.sentinelpay.fraudengine.dto;

import java.time.Instant;
import java.util.Optional;

public record SearchRequest(
        Optional<String> userId,
        Optional<String> decision,
        Optional<Instant> fromDate,
        Optional<Instant> toDate,
        Optional<Integer> limit
) {}