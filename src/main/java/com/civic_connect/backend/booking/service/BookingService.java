package com.civic_connect.backend.booking.service;

import com.civic_connect.backend.booking.dto.BookingResponse;
import com.civic_connect.backend.booking.dto.CreateBookingRequest;
import com.civic_connect.backend.booking.entity.Booking;
import com.civic_connect.backend.booking.repository.BookingRepository;
import com.civic_connect.backend.common.exceptionHandler.ApiException;
import com.civic_connect.backend.common.enums.BookingStatus;
import com.civic_connect.backend.common.enums.Role;
import com.civic_connect.backend.common.enums.VerificationStatus;
import com.civic_connect.backend.complaint.entity.Complaint;
import com.civic_connect.backend.complaint.service.ComplaintService;
import com.civic_connect.backend.user.entity.User;
import com.civic_connect.backend.worker.entity.Worker;
import com.civic_connect.backend.worker.repository.WorkerRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BookingService {

    private final BookingRepository bookings;
    private final WorkerRepository workers;
    private final ComplaintService complaints;

    public BookingService(
            BookingRepository bookings,
            WorkerRepository workers,
            ComplaintService complaints) {
        this.bookings = bookings;
        this.workers = workers;
        this.complaints = complaints;
    }

    public BookingResponse create(String email, CreateBookingRequest request) {
        User citizen = complaints.current(email);
        requireRole(citizen, Role.CITIZEN);
        Complaint issue = complaints.get(request.issueId());
        if (!issue.getReportedBy().getId().equals(citizen.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can book work only for your own issue");
        }

        Worker worker = worker(request.workerId());
        if (!worker.isAvailable() || worker.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only available verified workers can be booked");
        }

        Booking booking = new Booking();
        booking.setCitizen(citizen);
        booking.setWorker(worker);
        booking.setIssue(issue);
        booking.setAmount(request.amount());
        return response(bookings.save(booking));
    }

    public BookingResponse accept(String email, Long bookingId) {
        Booking booking = booking(bookingId);
        assertWorker(email, booking);
        transition(booking, BookingStatus.PENDING, BookingStatus.ACCEPTED);
        return response(booking);
    }

    public BookingResponse beginWork(String email, Long bookingId) {
        Booking booking = booking(bookingId);
        assertWorker(email, booking);
        transition(booking, BookingStatus.ACCEPTED, BookingStatus.IN_PROGRESS);
        return response(booking);
    }

    public BookingResponse complete(String email, Long bookingId) {
        Booking booking = booking(bookingId);
        assertWorker(email, booking);
        transition(booking, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED);
        return response(booking);
    }

    public BookingResponse confirmCompletion(String email, Long bookingId) {
        Booking booking = booking(bookingId);
        if (!booking.getCitizen().getEmail().equals(email)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the booking citizen can confirm completion");
        }
        transition(booking, BookingStatus.COMPLETED, BookingStatus.PAYMENT_PENDING);
        return response(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> mine(String email) {
        User user = complaints.current(email);
        List<Booking> results = user.getRole() == Role.WORKER
                ? workers.findByUser(user).map(worker -> bookings.findByWorkerIdOrderByCreatedAtDesc(worker.getId())).orElse(List.of())
                : bookings.findByCitizenIdOrderByCreatedAtDesc(user.getId());
        return results.stream().map(this::response).toList();
    }

    public Booking get(Long id) {
        return booking(id);
    }

    public BookingResponse response(Booking booking) {
        return new BookingResponse(
                booking.getId(), booking.getCitizen().getId(), booking.getWorker().getId(),
                booking.getIssue().getId(), booking.getBookingStatus(), booking.getAmount(),
                booking.isPaymentRequired(), booking.getCreatedAt(), booking.getUpdatedAt());
    }

    private Booking booking(Long id) {
        return bookings.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private Worker worker(Long id) {
        return workers.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Worker not found"));
    }

    private void assertWorker(String email, Booking booking) {
        User user = complaints.current(email);
        requireRole(user, Role.WORKER);
        if (workers.findByUser(user).isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Worker profile not found for this account");
        }
        if (!booking.getWorker().getUser().getEmail().equals(email)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Booking belongs to another worker");
        }
    }

    private void transition(Booking booking, BookingStatus expected, BookingStatus next) {
        if (booking.getBookingStatus() != expected) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Booking is not in " + expected + " status");
        }
        booking.setBookingStatus(next);
    }

    private void requireRole(User user, Role role) {
        if (user.getRole() != role) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This action requires " + role + " role");
        }
    }
}
