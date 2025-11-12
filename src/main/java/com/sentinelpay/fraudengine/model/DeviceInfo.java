package com.sentinelpay.fraudengine.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DeviceInfo {
    String ip;
    String userAgentHash;
    String fingerprint;
}