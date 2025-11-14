package com.sentinelpay.fraudengine.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class RateLimitService {
    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final RateLimiterRegistry rateLimiterRegistry;
    private final ObjectMapper objectMapper;

    public RateLimitService(RateLimiterRegistry rateLimiterRegistry, ObjectMapper objectMapper) {
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.objectMapper = objectMapper;
    }

    public boolean allowRequest(String clientId, String endpoint) {
        String limiterName = endpoint + "-" + clientId;
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(limiterName);

        try {
            RateLimiter.waitForPermission(rateLimiter);
            return true;
        } catch (RequestNotPermitted ex) {
            log.warn("Rate limit exceeded for client: {} on endpoint: {}", clientId, endpoint);
            return false;
        }
    }

    public Map<String, Object> getRateLimitStats(String clientId, String endpoint) {
        String limiterName = endpoint + "-" + clientId;
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(limiterName);
        RateLimiter.Metrics metrics = rateLimiter.getMetrics();

        Map<String, Object> stats = new HashMap<>();
        stats.put("availablePermissions", metrics.getAvailablePermissions());
        stats.put("numberOfWaitingThreads", metrics.getNumberOfWaitingThreads());
        return stats;
    }
}