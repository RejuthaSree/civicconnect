package com.civic_connect.backend;

import com.civic_connect.backend.booking.dto.BookingResponse;
import com.civic_connect.backend.booking.dto.CreateBookingRequest;
import com.civic_connect.backend.booking.dto.GovernmentVerifyAndPayResponse;
import com.civic_connect.backend.booking.entity.Booking;
import com.civic_connect.backend.booking.repository.BookingRepository;
import com.civic_connect.backend.booking.service.BookingService;
import com.civic_connect.backend.common.config.IssuePricingConfig;
import com.civic_connect.backend.common.enums.*;
import com.civic_connect.backend.common.exceptionHandler.ApiException;
import com.civic_connect.backend.complaint.Repository.ComplaintRepository;
import com.civic_connect.backend.complaint.dto.ComplaintResponse;
import com.civic_connect.backend.complaint.dto.CreateComplaintRequest;
import com.civic_connect.backend.complaint.entity.Complaint;
import com.civic_connect.backend.complaint.service.ComplaintService;
import com.civic_connect.backend.payment.dto.RazorpayOrderResponse;
import com.civic_connect.backend.payment.repository.PaymentRepository;
import com.civic_connect.backend.user.Repository.UserRepository;
import com.civic_connect.backend.user.entity.User;
import com.civic_connect.backend.worker.repository.WorkerRepository;
import com.civic_connect.backend.worker.entity.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ComplaintBookingPaymentFlowTest {

    @Autowired
    private ComplaintService complaintService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private IssuePricingConfig issuePricingConfig;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private User citizenUser;
    private User adminUser;
    private Worker verifiedWorker;
    private Worker pendingWorker;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        complaintRepository.deleteAll();
        workerRepository.deleteAll();
        userRepository.deleteAll();

        citizenUser = new User();
        citizenUser.setEmail("citizen-test@example.com");
        citizenUser.setUsername("Test Citizen");
        citizenUser.setRole(Role.CITIZEN);
        citizenUser = userRepository.save(citizenUser);

        adminUser = new User();
        adminUser.setEmail("admin-test@example.com");
        adminUser.setUsername("Test Admin");
        adminUser.setRole(Role.ADMIN);
        adminUser = userRepository.save(adminUser);

        User workerUser1 = new User();
        workerUser1.setEmail("worker-verified@example.com");
        workerUser1.setUsername("Verified Worker");
        workerUser1.setRole(Role.WORKER);
        workerUser1 = userRepository.save(workerUser1);

        verifiedWorker = new Worker();
        verifiedWorker.setUser(workerUser1);
        verifiedWorker.setSkill(WorkerSkill.PLUMBER);
        verifiedWorker.setServiceArea("Indiranagar");
        verifiedWorker.setCity("Bangalore");
        verifiedWorker.setAvailable(true);
        verifiedWorker.setVerificationStatus(VerificationStatus.VERIFIED);
        verifiedWorker.setWorkRadiusKm(10.0);
        verifiedWorker.setTotalEarnings(0.0);
        verifiedWorker.setTotalGovernmentPaymentsReceived(0.0);
        verifiedWorker = workerRepository.save(verifiedWorker);

        User workerUser2 = new User();
        workerUser2.setEmail("worker-pending@example.com");
        workerUser2.setUsername("Pending Worker");
        workerUser2.setRole(Role.WORKER);
        workerUser2 = userRepository.save(workerUser2);

        pendingWorker = new Worker();
        pendingWorker.setUser(workerUser2);
        pendingWorker.setSkill(WorkerSkill.PLUMBER);
        pendingWorker.setServiceArea("Indiranagar");
        pendingWorker.setCity("Bangalore");
        pendingWorker.setAvailable(true);
        pendingWorker.setVerificationStatus(VerificationStatus.PENDING);
        pendingWorker.setWorkRadiusKm(10.0);
        pendingWorker.setTotalEarnings(0.0);
        pendingWorker.setTotalGovernmentPaymentsReceived(0.0);
        pendingWorker = workerRepository.save(pendingWorker);
    }

    private Complaint createComplaint(IssueType type, IssueScope scope) {
        CreateComplaintRequest req = new CreateComplaintRequest(
                "Test " + type + " complaint",
                "Description of " + type + " issue",
                "123 Main St",
                "Indiranagar",
                "Bangalore",
                12.9716,
                77.5946,
                null,
                type,
                PriorityLevel.MEDIUM,
                scope
        );
        ComplaintResponse resp = complaintService.create(citizenUser.getEmail(), req);
        return complaintRepository.findById(resp.id()).orElseThrow();
    }

    private Complaint createComplaintNoScope(IssueType type) {
        CreateComplaintRequest req = new CreateComplaintRequest(
                "Test " + type + " complaint no scope",
                "Description",
                "123 Main St",
                "Indiranagar",
                "Bangalore",
                12.9716,
                77.5946,
                null,
                type,
                PriorityLevel.MEDIUM,
                null
        );
        ComplaintResponse resp = complaintService.create(citizenUser.getEmail(), req);
        return complaintRepository.findById(resp.id()).orElseThrow();
    }

    @Test
    @DisplayName("TR-T2a: PLUMBING with PUBLIC scope returns 400 HOUSEHOLD required")
    void complaintPlumbingWithPublicScopeRejected() {
        ApiException ex = assertThrows(ApiException.class, () ->
                createComplaint(IssueType.PLUMBING, IssueScope.PUBLIC));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().toUpperCase().contains("HOUSEHOLD")
                || ex.getMessage().toLowerCase().contains("citizen"),
                "Error message should mention HOUSEHOLD or citizen-paid");
    }

    @Test
    @DisplayName("TR-T2b: ROAD with HOUSEHOLD scope returns 400 PUBLIC required")
    void complaintRoadWithHouseholdScopeRejected() {
        ApiException ex = assertThrows(ApiException.class, () ->
                createComplaint(IssueType.ROAD, IssueScope.HOUSEHOLD));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().toUpperCase().contains("PUBLIC")
                        || ex.getMessage().toLowerCase().contains("government"),
                "Error message should mention PUBLIC or government pays");
    }

    @Test
    @DisplayName("TR-T2c: PLUMBING without issueScope auto-assigns HOUSEHOLD")
    void complaintPlumbingNoScopeAutoAssignsHousehold() {
        Complaint c = createComplaintNoScope(IssueType.PLUMBING);
        assertEquals(IssueScope.HOUSEHOLD, c.getIssueScope(),
                "PLUMBING is a household type, so scope must be HOUSEHOLD");
        assertEquals(IssueType.PLUMBING, c.getIssueType());
    }

    @Test
    @DisplayName("TR-T2d: ROAD without issueScope auto-assigns PUBLIC")
    void complaintRoadNoScopeAutoAssignsPublic() {
        Complaint c = createComplaintNoScope(IssueType.ROAD);
        assertEquals(IssueScope.PUBLIC, c.getIssueScope(),
                "ROAD is a public type, so scope must be PUBLIC");
        assertEquals(IssueType.ROAD, c.getIssueType());
    }

    @Test
    @DisplayName("TR-T3a: PLUMBING booking amount overridden to 1000.0 ignoring request amount 1")
    void bookingPlumbingIgnoresRequestAmount() {
        Complaint plumbing = createComplaint(IssueType.PLUMBING, IssueScope.HOUSEHOLD);
        CreateBookingRequest req = new CreateBookingRequest(verifiedWorker.getId(), plumbing.getId(), 1.0);
        BookingResponse resp = bookingService.create(citizenUser.getEmail(), req);
        assertEquals(1000.0, resp.amount(),
                "PLUMBING fixed price is 1000.0, request amount must be ignored");
        assertEquals(issuePricingConfig.getFixedPrice(IssueType.PLUMBING), resp.amount());
    }

    @Test
    @DisplayName("TR-T3b: ROAD booking amount overridden to 2500.0 ignoring request amount 9999")
    void bookingRoadIgnoresRequestAmount() {
        Complaint road = createComplaint(IssueType.ROAD, IssueScope.PUBLIC);
        CreateBookingRequest req = new CreateBookingRequest(verifiedWorker.getId(), road.getId(), 9999.0);
        BookingResponse resp = bookingService.create(citizenUser.getEmail(), req);
        assertEquals(2500.0, resp.amount(),
                "ROAD fixed price is 2500.0, request amount must be ignored");
        assertEquals(issuePricingConfig.getFixedPrice(IssueType.ROAD), resp.amount());
    }

    @Test
    @DisplayName("TR-T3c: HOUSEHOLD booking accepts PENDING worker")
    void bookingHouseholdAllowsPendingWorker() {
        Complaint plumbing = createComplaint(IssueType.PLUMBING, IssueScope.HOUSEHOLD);
        assertEquals(VerificationStatus.PENDING, pendingWorker.getVerificationStatus());
        CreateBookingRequest req = new CreateBookingRequest(pendingWorker.getId(), plumbing.getId(), 1000.0);
        BookingResponse resp = bookingService.create(citizenUser.getEmail(), req);
        assertNotNull(resp.id());
        assertEquals(pendingWorker.getId(), resp.workerId());
        assertEquals(plumbing.getId(), resp.issueId());
    }

    @Test
    @DisplayName("TR-T3d: PUBLIC booking rejects PENDING worker with 400")
    void bookingPublicRejectsPendingWorker() {
        Complaint road = createComplaint(IssueType.ROAD, IssueScope.PUBLIC);
        assertEquals(VerificationStatus.PENDING, pendingWorker.getVerificationStatus());
        CreateBookingRequest req = new CreateBookingRequest(pendingWorker.getId(), road.getId(), 2500.0);
        ApiException ex = assertThrows(ApiException.class, () ->
                bookingService.create(citizenUser.getEmail(), req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().toLowerCase().contains("verified"),
                "Error message should mention verified workers requirement");
    }

    @Test
    @DisplayName("TR-T4a: PUBLIC booking confirm-completion moves to GOVERNMENT_VERIFICATION_PENDING")
    void publicBookingConfirmGoesToGovernmentVerificationPending() {
        Complaint road = createComplaint(IssueType.ROAD, IssueScope.PUBLIC);
        Booking booking = buildAndAdvanceBooking(road, verifiedWorker, BookingStatus.COMPLETED);

        BookingResponse resp = bookingService.confirmCompletion(citizenUser.getEmail(), booking.getId());
        assertEquals(BookingStatus.GOVERNMENT_VERIFICATION_PENDING, resp.bookingStatus(),
                "PUBLIC booking must go to GOVERNMENT_VERIFICATION_PENDING, not PAYMENT_PENDING");
        assertNotEquals(BookingStatus.PAYMENT_PENDING, resp.bookingStatus());
    }

    @Test
    @DisplayName("TR-T4b: HOUSEHOLD booking confirm-completion moves directly to PAYMENT_PENDING")
    void householdBookingConfirmGoesStraightToPaymentPending() {
        Complaint plumbing = createComplaint(IssueType.PLUMBING, IssueScope.HOUSEHOLD);
        Booking booking = buildAndAdvanceBooking(plumbing, verifiedWorker, BookingStatus.COMPLETED);

        BookingResponse resp = bookingService.confirmCompletion(citizenUser.getEmail(), booking.getId());
        assertEquals(BookingStatus.PAYMENT_PENDING, resp.bookingStatus(),
                "HOUSEHOLD booking must go straight to PAYMENT_PENDING (no admin step)");
    }

    @Test
    @DisplayName("TR-T4c: ADMIN government-verify-and-pay transitions to PAYMENT_PENDING and returns order data")
    void adminGovernmentVerifyAndPayProducesOrder() {
        Complaint road = createComplaint(IssueType.ROAD, IssueScope.PUBLIC);
        Booking booking = buildAndAdvanceBooking(road, verifiedWorker, BookingStatus.COMPLETED);
        bookingService.confirmCompletion(citizenUser.getEmail(), booking.getId());

        GovernmentVerifyAndPayResponse resp = invokeGovernmentVerifyAndPay(adminUser.getEmail(), booking.getId());

        assertNotNull(resp, "Government verify & pay response must not be null");
        assertNotNull(resp.booking(), "Booking response must be present");
        assertEquals(BookingStatus.PAYMENT_PENDING, resp.booking().bookingStatus(),
                "After admin verify, booking must be PAYMENT_PENDING");

        RazorpayOrderResponse order = resp.razorpayOrder();
        assertNotNull(order, "Razorpay order data must be returned in the response");
        assertNotNull(order.orderId(), "Razorpay orderId must be present (even in simulated/stubbed environments)");
        assertEquals(2500.0, order.amount(), 0.001, "Order amount matches ROAD fixed price");
        assertEquals(road.getIssueScope(), IssueScope.PUBLIC);
    }

    @Test
    @DisplayName("TR-T4d: Non-admin (citizen) cannot call government-verify-and-pay (403)")
    void nonAdminCannotCallGovernmentVerifyAndPay() {
        Complaint road = createComplaint(IssueType.ROAD, IssueScope.PUBLIC);
        Booking booking = buildAndAdvanceBooking(road, verifiedWorker, BookingStatus.COMPLETED);
        bookingService.confirmCompletion(citizenUser.getEmail(), booking.getId());

        ApiException ex = assertThrows(ApiException.class, () ->
                invokeGovernmentVerifyAndPay(citizenUser.getEmail(), booking.getId()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus(),
                "Non-admin must receive 403 Forbidden");
    }

    @Test
    @DisplayName("IssuePricingConfig validates all enum references resolve (AC1 sanity)")
    void pricingConfigAllReferencesResolve() {
        for (IssueType t : IssueType.values()) {
            IssueScope required = issuePricingConfig.getRequiredScope(t);
            assertNotNull(required, "Required scope resolves for " + t);
            assertTrue(issuePricingConfig.isValidScopeForType(t, required),
                    "Scope validation passes for " + t + "/" + required);
            Double price = issuePricingConfig.getFixedPrice(t);
            assertNotNull(price, "Fixed price exists for " + t);
            assertTrue(price > 0, "Price is positive for " + t);
            WorkerSkill skill = issuePricingConfig.getRequiredSkill(t);
            assertNotNull(skill, "Required skill resolves for " + t);
        }
    }

    @Test
    @DisplayName("AC8 combined flow: PUBLIC full path vs HOUSEHOLD skips admin gate")
    void combinedPublicVersusHouseholdFlow() {
        Complaint publicIssue = createComplaint(IssueType.ROAD, IssueScope.PUBLIC);
        Booking publicBooking = buildAndAdvanceBooking(publicIssue, verifiedWorker, BookingStatus.COMPLETED);
        BookingResponse afterCitizenConfirm = bookingService.confirmCompletion(citizenUser.getEmail(), publicBooking.getId());
        assertEquals(BookingStatus.GOVERNMENT_VERIFICATION_PENDING, afterCitizenConfirm.bookingStatus(),
                "PUBLIC: citizen confirm → GOV_VERIFY_PENDING");
        ApiException tooEarlyPay = assertThrows(ApiException.class, () ->
                invokeGovernmentVerifyAndPay(citizenUser.getEmail(), publicBooking.getId()));
        assertEquals(HttpStatus.FORBIDDEN, tooEarlyPay.getStatus(),
                "PUBLIC: citizen payment-creation attempt is 403 (must be admin)");

        GovernmentVerifyAndPayResponse adminResp = invokeGovernmentVerifyAndPay(adminUser.getEmail(), publicBooking.getId());
        assertEquals(BookingStatus.PAYMENT_PENDING, adminResp.booking().bookingStatus(),
                "PUBLIC: admin verify → PAYMENT_PENDING");
        assertNotNull(adminResp.razorpayOrder().orderId());

        Complaint householdIssue = createComplaint(IssueType.PLUMBING, IssueScope.HOUSEHOLD);
        Booking householdBooking = buildAndAdvanceBooking(householdIssue, verifiedWorker, BookingStatus.COMPLETED);
        BookingResponse afterHouseholdConfirm = bookingService.confirmCompletion(citizenUser.getEmail(), householdBooking.getId());
        assertEquals(BookingStatus.PAYMENT_PENDING, afterHouseholdConfirm.bookingStatus(),
                "HOUSEHOLD: citizen confirm → PAYMENT_PENDING with NO admin intervention");
        assertNotEquals(BookingStatus.GOVERNMENT_VERIFICATION_PENDING, afterHouseholdConfirm.bookingStatus());
    }

    private Booking buildAndAdvanceBooking(Complaint issue, Worker worker, BookingStatus targetStatus) {
        Booking booking = new Booking();
        booking.setCitizen(citizenUser);
        booking.setWorker(worker);
        booking.setIssue(issue);
        booking.setAmount(issuePricingConfig.getFixedPrice(issue.getIssueType()));
        booking.setBookingStatus(BookingStatus.PENDING);
        booking.setPaymentRequired(true);
        booking = bookingRepository.save(booking);

        if (targetStatus == BookingStatus.PENDING) return booking;

        booking.setBookingStatus(BookingStatus.ACCEPTED);
        booking = bookingRepository.save(booking);
        if (targetStatus == BookingStatus.ACCEPTED) return booking;

        booking.setBookingStatus(BookingStatus.IN_PROGRESS);
        booking = bookingRepository.save(booking);
        if (targetStatus == BookingStatus.IN_PROGRESS) return booking;

        booking.setBookingStatus(BookingStatus.COMPLETED);
        booking = bookingRepository.save(booking);
        return booking;
    }

    private GovernmentVerifyAndPayResponse invokeGovernmentVerifyAndPay(String email, Long bookingId) {
        BookingResponse verified = bookingService.governmentVerify(email, bookingId);
        RazorpayOrderResponse order = simulatePaymentServiceCreateOrder(email, verified.id(), verified.amount());
        return new GovernmentVerifyAndPayResponse(verified, order);
    }

    private RazorpayOrderResponse simulatePaymentServiceCreateOrder(String email, Long bookingId, Double amount) {
        User caller = userRepository.findByEmail(email).orElse(null);
        assertNotNull(caller, "Caller user must exist");
        if (caller.getRole() != Role.ADMIN) {
            throw new com.civic_connect.backend.common.exceptionHandler.ApiException(
                    HttpStatus.FORBIDDEN, "This action requires ADMIN role");
        }
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        if (booking.getBookingStatus() != BookingStatus.PAYMENT_PENDING) {
            throw new com.civic_connect.backend.common.exceptionHandler.ApiException(
                    HttpStatus.BAD_REQUEST, "Payment is available only after citizen completion confirmation");
        }
        if (amount == null || Double.compare(booking.getAmount(), amount) != 0) {
            throw new com.civic_connect.backend.common.exceptionHandler.ApiException(
                    HttpStatus.BAD_REQUEST, "Payment amount must match the booking amount");
        }
        return new RazorpayOrderResponse(
                "sim-order-" + bookingId + "-" + System.nanoTime(),
                amount,
                "INR",
                "test-key-id"
        );
    }
}
