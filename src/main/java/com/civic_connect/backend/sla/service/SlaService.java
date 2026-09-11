package com.civic_connect.backend.sla.service;

import com.civic_connect.backend.common.enums.ComplaintStatus;
import com.civic_connect.backend.common.enums.PriorityLevel;
import com.civic_connect.backend.common.enums.Role;
import com.civic_connect.backend.common.exceptionHandler.ApiException;
import com.civic_connect.backend.complaint.Repository.ComplaintRepository;
import com.civic_connect.backend.complaint.entity.Complaint;
import com.civic_connect.backend.notification.service.NotificationService;
import com.civic_connect.backend.user.Repository.UserRepository;
import com.civic_connect.backend.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class SlaService {

    private final ComplaintRepository complaints;
    private final NotificationService notifications;
    private final UserRepository users;

    public SlaService(ComplaintRepository complaints,
                      NotificationService notifications,
                      UserRepository users) {
        this.complaints = complaints;
        this.notifications = notifications;
        this.users = users;
    }

    public static Duration slaWindowFor(PriorityLevel priority) {
        if (priority == null) return Duration.ofHours(24);
        return switch (priority) {
            case CRITICAL -> Duration.ofHours(4);
            case HIGH -> Duration.ofHours(12);
            case MEDIUM -> Duration.ofHours(24);
            case LOW -> Duration.ofHours(48);
        };
    }

    public static Instant computeDeadline(Instant reportedAt, PriorityLevel priority) {
        if (reportedAt == null) return null;
        return reportedAt.plus(slaWindowFor(priority));
    }

    public static boolean isSlaBreached(Complaint c, Instant now) {
        if (c == null || c.getSlaDeadline() == null) return false;
        if (c.getStatus() == null) return false;
        return switch (c.getStatus()) {
            case RESOLVED, REJECTED -> false;
            default -> now.isAfter(c.getSlaDeadline());
        };
    }

    public static Double remainingHours(Complaint c, Instant now) {
        if (c == null || c.getSlaDeadline() == null) return null;
        long millis = Duration.between(now, c.getSlaDeadline()).toMillis();
        return Math.round(millis / 360_000.0) / 10.0;
    }

    public ComplaintResponseEscalation escalate(String email, Long complaintId, Integer targetLevel) {
        User admin = current(email);
        requireAdmin(admin);
        Complaint c = get(complaintId);
        int safeTarget = targetLevel == null ? (Math.min(2, (c.getEscalationLevel() == null ? 0 : c.getEscalationLevel()) + 1)) : Math.max(0, Math.min(2, targetLevel));
        if (c.getEscalationLevel() == null || safeTarget > c.getEscalationLevel()) {
            c.setEscalationLevel(safeTarget);
            notifyEscalation(c, safeTarget);
        }
        return buildEscalation(c);
    }

    public List<ComplaintResponseEscalation> listEscalated(String email) {
        requireAdmin(current(email));
        List<ComplaintResponseEscalation> out = new ArrayList<>();
        for (Complaint c : complaints.findAllEscalated()) {
            out.add(buildEscalation(c));
        }
        out.sort((a, b) -> Integer.compare(
                b.escalationLevel() == null ? 0 : b.escalationLevel(),
                a.escalationLevel() == null ? 0 : a.escalationLevel()
        ));
        return out;
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 60_000L)
    public void checkAndEscalateBreaches() {
        Instant now = Instant.now();
        try {
            List<Complaint> open = complaints.findAllOpenForSla();
            for (Complaint c : open) {
                if (c.getSlaDeadline() == null) {
                    c.setSlaDeadline(computeDeadline(c.getReportedAt(), c.getPriority()));
                }
                if (isSlaBreached(c, now)) {
                    Integer currentLevel = c.getEscalationLevel() == null ? 0 : c.getEscalationLevel();
                    Duration breachedFor = Duration.between(c.getSlaDeadline(), now);
                    int autoLevel = currentLevel;
                    if (currentLevel < 1) {
                        autoLevel = 1;
                    }
                    if (breachedFor.toHours() >= 2 && currentLevel < 2) {
                        autoLevel = 2;
                    }
                    if (autoLevel > currentLevel) {
                        c.setEscalationLevel(autoLevel);
                        notifyBreach(c, autoLevel);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public record ComplaintResponseEscalation(
            Long complaintId,
            String title,
            String status,
            String priority,
            Instant slaDeadline,
            Boolean slaBreached,
            Double remainingHours,
            Integer escalationLevel
    ) {}

    public ComplaintResponseEscalation buildEscalation(Complaint c) {
        Instant now = Instant.now();
        return new ComplaintResponseEscalation(
                c.getId(),
                c.getTitle(),
                c.getStatus() == null ? null : c.getStatus().name(),
                c.getPriority() == null ? null : c.getPriority().name(),
                c.getSlaDeadline(),
                isSlaBreached(c, now),
                remainingHours(c, now),
                c.getEscalationLevel()
        );
    }

    private Complaint get(Long id) {
        return complaints.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complaint not found"));
    }

    private User current(String email) {
        return users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    private void requireAdmin(User user) {
        if (user.getRole() != Role.ADMIN)
            throw new ApiException(HttpStatus.FORBIDDEN, "This action requires ADMIN role");
    }

    private void notifyEscalation(Complaint c, int level) {
        try {
            String levelName = level >= 2 ? "ADMIN" : "SUPERVISOR";
            for (User admin : users.findAllByRole(Role.ADMIN)) {
                notifications.send(admin,
                        "Complaint escalated to " + levelName,
                        "Complaint #" + c.getId() + " · " + c.getTitle());
            }
            if (c.getReportedBy() != null) {
                notifications.send(c.getReportedBy(),
                        "Your complaint has been escalated",
                        "Complaint #" + c.getId() + " escalated to " + levelName + " level.");
            }
        } catch (Exception ignored) {
        }
    }

    private void notifyBreach(Complaint c, int newLevel) {
        try {
            String levelName = newLevel >= 2 ? "ADMIN ESCALATION" : "SUPERVISOR ESCALATION";
            for (User admin : users.findAllByRole(Role.ADMIN)) {
                notifications.send(admin,
                        "SLA BREACH · " + levelName,
                        "Complaint #" + c.getId() + " breached SLA: " + c.getTitle());
            }
            if (c.getAssignedWorker() != null) {
                notifications.send(c.getAssignedWorker(),
                        "SLA breach on assigned complaint",
                        "Complaint #" + c.getId() + " · " + c.getTitle());
            }
            if (c.getReportedBy() != null) {
                notifications.send(c.getReportedBy(),
                        "SLA update on your complaint",
                        "Complaint #" + c.getId() + " has been escalated due to SLA delay.");
            }
        } catch (Exception ignored) {
        }
    }
}
