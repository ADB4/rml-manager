package com.adb4.rmlmanager.entity;

import com.adb4.rmlmanager.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", name = "id",  updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_name", nullable = false, length = 32)
    private String userName;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "role")
    private UserRole role;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
