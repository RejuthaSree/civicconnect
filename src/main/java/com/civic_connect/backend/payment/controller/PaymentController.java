package com.civic_connect.backend.payment.controller;

import com.civic_connect.backend.payment.service.PaymentService;
import com.civic_connect.backend.payment.dto.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/create-order")
    public RazorpayOrderResponse createOrder(
            Authentication authentication,
            @Valid @RequestBody CreateRazorpayOrderRequest request) {
        return service.createOrder(authentication.getName(), request);
    }

    @PostMapping("/verify")
    public PaymentVerificationResponse verify(
            Authentication authentication,
            @Valid @RequestBody VerifyRazorpayPaymentRequest request) {
        return service.verify(authentication.getName(), request);
    }

    @GetMapping("/history")
    public List<PaymentResponse> history(Authentication authentication) {
        return service.history(authentication.getName());
    }
}
