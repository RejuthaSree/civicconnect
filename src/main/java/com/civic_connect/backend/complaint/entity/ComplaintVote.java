package com.civic_connect.backend.complaint.entity;

import com.civic_connect.backend.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "complaint_votes", uniqueConstraints = @UniqueConstraint(columnNames = {"complaint_id", "voter_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ComplaintVote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Complaint complaint;

    @ManyToOne(optional = false)
    private User voter;
}
