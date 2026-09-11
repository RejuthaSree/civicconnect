package com.civic_connect.backend.complaint.controller;

import com.civic_connect.backend.common.enums.ComplaintStatus;
import com.civic_connect.backend.complaint.dto.*;
import com.civic_connect.backend.complaint.service.ComplaintService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService service; public ComplaintController(ComplaintService service) {
        this.service=service;
    }
    @PostMapping public ResponseEntity<ComplaintResponse> create(Authentication a,
                                                                 @Valid @RequestBody CreateComplaintRequest r)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(a.getName(),r)); }

    @GetMapping public Page<ComplaintResponse> list(@RequestParam(name = "status", required=false) ComplaintStatus status,
                                                    Pageable pageable) {
        return service.list(status,pageable);
    }
    @GetMapping("/mine") public Page<ComplaintResponse> mine(Authentication a,Pageable p) {
        return service.mine(a.getName(),p);
    }
    @PostMapping("/{id}/vote") public ComplaintResponse vote
            (Authentication a,@PathVariable("id") Long id)
    {
        return service.vote(a.getName(),id);
    }
    @PostMapping("/{id}/verify")
    public ComplaintResponse verify
            (Authentication a,@PathVariable("id") Long id)
    {
        return service.verify(a.getName(),id);}
}
