package com.civic_connect.backend.admin.service;

import com.civic_connect.backend.admin.dto.GeoComplaint;
import com.civic_connect.backend.admin.dto.GovernmentStats;
import com.civic_connect.backend.assignment.repository.AssignmentRepository;
import com.civic_connect.backend.classifier.dto.AiClassificationResult;
import com.civic_connect.backend.common.enums.CompletionStatus;
import com.civic_connect.backend.common.enums.ComplaintStatus;
import com.civic_connect.backend.common.enums.IssueScope;
import com.civic_connect.backend.common.enums.IssueType;
import com.civic_connect.backend.common.enums.PriorityLevel;
import com.civic_connect.backend.common.enums.Role;
import com.civic_connect.backend.common.enums.VerificationStatus;
import com.civic_connect.backend.common.exceptionHandler.ApiException;
import com.civic_connect.backend.complaint.Repository.ComplaintRepository;
import com.civic_connect.backend.complaint.dto.ComplaintResponse;
import com.civic_connect.backend.complaint.entity.Complaint;
import com.civic_connect.backend.complaint.service.ComplaintService;
import com.civic_connect.backend.payment.repository.PaymentRepository;
import com.civic_connect.backend.user.entity.User;
import com.civic_connect.backend.worker.dto.WorkerResponse;
import com.civic_connect.backend.worker.entity.Worker;
import com.civic_connect.backend.worker.repository.WorkerRepository;
import com.civic_connect.backend.worker.service.WorkerService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service @Transactional
public class WorkforceAdminService {
 private final WorkerRepository workers;
 private final WorkerService workerService;
 private final ComplaintService complaints;
 private final ComplaintRepository complaintRepository;
 private final AssignmentRepository assignments;
 private final PaymentRepository payments;
 public WorkforceAdminService(WorkerRepository workers,WorkerService workerService,ComplaintService complaints,
                              ComplaintRepository complaintRepository, AssignmentRepository assignments,
                              PaymentRepository payments){
  this.workers=workers;
  this.workerService=workerService;
  this.complaints=complaints;
  this.complaintRepository = complaintRepository;
  this.assignments = assignments;
  this.payments = payments;
 }
 public WorkerResponse verify(String email,Long workerId,boolean approved,String notes){
  requireAdmin(complaints.current(email));
  Worker worker=get(workerId);
  worker.setVerificationStatus(approved? VerificationStatus.VERIFIED:VerificationStatus.REJECTED);
  worker.setVerificationNotes(notes);
  worker.setVerifiedAt(approved?Instant.now():null);

  if(!approved) {
   worker.setAvailable(false);
  }
  return workerService.response(worker);
 }
 public WorkerResponse availability(String email,Long workerId,boolean available){

  requireAdmin(complaints.current(email));
  Worker worker=get(workerId);
  worker.setAvailable(available);
  return workerService.response(worker);
 }
 public java.util.List<WorkerResponse> list(String email) {
  requireAdmin(complaints.current(email));
  return workers.findAllByOrderByIdDesc().stream().map(workerService::response).toList();
 }

 public ComplaintResponse overrideClassification(String email, Long complaintId,
                                                  IssueType category, PriorityLevel priority,
                                                  boolean applyToComplaint) {
  User admin = complaints.current(email);
  requireAdmin(admin);
  Complaint c = complaints.get(complaintId);
  AiClassificationResult existing = complaints.parseAiClassification(c.getAiClassification());
  AiClassificationResult updated = new AiClassificationResult(
          category,
          priority,
          existing != null ? existing.suggestedDepartment() : null,
          1.0,
          existing != null && existing.aiSuggested(),
          admin.getId()
  );
  String json = complaints.writeAiClassification(updated);
  if (json != null) c.setAiClassification(json);
  if (applyToComplaint) {
   if (category != null) c.setIssueType(category);
   if (priority != null) c.setPriority(priority);
  }
  return complaints.toResponse(c);
 }

 public ComplaintResponse detectDuplicates(String email, Long complaintId) {
  requireAdmin(complaints.current(email));
  return complaints.oneWithDuplicates(complaintId);
 }

 public List<ComplaintResponse> groupDuplicates(String email, List<Long> ids) {
  requireAdmin(complaints.current(email));
  String groupId = complaints.groupDuplicates(ids);
  return complaints.findByDuplicateGroup(groupId);
 }

 public List<ComplaintResponse> groupDuplicatesByIdsParam(String email, String idsCsv) {
  if (idsCsv == null || idsCsv.isBlank())
   throw new ApiException(HttpStatus.BAD_REQUEST, "ids query param required (comma-separated)");
  List<Long> ids = Arrays.stream(idsCsv.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .map(Long::valueOf)
          .collect(Collectors.toList());
  return groupDuplicates(email, ids);
 }

 public ComplaintResponse ungroupDuplicate(String email, Long complaintId) {
  requireAdmin(complaints.current(email));
  complaints.ungroupDuplicate(complaintId);
  return complaints.one(complaintId);
 }

 @Transactional(readOnly = true)
 public GovernmentStats getGovernmentStats(String email) {
  requireAdmin(complaints.current(email));
  Instant today = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
  Instant sevenDaysAgo = today.minus(Duration.ofDays(7));
  Instant slaCutoff = Instant.now().minus(Duration.ofHours(48));

  long totalComplaints = complaintRepository.count();
  long reportedToday = complaintRepository.countReportedSince(today);
  long reportedLast7Days = complaintRepository.countReportedSince(sevenDaysAgo);
  long resolvedToday = complaintRepository.countResolvedSince(today);
  long resolvedLast7Days = complaintRepository.countResolvedSince(sevenDaysAgo);
  long slaBreached = complaintRepository.countSlaBreached(slaCutoff);

  long openComplaints = 0;
  long inProgressComplaints = 0;
  Map<String, Long> countsByStatus = new LinkedHashMap<>();
  for (ComplaintStatus s : ComplaintStatus.values()) {
   long c = complaintRepository.countByStatus(s);
   countsByStatus.put(s.name(), c);
   switch (s) {
    case REPORTED, UNDER_REVIEW -> openComplaints += c;
    case ASSIGNED, WORK_ACCEPTED, IN_PROGRESS, WORK_COMPLETED, CITIZEN_VERIFICATION, PAYMENT_APPROVED -> inProgressComplaints += c;
    default -> {}
   }
  }

  Map<String, Long> countsByPriority = new LinkedHashMap<>();
  for (PriorityLevel p : PriorityLevel.values()) {
   countsByPriority.put(p.name(), countComplaintsByPriority(p));
  }
  Map<String, Long> countsByIssueType = new LinkedHashMap<>();
  for (IssueType t : IssueType.values()) {
   countsByIssueType.put(t.name(), countComplaintsByIssueType(t));
  }
  Map<String, Long> countsByArea = complaintRepository.findAll().stream()
          .map(Complaint::getArea)
          .filter(Objects::nonNull)
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .collect(Collectors.groupingBy(a -> a, LinkedHashMap::new, Collectors.counting()));

  List<GovernmentStats.TopAreaStat> topAreas = countsByArea.entrySet().stream()
          .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
          .limit(6)
          .map(e -> new GovernmentStats.TopAreaStat(
                  e.getKey(),
                  e.getValue(),
                  countAreaStatusFiltered(e.getKey(), true),
                  countAreaStatusFiltered(e.getKey(), false)))
          .collect(Collectors.toList());

  Double avgResolutionHours = null;
  List<Complaint> resolvedWithDates = complaintRepository.findAllResolvedWithReportedAt();
  if (!resolvedWithDates.isEmpty()) {
   double totalHours = resolvedWithDates.stream()
           .filter(c -> c.getResolvedAt() != null && c.getReportedAt() != null)
           .mapToDouble(c -> Duration.between(c.getReportedAt(), c.getResolvedAt()).toMillis() / 3_600_000.0)
           .sum();
   long n = resolvedWithDates.size();
   avgResolutionHours = Math.round(totalHours * 10.0 / n) / 10.0;
  }

  long totalWorkers = workers.count();
  long verifiedWorkers = workers.countByVerificationStatus(VerificationStatus.VERIFIED);
  long activeAssignments = assignments.count() - assignments.countByCompletionStatus(CompletionStatus.REJECTED);
  long completedPublicAssignments = assignments.countByCompletionStatusAndComplaintIssueScope(
          CompletionStatus.APPROVED, IssueScope.PUBLIC);
  long unpaidPublicAssignments = completedPublicAssignments;
  long paidPublic = 0;
  for (var p : payments.findAll()) {
   if (p.getPaymentSource() != null && p.getPaymentSource().name().contains("GOVERNMENT")
           && p.getPaymentStatus() != null
           && (p.getPaymentStatus().name().equals("PAID") || p.getPaymentStatus().name().equals("VERIFIED"))) {
    paidPublic++;
   }
  }
  unpaidPublicAssignments = Math.max(0L, completedPublicAssignments - paidPublic);

  return new GovernmentStats(
          totalComplaints,
          openComplaints,
          inProgressComplaints,
          resolvedToday,
          resolvedLast7Days,
          slaBreached,
          avgResolutionHours,
          reportedToday,
          reportedLast7Days,
          totalWorkers,
          verifiedWorkers,
          activeAssignments,
          completedPublicAssignments,
          unpaidPublicAssignments,
          countsByStatus,
          countsByPriority,
          countsByIssueType,
          countsByArea,
          topAreas
  );
 }

 @Transactional(readOnly = true)
 public List<GeoComplaint> listComplaintsForMap(String email, ComplaintStatus status,
                                                PriorityLevel priority, IssueType issueType,
                                                String area, Instant from, Instant to) {
  requireAdmin(complaints.current(email));
  return complaintRepository.findAllWithCoordinates(status, priority, issueType, area, from, to)
          .stream()
          .map(c -> new GeoComplaint(
                  c.getId(),
                  c.getTitle(),
                  c.getStatus() == null ? null : c.getStatus().name(),
                  c.getPriority() == null ? null : c.getPriority().name(),
                  c.getIssueType() == null ? null : c.getIssueType().name(),
                  c.getArea(),
                  c.getCity(),
                  c.getLatitude(),
                  c.getLongitude(),
                  c.getReportedAt() == null ? null : c.getReportedAt().toString()
          ))
          .collect(Collectors.toList());
 }

 private long countComplaintsByPriority(PriorityLevel p) {
  long n = 0;
  for (var c : complaintRepository.findAll()) if (c.getPriority() == p) n++;
  return n;
 }
 private long countComplaintsByIssueType(IssueType t) {
  long n = 0;
  for (var c : complaintRepository.findAll()) if (c.getIssueType() == t) n++;
  return n;
 }
 private long countAreaStatusFiltered(String area, boolean open) {
  long n = 0;
  for (var c : complaintRepository.findAll()) {
   if (area.equals(c.getArea())) {
    boolean isOpen = c.getStatus() != null
            && c.getStatus() != ComplaintStatus.RESOLVED
            && c.getStatus() != ComplaintStatus.REJECTED;
    if (open == isOpen) n++;
   }
  }
  return n;
 }

 private Worker get(Long id){
  return workers.findById(id).orElseThrow(()->
          new ApiException(HttpStatus.NOT_FOUND,"Worker not found"));
 }
 private void requireAdmin(User user){
  if(user.getRole()!= Role.ADMIN)
   throw new ApiException(HttpStatus.FORBIDDEN,"This action requires ADMIN role");}
}
