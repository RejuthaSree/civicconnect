package com.civic_connect.backend.review.controller;

import com.civic_connect.backend.review.dto.*;
import com.civic_connect.backend.review.service.WorkerReviewService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class WorkerReviewController {
 private final WorkerReviewService service;

 public WorkerReviewController(WorkerReviewService service){
  this.service=service;
 }
 @PostMapping("/assignments/{assignmentId}/reviews")
 public WorkerReviewResponse create(Authentication a,@PathVariable("assignmentId") Long assignmentId,
                                    @Valid @RequestBody CreateReviewRequest r){
  return service.create(a.getName(),assignmentId,r);
 }
 @GetMapping("/workers/{workerId}/reviews") public List<WorkerReviewResponse> list(@PathVariable("workerId") Long workerId){
  return service.list(workerId);
 }
}
