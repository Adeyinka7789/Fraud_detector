
package com.sentinelpay.fraudengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

public record TransactionRequest(
        @NotBlank String userId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String merchantId,
        @NotBlank String ipAddress,
        @NotNull Map<String, Object> deviceInfo
) {}