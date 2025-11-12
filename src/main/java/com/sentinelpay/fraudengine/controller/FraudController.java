package com.sentinelpay.fraudengine.controller;

import com.sentinelpay.fraudengine.dto.TransactionRequest;
import com.sentinelpay.fraudengine.dto.TransactionResponse;
import com.sentinelpay.fraudengine.service.FraudService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/transactions")
public class FraudController {

    private final FraudService fraudService;

    public FraudController(FraudService fraudService) {
        this.fraudService = fraudService;
    }

    @PostMapping
    public Mono<ResponseEntity<TransactionResponse>> process(@Valid @RequestBody TransactionRequest request) {
        return fraudService.evaluate(request)
                .map(response -> ResponseEntity.accepted().body(response));
    }
}