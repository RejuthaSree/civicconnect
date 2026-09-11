package com.civic_connect.backend.notification.controller;

import com.civic_connect.backend.common.exceptionHandler.ApiException;
import com.civic_connect.backend.notification.dto.NotificationResponse;
import com.civic_connect.backend.notification.entity.Notification;
import com.civic_connect.backend.notification.repository.NotificationRepository;
import com.civic_connect.backend.user.Repository.UserRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notifications;
    private final UserRepository users;

    public NotificationController(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    @GetMapping
    public List<NotificationResponse> list(Authentication authentication) {
        return notifications.findByUserOrderByCreatedAtDesc(users.findByEmail(authentication.getName())
                        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user not found")))
                .stream()
                .map(notification -> new NotificationResponse(
                        notification.getId(), notification.getTitle(), notification.getMessage(),
                        notification.getCreatedAt(), notification.isRead()))
                .toList();
    }

    @PostMapping("/{id}/read")
    @Transactional
    public void read(Authentication authentication, @PathVariable("id") Long id) {
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notification.getUser().getEmail().equals(authentication.getName())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Notification belongs to another user");
        }
        notification.setRead(true);
    }
}
