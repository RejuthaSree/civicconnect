package com.civic_connect.backend.payment.dto;
public record RazorpayOrderResponse(String orderId, Double amount, String currency, String razorpayKey) { }
