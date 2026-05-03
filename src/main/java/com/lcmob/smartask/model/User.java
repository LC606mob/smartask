package com.lcmob.smartask.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户实体
 *
 * 【对应表】users
 * 【职责】存储用户基本信息和组织标签
 *
 * 【字段说明】
 *   - id：用户唯一标识（自增主键）
 *   - username：用户名（唯一）
 *   - password：密码（BCrypt加密存储）
 *   - role：角色（USER/ADMIN）
 *   - orgTags：所属组织标签（逗号分隔，如"TECH,DEFAULT"）
 *   - primaryOrg：主组织标签（上传文档时默认使用）
 */
@Data
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "org_tags")
    private String orgTags; // 用户所属组织标签，多个用逗号分隔

    @Column(name = "primary_org")
    private String primaryOrg; // 用户主组织标签

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum Role {
        USER, ADMIN
    }
}
