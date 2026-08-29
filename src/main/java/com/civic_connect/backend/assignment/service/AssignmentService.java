package com.civic_connect.backend.assignment.service;
import com.civic_connect.backend.assignment.entity.Assignment;
import com.civic_connect.backend.assignment.repository.AssignmentRepository;
import com.civic_connect.backend.assignment.dto.AssignmentResponse;
import com.civic_connect.backend.common.enums.ComplaintStatus;
import com.civic_connect.backend.common.enums.CompletionStatus;
import com.civic_connect.backend.common.enums.Role;
import com.civic_connect.backend.common.enums.VerificationStatus;
import com.civic_connect.backend.common.exceptionHandler.ApiException;
import com.civic_connect.backend.complaint.entity.Complaint;
import com.civic_connect.backend.complaint.service.ComplaintService;
import com.civic_connect.backend.user.entity.User;
import com.civic_connect.backend.notification.service.NotificationService;
import java.time.Instant;

import com.civic_connect.backend.worker.entity.Worker;
import com.civic_connect.backend.worker.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class AssignmentService {
 private final AssignmentRepository assignments;
 private final ComplaintService complaints;
 private final WorkerRepository workers;
 private final NotificationService notifications;

 public AssignmentService(AssignmentRepository assignments,ComplaintService complaints,WorkerRepository workers, NotificationService notifications) {
  this.assignments=assignments;
  this.complaints=complaints;
  this.workers=workers;
  this.notifications=notifications;
 }
 public AssignmentResponse assign(String email,
                                  Long complaintId,
                                  Long workerId){
  User u=complaints.current(email);
  if(u.getRole()!= Role.ADMIN)
   throw new ApiException(HttpStatus.FORBIDDEN, "This action requires ADMIN role");

  Complaint c=complaints.get(complaintId);

  Worker w=workers.findById(workerId)
          .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND, "Worker not found"));

  if(!w.isAvailable())throw new ApiException(HttpStatus.BAD_REQUEST, "Worker is unavailable");
  if(w.getVerificationStatus()!= VerificationStatus.VERIFIED)
   throw new ApiException(HttpStatus.BAD_REQUEST, "Only verified workers can receive assignments");

  if(assignments.findByComplaintId(complaintId).isPresent())
   throw new ApiException(HttpStatus.CONFLICT, "Complaint already assigned");
  Assignment a=new Assignment();
  a.setComplaint(c);
  a.setWorker(w);
  c.setAssignedWorker(w.getUser());
  c.setStatus(ComplaintStatus.ASSIGNED);
  a=assignments.save(a); notifications.send(w.getUser(), "New assignment", c.getTitle()); return response(a);
 }

 /** Allows a citizen to request a verified worker for their own civic complaint. */
 public AssignmentResponse request(String email, Long complaintId, Long workerId) {
  User citizen=complaints.current(email);
  if(citizen.getRole()!=Role.CITIZEN) throw new ApiException(HttpStatus.FORBIDDEN, "This action requires CITIZEN role");
  Complaint c=complaints.get(complaintId);
  if(!c.getReportedBy().getId().equals(citizen.getId())) throw new ApiException(HttpStatus.FORBIDDEN, "You may request a worker only for your own complaint");
  Worker w=workers.findById(workerId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Worker not found"));
  if(!w.isAvailable() || w.getVerificationStatus()!=VerificationStatus.VERIFIED) throw new ApiException(HttpStatus.BAD_REQUEST,"Requested worker is unavailable or unverified");
  if(assignments.findByComplaintId(complaintId).isPresent()) throw new ApiException(HttpStatus.CONFLICT,"Complaint already assigned");
  Assignment assignment=new Assignment(); assignment.setComplaint(c); assignment.setWorker(w); c.setAssignedWorker(w.getUser()); c.setStatus(ComplaintStatus.ASSIGNED);
  assignment=assignments.save(assignment); notifications.send(w.getUser(), "Worker requested", c.getTitle()); return response(assignment);
 }

 public AssignmentResponse accept(String email,Long id){
  Assignment a=get(id);
  assertWorker(email,a);
  if(a.getAcceptedAt()==null)
   a.setAcceptedAt(Instant.now());
  a.getComplaint().setStatus(ComplaintStatus.WORK_ACCEPTED);
  notifications.send(a.getComplaint().getReportedBy(), "Worker accepted task", a.getComplaint().getTitle());

  return response(a);
 }

 public AssignmentResponse complete(String email,Long id,String remarks,String beforeImageUrl,String afterImageUrl) {

  Assignment a=get(id);
  assertWorker(email,a);
  if(a.getAcceptedAt()==null) throw new ApiException(HttpStatus.BAD_REQUEST, "Accept the assignment before completing it");
  a.setRemarks(remarks);
  a.setBeforeImageUrl(beforeImageUrl);
  a.setAfterImageUrl(afterImageUrl);
  a.setProofImageUrl(afterImageUrl);
  a.setCompletedAt(Instant.now());
  a.setCompletionStatus(CompletionStatus.PENDING_CITIZEN_APPROVAL);
  a.getComplaint().setStatus(ComplaintStatus.CITIZEN_VERIFICATION);
  notifications.send(a.getComplaint().getReportedBy(), "Verification required", a.getComplaint().getTitle());
  return response(a);
 }

 public AssignmentResponse citizenVerify(String email, Long id, boolean approved) {

  Assignment a=get(id);
  User citizen=complaints.current(email);

  if(!a.getComplaint().getReportedBy().getId().equals(citizen.getId()))
   throw new ApiException(HttpStatus.FORBIDDEN, "Only the reporting citizen may verify this work");

  if(a.getCompletionStatus()!=CompletionStatus.PENDING_CITIZEN_APPROVAL)

   throw new ApiException(HttpStatus.BAD_REQUEST, "Work is not awaiting citizen verification");
  a.setCitizenVerifiedAt(Instant.now());

  if(approved){
   a.setCompletionStatus(CompletionStatus.APPROVED);
   a.getComplaint().setStatus(ComplaintStatus.WORK_COMPLETED);
   Worker w=a.getWorker(); w.setCompletedTasks(w.getCompletedTasks()+1);
   notifications.send(w.getUser(), "Work verified", a.getComplaint().getTitle());
  }
  else {
   a.setCompletionStatus(CompletionStatus.REJECTED); a.getComplaint().setStatus(ComplaintStatus.IN_PROGRESS);
  }
  return response(a);
 }
 private Assignment get(Long id){
  return assignments.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND, "Assignment not found"));
 }

 private void assertWorker(String email,Assignment a){
  User user = complaints.current(email);
  if (user.getRole() != Role.WORKER)
   throw new ApiException(HttpStatus.FORBIDDEN, "This action requires WORKER role");
  if (workers.findByUser(user).isEmpty())
   throw new ApiException(HttpStatus.FORBIDDEN, "Worker profile not found for this account");
  if(!a.getWorker().getUser().getEmail().equals(email))
   throw new ApiException(HttpStatus.FORBIDDEN, "Assignment belongs to another worker");
 }

 private AssignmentResponse response(Assignment a){
  return new AssignmentResponse(a.getId(),
          a.getComplaint().getId(),
          a.getWorker().getId(),
          a.getAssignedAt(),
          a.getAcceptedAt(),
          a.getCompletedAt(),
          a.getRemarks(),
          a.getProofImageUrl(), a.getBeforeImageUrl(), a.getAfterImageUrl(), a.getCompletionStatus(), a.getCitizenVerifiedAt());
 }
}
