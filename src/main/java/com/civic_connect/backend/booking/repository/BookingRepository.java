package com.civic_connect.backend.booking.repository;

import java.util.List;

import com.civic_connect.backend.booking.entity.Booking;
import com.civic_connect.backend.common.enums.BookingStatus;
import com.civic_connect.backend.common.enums.IssueScope;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByCitizenIdOrderByCreatedAtDesc(Long citizenId);

    List<Booking> findByWorkerIdOrderByCreatedAtDesc(Long workerId);

    List<Booking> findByIssueIssueScopeAndBookingStatusOrderByCreatedAtDesc(IssueScope issueScope, BookingStatus bookingStatus);
}
