package com.civic_connect.backend.notification.entity;

import com.civic_connect.backend.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    @Column(updatable = false) private Instant createdAt;
    private boolean isRead;

    @PrePersist void created() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
