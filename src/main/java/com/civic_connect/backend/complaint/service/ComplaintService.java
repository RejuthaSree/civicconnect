package com.civic_connect.backend.complaint.service;

import com.civic_connect.backend.common.enums.*;
import com.civic_connect.backend.common.exceptionHandler.ApiException;
import com.civic_connect.backend.complaint.entity.Complaint;
import com.civic_connect.backend.complaint.Repository.ComplaintRepository;
import com.civic_connect.backend.complaint.dto.*;
import com.civic_connect.backend.notification.entity.Notification;
import com.civic_connect.backend.notification.repository.NotificationRepository;
import com.civic_connect.backend.user.entity.User;
import com.civic_connect.backend.user.Repository.UserRepository;
import com.civic_connect.backend.worker.entity.Worker;
import com.civic_connect.backend.worker.repository.WorkerRepository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ComplaintService {
    private final ComplaintRepository complaints; private final UserRepository users;
    private final WorkerRepository workers; private final NotificationRepository notifications;

    public ComplaintService(ComplaintRepository complaints,
                            UserRepository users,
                            WorkerRepository workers,
                            NotificationRepository notifications) {

        this.complaints = complaints;
        this.users = users;
        this.workers = workers;
        this.notifications = notifications;
    }
    public ComplaintResponse create(String email, CreateComplaintRequest request) {
        User reporter = current(email);
        requireRole(reporter, Role.CITIZEN);
        Complaint c = new Complaint();
        c.setTitle(request.title());
        c.setDescription(request.description());
        c.setAddress(request.address());
        c.setLocation(request.address());
        c.setArea(request.area());
        c.setCity(request.city());
        c.setLatitude(request.latitude());
        c.setLongitude(request.longitude());
        c.setImageUrl(request.imageUrl());
        c.setIssueType(request.issueType());
        c.setPriority(request.priority());
        c.setReportedBy(reporter);
        c = complaints.save(c);
        notifyMatchingWorkers(c);
        return toResponse(c);
    }
    @Transactional(readOnly = true)
    public Page<ComplaintResponse> list(ComplaintStatus status, Pageable pageable) {

        Page<Complaint> page = status == null ?
                complaints.findAll(pageable) : complaints.findByStatus(status, pageable);
        return page.map(this::toResponse);
    }
    @Transactional(readOnly = true)
    public Page<ComplaintResponse> mine(String email, Pageable pageable)
    {
        return complaints.findByReportedBy(current(email), pageable).map(this::toResponse);
    }
    public ComplaintResponse vote(String email, Long id) {
        requireRole(current(email), Role.CITIZEN);
        Complaint c = get(id);
        c.setUpvotes(c.getUpvotes() + 1);
        return toResponse(c); }

    public ComplaintResponse verify(String email, Long id) {
        User u = current(email);
        Complaint c = get(id);
        if (!c.getReportedBy().getId().equals(u.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the reporting citizen may verify resolution");
        if (c.getStatus() == ComplaintStatus.WORK_COMPLETED) {
            c.setStatus(ComplaintStatus.RESOLVED);
            c.setResolvedAt(Instant.now());
            return toResponse(c);
        }
        if (c.getStatus() != ComplaintStatus.RESOLVED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Complaint is not ready for verification");
        }
        return toResponse(c);
    }
    public ComplaintResponse setPriority(String email, Long id, PriorityLevel priority) {
        requireRole(current(email), Role.ADMIN);
        Complaint c = get(id);
        c.setPriority(priority);
        return toResponse(c);
    }
    public Complaint get(Long id) {
        return complaints.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
    }
    public User current(String email) {
        return users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user not found")); }

    private void requireRole(User user, Role role) {
        if (user.getRole() != role)
            throw new ApiException(HttpStatus.FORBIDDEN, "This action requires " + role + " role"); }
    private void notifyMatchingWorkers(Complaint complaint) {
        WorkerSkill skill = skillFor(complaint.getIssueType());
        List<Worker> matches = workers.findBySkillAndAvailableTrue(skill)
                .stream().
                filter(w -> w.getVerificationStatus() == VerificationStatus.VERIFIED).
                filter(w -> inRange(complaint, w)).toList();

        for (Worker worker : matches) {
            Notification n = new Notification();
            n.setUser(worker.getUser());
            n.setTitle("New nearby civic issue");
            n.setMessage(complaint.getTitle()); notifications.save(n); }
    }

    private WorkerSkill skillFor(IssueType type) {
        return switch (type) {

        case ELECTRICITY -> WorkerSkill.ELECTRICIAN;

        case WATER, DRAINAGE -> WorkerSkill.PLUMBER;
        case GARBAGE -> WorkerSkill.SANITATION;
        case ROAD -> WorkerSkill.ROAD_REPAIR;
        default -> WorkerSkill.CONTRACTOR; }; }
    private boolean inRange(Complaint c, Worker w) {
        if (c.getLatitude() == null || c.getLongitude() == null || w.getLatitude() == null || w.getLongitude() == null)
            return c.getArea().equalsIgnoreCase(w.getServiceArea());
        double lat = Math.toRadians(w.getLatitude()-c.getLatitude());
        double lon = Math.toRadians(w.getLongitude()-c.getLongitude());
        double a = Math.sin(lat/2)*Math.sin(lat/2)+Math.cos(Math.toRadians(c.getLatitude()))*Math.cos(Math.toRadians(w.getLatitude()))*Math.sin(lon/2)*Math.sin(lon/2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a),Math.sqrt(1-a)) <= w.getWorkRadiusKm();
    }
    public ComplaintResponse toResponse(Complaint c)
    {
        return new ComplaintResponse(c.getId(),c.getTitle(),c.getDescription(),
                c.getAddress(),c.getArea(),c.getCity(),c.getLatitude(),
                c.getLongitude(),c.getImageUrl(),c.getStatus(),c.getPriority(),c.getIssueType(),c.getReportedAt(),c.getResolvedAt(),c.getUpvotes(),c.getAiClassification(),c.getReportedBy().getId(),c.getAssignedWorker()==null?null:c.getAssignedWorker().getId()); }
}
