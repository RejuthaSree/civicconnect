package com.civic_connect.backend.booking.controller;

import com.civic_connect.backend.booking.dto.BookingResponse;
import com.civic_connect.backend.booking.dto.CreateBookingRequest;
import com.civic_connect.backend.booking.dto.GovernmentVerifyAndPayResponse;
import com.civic_connect.backend.booking.service.BookingService;
import com.civic_connect.backend.common.enums.PaymentSource;
import com.civic_connect.backend.payment.dto.CreateRazorpayOrderRequest;
import com.civic_connect.backend.payment.dto.RazorpayOrderResponse;
import com.civic_connect.backend.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService service;
    private final PaymentService paymentService;

    public BookingController(BookingService service, PaymentService paymentService) {
        this.service = service;
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(authentication.getName(), request));
    }

    @GetMapping("/mine")
    public List<BookingResponse> mine(Authentication authentication) {
        return service.mine(authentication.getName());
    }

    @PostMapping("/{id}/accept")
    public BookingResponse accept(Authentication authentication, @PathVariable("id") Long id) {
        return service.accept(authentication.getName(), id);
    }

    @PostMapping("/{id}/start")
    public BookingResponse start(Authentication authentication, @PathVariable("id") Long id) {
        return service.beginWork(authentication.getName(), id);
    }

    @PostMapping("/{id}/complete")
    public BookingResponse complete(Authentication authentication, @PathVariable("id") Long id) {
        return service.complete(authentication.getName(), id);
    }

    @PostMapping("/{id}/confirm-completion")
    public BookingResponse confirmCompletion(Authentication authentication, @PathVariable("id") Long id) {
        return service.confirmCompletion(authentication.getName(), id);
    }

    @PostMapping("/{id}/government-verify-and-pay")
    public GovernmentVerifyAndPayResponse governmentVerifyAndPay(
            Authentication authentication,
            @PathVariable("id") Long id) {
        BookingResponse verified = service.governmentVerify(authentication.getName(), id);
        CreateRazorpayOrderRequest orderRequest = new CreateRazorpayOrderRequest(
                verified.id(),
                verified.amount(),
                PaymentSource.GOVERNMENT);
        RazorpayOrderResponse order = paymentService.createOrder(authentication.getName(), orderRequest);
        return new GovernmentVerifyAndPayResponse(verified, order);
    }
}
