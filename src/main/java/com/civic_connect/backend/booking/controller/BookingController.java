package com.civic_connect.backend.booking.controller;

import com.civic_connect.backend.booking.service.BookingService;
import com.civic_connect.backend.booking.dto.BookingResponse;
import com.civic_connect.backend.booking.dto.CreateBookingRequest;
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

    public BookingController(BookingService service) {
        this.service = service;
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
}
