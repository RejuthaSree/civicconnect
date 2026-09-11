package com.civic_connect.backend.worker.controller;

import com.civic_connect.backend.assignment.repository.AssignmentRepository;
import com.civic_connect.backend.common.enums.CompletionStatus;
import com.civic_connect.backend.review.repository.WorkerReviewRepository;
import com.civic_connect.backend.worker.dto.WorkerPortfolioItemResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/workers")
public class WorkerPortfolioController {
 private final AssignmentRepository assignments;
 private final WorkerReviewRepository reviews;
 public WorkerPortfolioController(AssignmentRepository assignments,WorkerReviewRepository reviews){
  this.assignments=assignments;
  this.reviews=reviews;
 }
 @GetMapping("/{workerId}/portfolio")
 public List<WorkerPortfolioItemResponse> portfolio(@PathVariable("workerId") Long workerId){
  return assignments.findByWorkerIdAndCompletionStatusOrderByCompletedAtDesc(workerId,
          CompletionStatus.APPROVED).stream().map(a->{var review=reviews.findByAssignmentId(a.getId()).
          orElse(null);return new WorkerPortfolioItemResponse(a.getId(),a.getComplaint().getTitle(),
          a.getComplaint().getArea(),a.getBeforeImageUrl(),a.getAfterImageUrl(),a.getCompletedAt(),
          review==null?null:review.getRating(),review==null?null:review.getComment());}).toList();
 }
}
