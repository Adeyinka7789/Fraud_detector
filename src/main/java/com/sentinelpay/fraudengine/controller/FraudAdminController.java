// FraudAdminController.java
package com.sentinelpay.fraudengine.controller;

import com.sentinelpay.fraudengine.dto.*;
import com.sentinelpay.fraudengine.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class FraudAdminController {

    private final MetricsService metricsService;
    private final RuleService ruleService;
    private final UserProfileService userProfileService;
    private final TransactionSearchService transactionSearchService;

    // 2. Transaction Search
    @GetMapping("/transactions/search")
    public Flux<TransactionResponse> searchTransactions(
            @RequestParam Optional<String> userId,
            @RequestParam Optional<String> decision,
            @RequestParam Optional<String> fromDate,
            @RequestParam Optional<String> toDate,
            @RequestParam Optional<Integer> limit) {

        SearchRequest searchRequest = new SearchRequest(
                userId,
                decision,
                fromDate.map(Instant::parse),
                toDate.map(Instant::parse),
                limit
        );

        return transactionSearchService.searchTransactions(searchRequest);
    }

    // 3. Rule Management
    @PostMapping("/rules")
    public Mono<RuleResponse> createRule(@RequestBody RuleRequest request) {
        return ruleService.createRule(request);
    }

    @GetMapping("/rules")
    public Flux<RuleResponse> getAllRules() {
        return ruleService.getAllRules();
    }

    @GetMapping("/rules/active")
    public Flux<RuleResponse> getActiveRules() {
        return ruleService.getActiveRules();
    }

    @PutMapping("/rules/{id}/enable")
    public Mono<RuleResponse> enableRule(@PathVariable String id) {
        return ruleService.enableRule(id);
    }

    @PutMapping("/rules/{id}/disable")
    public Mono<RuleResponse> disableRule(@PathVariable String id) {
        return ruleService.disableRule(id);
    }

    // 4. Metrics Dashboard
    @GetMapping("/metrics")
    public Mono<MetricsResponse> getMetrics() {
        return metricsService.getMetrics();
    }

    // 5. User Profiling
    @GetMapping("/users/{userId}/profile")
    public Mono<UserProfile> getUserProfile(@PathVariable String userId) {
        return userProfileService.buildUserProfile(userId);
    }
}