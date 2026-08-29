package com.civic_connect.backend.worker.controller;
import com.civic_connect.backend.worker.dto.WorkerRegistrationRequest;
import com.civic_connect.backend.worker.dto.WorkerResponse;
import com.civic_connect.backend.worker.service.WorkerService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.civic_connect.backend.common.enums.WorkerSkill;

@RestController
@RequestMapping("/api/workers")
public class WorkerController {

    private final WorkerService service;

    public WorkerController(WorkerService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public WorkerResponse register(
            Authentication authentication,
            @Valid @RequestBody WorkerRegistrationRequest request) {
        return service.register(authentication.getName(), request);
    }

    @GetMapping("/me")
    public WorkerResponse me(Authentication authentication) {
        return service.mine(authentication.getName());
    }

    @GetMapping
    public Page<WorkerResponse> browse(@RequestParam(name = "skill", required = false) WorkerSkill skill, Pageable pageable) {
        return service.browse(skill, pageable);
    }

    @GetMapping("/{id}")
    public WorkerResponse profile(@PathVariable("id") Long id) { return service.publicProfile(id); }
}
