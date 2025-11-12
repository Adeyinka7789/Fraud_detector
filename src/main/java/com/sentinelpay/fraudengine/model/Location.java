package com.sentinelpay.fraudengine.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Location {
    String country;
    Double lat;
    Double lng;
}