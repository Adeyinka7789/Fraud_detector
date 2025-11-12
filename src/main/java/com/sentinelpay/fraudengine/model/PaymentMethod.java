package com.sentinelpay.fraudengine.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PaymentMethod {
    String type;
    String token;
}