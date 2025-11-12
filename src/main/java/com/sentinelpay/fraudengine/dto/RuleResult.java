package com.sentinelpay.fraudengine.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RuleResult {
    boolean passed;
    String ruleId;
    String reason;

    public static final RuleResult PASS = RuleResult.builder().passed(true).ruleId("PASS").reason("ok").build();
    public static final RuleResult FAIL_SAFE = RuleResult.builder().passed(false).ruleId("FAILSAFE").reason("fallback").build();
}