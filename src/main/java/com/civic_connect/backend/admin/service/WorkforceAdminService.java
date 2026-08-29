package com.civic_connect.backend.admin.service;

import com.civic_connect.backend.common.enums.Role;
import com.civic_connect.backend.common.enums.VerificationStatus;
import com.civic_connect.backend.common.exceptionHandler.ApiException;
import com.civic_connect.backend.complaint.service.ComplaintService;
import com.civic_connect.backend.user.entity.User;
import com.civic_connect.backend.worker.dto.WorkerResponse;
import java.time.Instant;

import com.civic_connect.backend.worker.entity.Worker;
import com.civic_connect.backend.worker.repository.WorkerRepository;
import com.civic_connect.backend.worker.service.WorkerService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class WorkforceAdminService {
 private final WorkerRepository workers;
 private final WorkerService workerService;
 private final ComplaintService complaints;
 public WorkforceAdminService(WorkerRepository workers,WorkerService workerService,ComplaintService complaints){
  this.workers=workers;
  this.workerService=workerService;
  this.complaints=complaints;
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
 private Worker get(Long id){
  return workers.findById(id).orElseThrow(()->
          new ApiException(HttpStatus.NOT_FOUND,"Worker not found"));
 }
 private void requireAdmin(User user){
  if(user.getRole()!= Role.ADMIN)
   throw new ApiException(HttpStatus.FORBIDDEN,"This action requires ADMIN role");}
}
