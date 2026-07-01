package com.adb4.rmlmanager.entity;

import com.adb4.rmlmanager.enums.PermissionLevel;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "asset_permissions")
public class AssetPermission extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "app_user_id", nullable = false, updatable = false)
    private UUID appUserId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "level", nullable = false)
    private PermissionLevel level;

    @CreatedBy
    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;
}
