package com.civic_connect.backend.admin.controller;
import com.civic_connect.backend.admin.dto.GeoComplaint;
import com.civic_connect.backend.admin.dto.GovernmentStats;
import com.civic_connect.backend.admin.service.WorkforceAdminService;
import com.civic_connect.backend.common.enums.ComplaintStatus;
import com.civic_connect.backend.common.enums.IssueType;
import com.civic_connect.backend.common.enums.PriorityLevel;
import com.civic_connect.backend.complaint.dto.ComplaintResponse;
import com.civic_connect.backend.complaint.service.ComplaintService;
import com.civic_connect.backend.sla.service.SlaService;
import com.civic_connect.backend.worker.dto.WorkerResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController @RequestMapping("/api/admin")

public class AdminController {
 private final ComplaintService complaints;
 private final WorkforceAdminService workforce;
 private final SlaService sla;
 public AdminController(ComplaintService complaints, WorkforceAdminService workforce, SlaService sla) {
  this.complaints=complaints; this.workforce=workforce; this.sla=sla;
 }
 @GetMapping("/stats")
 public GovernmentStats stats(Authentication authentication) {
  return workforce.getGovernmentStats(authentication.getName());
 }
 @GetMapping("/complaints/geo")
 public List<GeoComplaint> geoComplaints(Authentication a,
                                         @RequestParam(value="status", required=false) ComplaintStatus status,
                                         @RequestParam(value="priority", required=false) PriorityLevel priority,
                                         @RequestParam(value="issueType", required=false) IssueType issueType,
                                         @RequestParam(value="area", required=false) String area,
                                         @RequestParam(value="from", required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
                                         @RequestParam(value="to", required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to) {
  return workforce.listComplaintsForMap(a.getName(), status, priority, issueType, area, from, to);
 }
 @GetMapping("/workers")
 public java.util.List<WorkerResponse> workers(Authentication authentication) {
  return workforce.list(authentication.getName());
 }
 @PatchMapping("/complaints/{id}/priority")
 public ComplaintResponse priority(Authentication a,@PathVariable("id") Long id,
                                   @RequestParam("priority") PriorityLevel priority) {
  return complaints.setPriority(a.getName(),id,priority);
 }
 @PatchMapping("/complaints/{id}/classification")
 public ComplaintResponse overrideClassification(Authentication a,
                                                  @PathVariable("id") Long id,
                                                  @RequestParam("category") IssueType category,
                                                  @RequestParam("priority") PriorityLevel priority,
                                                  @RequestParam(name="apply", defaultValue="true") boolean applyToComplaint) {
  return workforce.overrideClassification(a.getName(),id,category,priority,applyToComplaint);
 }
 @GetMapping("/complaints/{id}/duplicates")
 public ComplaintResponse detectDuplicates(Authentication a, @PathVariable("id") Long id) {
  return workforce.detectDuplicates(a.getName(), id);
 }
 @PostMapping("/complaints/duplicates/group")
 public List<ComplaintResponse> groupDuplicates(Authentication a,
                                                 @RequestParam("ids") String idsCsv) {
  return workforce.groupDuplicatesByIdsParam(a.getName(), idsCsv);
 }
 @DeleteMapping("/complaints/{id}/duplicates/group")
 public ComplaintResponse ungroupDuplicate(Authentication a, @PathVariable("id") Long id) {
  return workforce.ungroupDuplicate(a.getName(), id);
 }
 @PostMapping("/complaints/{id}/escalate")
 public SlaService.ComplaintResponseEscalation escalate(Authentication a,
                                                        @PathVariable("id") Long id,
                                                        @RequestParam(value="level", required=false) Integer level) {
  return sla.escalate(a.getName(), id, level);
 }
 @GetMapping("/complaints/escalated")
 public List<SlaService.ComplaintResponseEscalation> escalated(Authentication a) {
  return sla.listEscalated(a.getName());
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
