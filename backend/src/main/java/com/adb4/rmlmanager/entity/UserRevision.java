package com.adb4.rmlmanager.entity;

import com.adb4.rmlmanager.config.UserRevisionListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import java.util.UUID;

@Entity
@RevisionEntity(UserRevisionListener.class)
@Table(name = "revinfo")
@Getter
@Setter
public class UserRevision {
    @Id
    @GeneratedValue
    @RevisionNumber
    private long id;

    @RevisionTimestamp
    private long timestamp;

    @Column(name = "user_id")
    private UUID userId;
}
