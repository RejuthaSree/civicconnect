package com.civic_connect.backend.review.service;

import com.civic_connect.backend.assignment.entity.Assignment;
import com.civic_connect.backend.assignment.repository.AssignmentRepository;
import com.civic_connect.backend.common.enums.CompletionStatus;
import com.civic_connect.backend.common.enums.Role;
import com.civic_connect.backend.common.exceptionHandler.ApiException;
import com.civic_connect.backend.complaint.service.ComplaintService;
import com.civic_connect.backend.review.dto.*;
import com.civic_connect.backend.review.entity.WorkerReview;
import com.civic_connect.backend.review.repository.WorkerReviewRepository;
import com.civic_connect.backend.user.entity.User;
import com.civic_connect.backend.worker.entity.Worker;
import com.civic_connect.backend.worker.repository.WorkerRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class WorkerReviewService {
 private final WorkerReviewRepository reviews;
 private final AssignmentRepository assignments;
 private final ComplaintService complaints;
 private final WorkerRepository workers;
 public WorkerReviewService(WorkerReviewRepository reviews, AssignmentRepository assignments,
                            ComplaintService complaints, WorkerRepository workers) {

  this.reviews=reviews;
  this.assignments=assignments;
  this.complaints=complaints;
  this.workers=workers;
 }
 public WorkerReviewResponse create(String email, Long assignmentId, CreateReviewRequest request) {

  Assignment assignment = assignments.findById(assignmentId).orElseThrow(()->
          new ApiException(HttpStatus.NOT_FOUND,"Assignment not found"));

  User citizen=complaints.current(email);

  if (citizen.getRole() != Role.CITIZEN)
   throw new ApiException(HttpStatus.FORBIDDEN,"This action requires CITIZEN role");

  if (!assignment.getComplaint().getReportedBy().getId().equals(citizen.getId()))
   throw new ApiException(HttpStatus.FORBIDDEN,"Only the reporting citizen may review this work");

  if (assignment.getCompletionStatus()!= CompletionStatus.APPROVED)
   throw new ApiException(HttpStatus.BAD_REQUEST,"Work must be citizen-approved before review");

  if (reviews.existsByAssignmentId(assignmentId))
   throw new ApiException(HttpStatus.CONFLICT,"A review already exists for this assignment");

  WorkerReview review=new WorkerReview();
  review.setAssignment(assignment);
  review.setWorker(assignment.getWorker());
  review.setRating(request.rating());
  review.setComment(request.comment());
  review=reviews.save(review);
  refreshRating(assignment.getWorker());
  return response(review);
 }
 @Transactional(readOnly=true) public List<WorkerReviewResponse> list(Long workerId) {

  Worker worker=workers.findById(workerId).orElseThrow(()->
          new ApiException(HttpStatus.NOT_FOUND,"Worker not found"));

  return reviews.findByWorkerOrderByCreatedAtDesc(worker).stream().map(this::response).toList();
 }

 private void refreshRating(Worker worker) {
  List<WorkerReview> all=reviews.findByWorkerOrderByCreatedAtDesc(worker);
  worker.setTotalReviews(all.size());
  worker.setRating(all.stream().mapToInt(WorkerReview::getRating).average().orElse(0));
 }
 private WorkerReviewResponse response(WorkerReview r) {
  return new WorkerReviewResponse(r.getId(),r.getAssignment().getId(),r.getWorker().getId(),
          r.getAssignment().getComplaint().getTitle()
          ,r.getRating(),r.getComment(),r.getCreatedAt());
 }
}
