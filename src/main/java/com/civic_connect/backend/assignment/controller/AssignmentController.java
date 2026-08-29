package com.civic_connect.backend.assignment.controller;
import com.civic_connect.backend.assignment.service.AssignmentService;
import com.civic_connect.backend.assignment.dto.AssignmentResponse;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/assignments")
public class AssignmentController {
 private final AssignmentService service;
 public AssignmentController(AssignmentService service){
  this.service=service;
 }
 @PostMapping public AssignmentResponse assign
         (Authentication a,
          @RequestParam("complaintId") Long complaintId,
          @RequestParam("workerId") Long workerId) {

 return service.assign(a.getName(),complaintId,workerId);
 }
 @PostMapping("/request")
 public AssignmentResponse request(Authentication a,@RequestParam("complaintId") Long complaintId,
                                   @RequestParam("workerId") Long workerId){
  return service.request(a.getName(),complaintId,workerId);
 }
 @PostMapping("/{id}/accept")
 public AssignmentResponse accept(Authentication a,
                                  @PathVariable("id") Long id) {
  return service.accept(a.getName(),id);
 }

 @PostMapping("/{id}/complete")
 public AssignmentResponse complete
         (Authentication a,
          @PathVariable("id") Long id,
          @RequestBody(required=false)
          Map<String,String> b){
  return service.complete(a.getName(),id,b==null?null:b.get("remarks"),
          b==null?null:b.get("beforeImageUrl"), b==null?null:b.get("afterImageUrl"));
 }
 @PostMapping("/{id}/citizen-verification")
 public AssignmentResponse citizenVerify(Authentication a,
                                         @PathVariable("id") Long id,
                                         @RequestParam("approved") boolean approved){
  return service.citizenVerify(a.getName(),id,approved);
 }
}
