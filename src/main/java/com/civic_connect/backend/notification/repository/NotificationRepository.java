package com.civic_connect.backend.notification.repository;

import com.civic_connect.backend.notification.entity.Notification;
import com.civic_connect.backend.user.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user);
}
