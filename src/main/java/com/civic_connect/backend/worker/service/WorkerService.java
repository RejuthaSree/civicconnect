package com.civic_connect.backend.worker.service;

import com.civic_connect.backend.common.exceptionHandler.ApiException;
import com.civic_connect.backend.common.enums.Role;
import com.civic_connect.backend.user.entity.User;
import com.civic_connect.backend.user.Repository.UserRepository;
import com.civic_connect.backend.worker.repository.WorkerRepository;
import com.civic_connect.backend.worker.entity.WorkerVerification;
import com.civic_connect.backend.worker.repository.WorkerVerificationRepository;
import com.civic_connect.backend.worker.dto.*;
import com.civic_connect.backend.worker.entity.Worker;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.civic_connect.backend.common.enums.VerificationStatus;
import com.civic_connect.backend.common.enums.WorkerSkill;

@Service @Transactional
public class WorkerService {
    private final WorkerRepository workers;
    private final UserRepository users;
    private final WorkerVerificationRepository verifications;

    public WorkerService(
            WorkerRepository workers,
            UserRepository users,
            WorkerVerificationRepository verifications) {
        this.workers = workers;
        this.users = users;
        this.verifications = verifications;
    }
    public WorkerResponse register(String email, WorkerRegistrationRequest request) {
        User user = current(email);
        if (user.getRole() == Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Administrators cannot enroll as workers");
        }
        user.setRole(Role.WORKER);
        Worker worker = workers.findByUser(user).orElseGet(Worker::new);
        worker.setUser(user);
        worker.setSkill(request.skill());
        worker.setServiceArea(request.serviceArea());
        worker.setLatitude(request.latitude());
        worker.setLongitude(request.longitude());
        worker.setCity(request.city());
        worker.setPhoneNumber(request.phoneNumber());
        worker.setAddress(request.address());
        worker.setProfilePhotoUrl(request.profilePhotoUrl());
        worker.setAadhaarMasked(request.aadhaarMasked());
        worker.setGovernmentIdReference(request.governmentIdReference());
        worker.setCertificates(request.certificates());
        if (request.experienceYears() != null) worker.setExperienceYears(request.experienceYears());
        worker.setVerificationStatus(VerificationStatus.PENDING);
        if (request.workRadiusKm() != null) {
            worker.setWorkRadiusKm(request.workRadiusKm());
        }
        Worker savedWorker = workers.save(worker);
        WorkerVerification verification = verifications.findByWorkerId(savedWorker.getId())
                .orElseGet(WorkerVerification::new);
        verification.setWorker(savedWorker);
        verification.setDocumentUrl(request.governmentIdReference());
        verification.setVerificationStatus(VerificationStatus.PENDING);
        verification.setApprovedBy(null);
        verification.setApprovedAt(null);
        verifications.save(verification);
        return response(savedWorker);
    }

    @Transactional(readOnly = true)
    public WorkerResponse mine(String email) {
        return response(workers.findByUser(current(email))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Worker profile not found")));
    }

    @Transactional(readOnly = true)
    public Page<WorkerResponse> browse(WorkerSkill skill, Pageable pageable) {
        Page<Worker> page = skill == null
                ? workers.findByAvailableTrueAndVerificationStatus(VerificationStatus.VERIFIED, pageable)
                : workers.findBySkillAndAvailableTrueAndVerificationStatus(skill, VerificationStatus.VERIFIED, pageable);
        return page.map(this::response);
    }

    @Transactional(readOnly = true)
    public WorkerResponse publicProfile(Long id) {
        Worker worker = workers.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Worker not found"));
        if (worker.getVerificationStatus() != VerificationStatus.VERIFIED)
            throw new ApiException(HttpStatus.NOT_FOUND, "Verified worker not found");
        return response(worker);
    }

    public User current(String email) {
        return users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    public WorkerResponse response(Worker worker) {
        return new WorkerResponse(
                worker.getId(), worker.getUser().getId(), worker.getUser().getUsername(), worker.getSkill(),
                worker.getServiceArea(), worker.getCity(), worker.getProfilePhotoUrl(), worker.getExperienceYears(),
                worker.getCertificates(), worker.getLatitude(), worker.getLongitude(), worker.getWorkRadiusKm(),
                worker.isAvailable(), worker.getVerificationStatus(), worker.getCompletedTasks(), worker.getRating(),
                worker.getTotalReviews(), worker.getTotalEarnings(), worker.getTotalGovernmentPaymentsReceived());
    }
}
