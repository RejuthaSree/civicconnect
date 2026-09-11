package com.civic_connect.backend.complaint.service;

import com.civic_connect.backend.classifier.AiClassificationService;
import com.civic_connect.backend.classifier.dto.AiClassificationResult;
import com.civic_connect.backend.common.enums.*;
import com.civic_connect.backend.common.exceptionHandler.ApiException;
import com.civic_connect.backend.complaint.entity.Complaint;
import com.civic_connect.backend.complaint.entity.ComplaintVote;
import com.civic_connect.backend.complaint.Repository.ComplaintRepository;
import com.civic_connect.backend.complaint.Repository.ComplaintVoteRepository;
import com.civic_connect.backend.complaint.dto.*;
import com.civic_connect.backend.notification.entity.Notification;
import com.civic_connect.backend.notification.repository.NotificationRepository;
import com.civic_connect.backend.sla.service.SlaService;
import com.civic_connect.backend.user.entity.User;
import com.civic_connect.backend.user.Repository.UserRepository;
import com.civic_connect.backend.worker.entity.Worker;
import com.civic_connect.backend.worker.repository.WorkerRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ComplaintService {
    private final ComplaintRepository complaints; private final UserRepository users;
    private final WorkerRepository workers; private final NotificationRepository notifications;
    private final ComplaintVoteRepository votes;
    private final AiClassificationService aiClassifier;

    public ComplaintService(ComplaintRepository complaints,
                            UserRepository users,
                            WorkerRepository workers,
                            NotificationRepository notifications,
                            ComplaintVoteRepository votes,
                            AiClassificationService aiClassifier) {

        this.complaints = complaints;
        this.users = users;
        this.workers = workers;
        this.notifications = notifications;
        this.votes = votes;
        this.aiClassifier = aiClassifier;
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
        c.setIssueScope(request.issueScope() == null ? IssueScope.PUBLIC : request.issueScope());
        c.setReportedBy(reporter);
        c = complaints.save(c);
        c.setSlaDeadline(SlaService.computeDeadline(c.getReportedAt(), c.getPriority()));
        c = complaints.save(c);
        try {
            AiClassificationResult ai = aiClassifier.classify(
                    c.getTitle(), c.getDescription(), c.getAddress(), c.getArea(), c.getCity()
            );
            if (ai != null) {
                String json = AiClassificationService.toJson(ai);
                if (json != null) {
                    c.setAiClassification(json);
                    c = complaints.save(c);
                }
            }
        } catch (Exception ignored) {
        }
        notifyMatchingWorkers(c);
        List<DuplicateCandidate> dupes;
        try {
            dupes = findPotentialDuplicates(c);
        } catch (Exception ignored) {
            dupes = new ArrayList<>();
        }
        return toResponse(c, dupes);
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

    @Transactional(readOnly = true)
    public ComplaintResponse one(Long id) {
        return toResponse(get(id));
    }

    @Transactional(readOnly = true)
    public ComplaintResponse oneWithDuplicates(Long id) {
        Complaint c = get(id);
        List<DuplicateCandidate> dupes = findPotentialDuplicates(c);
        return toResponse(c, dupes);
    }

    public ComplaintResponse vote(String email, Long id) {
        User voter = current(email);
        requireRole(voter, Role.CITIZEN);
        Complaint c = get(id);
        if (votes.existsByComplaintIdAndVoterId(c.getId(), voter.getId()))
            throw new ApiException(HttpStatus.CONFLICT, "You have already upvoted this complaint");
        ComplaintVote vote = new ComplaintVote();
        vote.setComplaint(c);
        vote.setVoter(voter);
        votes.save(vote);
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
        c.setSlaDeadline(SlaService.computeDeadline(c.getReportedAt(), priority));
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
            return c.getArea() != null && c.getArea().equalsIgnoreCase(w.getServiceArea());
        return haversineKm(c.getLatitude(), c.getLongitude(), w.getLatitude(), w.getLongitude()) <= w.getWorkRadiusKm();
    }

    public AiClassificationResult parseAiClassification(String rawJson) {
        return AiClassificationService.fromJson(rawJson);
    }

    public String writeAiClassification(AiClassificationResult r) {
        return AiClassificationService.toJson(r);
    }

    public List<DuplicateCandidate> findPotentialDuplicates(Complaint target) {
        List<Complaint> pool = gatherCandidatePool(target);
        List<DuplicateCandidate> results = new ArrayList<>();
        String targetText = normalize(target.getTitle() + " " + target.getDescription());
        for (Complaint other : pool) {
            if (other.getId().equals(target.getId())) continue;
            String otherText = normalize(other.getTitle() + " " + other.getDescription());
            double similarity = diceCoefficient(targetText, otherText);
            Double dist = null;
            if (target.getLatitude() != null && target.getLongitude() != null
                    && other.getLatitude() != null && other.getLongitude() != null) {
                dist = haversineKm(target.getLatitude(), target.getLongitude(), other.getLatitude(), other.getLongitude());
            }
            boolean areaMatch = target.getArea() != null && other.getArea() != null
                    && target.getArea().trim().equalsIgnoreCase(other.getArea().trim());
            boolean geoOk = (dist != null && dist <= 2.0) || (dist == null && areaMatch);
            if (similarity >= 0.45 && geoOk) {
                results.add(new DuplicateCandidate(other.getId(), other.getTitle(),
                        Math.round(similarity * 1000.0) / 1000.0, dist));
            }
        }
        results.sort((a, b) -> Double.compare(
                (b.similarity() == null ? 0.0 : b.similarity()),
                (a.similarity() == null ? 0.0 : a.similarity())
        ));
        return results.stream().limit(5).collect(Collectors.toList());
    }

    private List<Complaint> gatherCandidatePool(Complaint target) {
        Long id = target.getId() == null ? -1L : target.getId();
        List<Complaint> pool = new ArrayList<>();
        boolean hasGeo = target.getLatitude() != null && target.getLongitude() != null;
        if (hasGeo) {
            double delta = 2.0 / 111.0;
            List<Complaint> geoCandidates = complaints.findCandidateDuplicatesByGeoBounds(
                    id,
                    target.getLatitude() - delta,
                    target.getLatitude() + delta,
                    target.getLongitude() - delta / Math.max(0.1, Math.cos(Math.toRadians(target.getLatitude()))),
                    target.getLongitude() + delta / Math.max(0.1, Math.cos(Math.toRadians(target.getLatitude())))
            );
            pool.addAll(geoCandidates);
        }
        if (target.getArea() != null || target.getCity() != null) {
            List<Complaint> areaCandidates = complaints.findCandidateDuplicatesByAreaOrCity(
                    id, target.getArea(), target.getCity()
            );
            for (Complaint a : areaCandidates) {
                if (!pool.contains(a)) pool.add(a);
            }
        }
        if (pool.isEmpty()) {
            pool = complaints.findAll(PageRequest.of(0, 100)).getContent();
        }
        return pool;
    }

    public String groupDuplicates(List<Long> ids) {
        if (ids == null || ids.size() < 2)
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least 2 complaint IDs are required to group");
        List<Complaint> list = new ArrayList<>();
        long minId = Long.MAX_VALUE;
        for (Long id : ids) {
            Complaint c = get(id);
            list.add(c);
            if (c.getId() < minId) minId = c.getId();
        }
        String groupId = "DUP-" + minId;
        for (Complaint c : list) c.setDuplicateGroupId(groupId);
        return groupId;
    }

    public void ungroupDuplicate(Long id) {
        Complaint c = get(id);
        c.setDuplicateGroupId(null);
    }

    public List<ComplaintResponse> findByDuplicateGroup(String groupId) {
        return complaints.findByDuplicateGroupId(groupId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double lat = Math.toRadians(lat2 - lat1);
        double lon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(lat/2)*Math.sin(lat/2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.sin(lon/2)*Math.sin(lon/2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private static double diceCoefficient(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.equals(b)) return 1.0;
        if (a.length() < 2 || b.length() < 2) return 0.0;
        Map<String, Integer> bgA = bigrams(a);
        Map<String, Integer> bgB = bigrams(b);
        int totalA = bgA.values().stream().mapToInt(Integer::intValue).sum();
        int totalB = bgB.values().stream().mapToInt(Integer::intValue).sum();
        int intersect = 0;
        for (Map.Entry<String, Integer> e : bgA.entrySet()) {
            Integer other = bgB.get(e.getKey());
            if (other != null) intersect += Math.min(e.getValue(), other);
        }
        return (2.0 * intersect) / (double) (totalA + totalB);
    }

    private static Map<String, Integer> bigrams(String s) {
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i + 1 < s.length(); i++) {
            String bg = s.substring(i, i + 2);
            out.merge(bg, 1, Integer::sum);
        }
        return out;
    }

    public ComplaintResponse toResponse(Complaint c)
    {
        return toResponse(c, null);
    }

    public ComplaintResponse toResponse(Complaint c, List<DuplicateCandidate> potentialDuplicates)
    {
        Instant now = Instant.now();
        AiClassificationResult ai = parseAiClassification(c.getAiClassification());
        return new ComplaintResponse(c.getId(),c.getTitle(),c.getDescription(),
                c.getAddress(),c.getArea(),c.getCity(),c.getLatitude(),
                c.getLongitude(),c.getImageUrl(),c.getStatus(),c.getPriority(),c.getIssueType(),c.getIssueScope(),c.getReportedAt(),c.getResolvedAt(),
                c.getSlaDeadline(),
                SlaService.isSlaBreached(c, now),
                SlaService.remainingHours(c, now),
                c.getEscalationLevel(),
                c.getUpvotes(),c.getAiClassification(),c.getReportedBy().getId(),c.getAssignedWorker()==null?null:c.getAssignedWorker().getId(),
                ai == null ? null : (ai.category() == null ? null : ai.category().name()),
                ai == null ? null : (ai.severity() == null ? null : ai.severity().name()),
                ai == null ? null : ai.suggestedDepartment(),
                ai == null ? null : ai.confidence(),
                ai == null ? null : ai.confirmedByAdminId(),
                c.getDuplicateGroupId(),
                potentialDuplicates);
    }
}
