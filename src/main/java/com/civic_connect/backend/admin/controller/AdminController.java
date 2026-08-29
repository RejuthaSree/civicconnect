package com.civic_connect.backend.admin.controller;
import com.civic_connect.backend.admin.service.WorkforceAdminService;
import com.civic_connect.backend.common.enums.PriorityLevel;
import com.civic_connect.backend.complaint.service.ComplaintService;
import com.civic_connect.backend.complaint.dto.ComplaintResponse;
import com.civic_connect.backend.worker.dto.WorkerResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin")

public class AdminController {
 private final ComplaintService complaints;
 private final WorkforceAdminService workforce;
 public AdminController(ComplaintService complaints, WorkforceAdminService workforce) {
  this.complaints=complaints; this.workforce=workforce;
 }
 @PatchMapping("/complaints/{id}/priority")
 public ComplaintResponse priority(Authentication a,@PathVariable("id") Long id,
                                   @RequestParam("priority") PriorityLevel priority) {
  return complaints.setPriority(a.getName(),id,priority);
 }
 @PostMapping("/workers/{id}/verification")
 public WorkerResponse verifyWorker(Authentication a,@PathVariable("id") Long id,
                                    @RequestParam("approved") boolean approved,
                                    @RequestParam(name = "notes", required=false) String notes){

  return workforce.verify(a.getName(),id,approved,notes);
 }
 @PostMapping("/workers/{id}/availability")
 public WorkerResponse availability(Authentication a,@PathVariable("id") Long id,@RequestParam("available") boolean available){
  return workforce.availability(a.getName(),id,available);}
}
