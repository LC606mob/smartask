package com.lcmob.smartask.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 组织标签实体
 *
 * 【对应表】organization_tags
 * 【职责】定义组织标签，支持树形结构
 *
 * 【字段说明】
 *   - tagId：标签唯一标识（如"TECH"、"DEFAULT"）
 *   - name：标签名称（如"技术部"）
 *   - description：标签描述
 *   - parentTag：父标签ID（实现层级关系）
 *   - createdBy：创建者
 *   - createdAt：创建时间
 *   - updatedAt：更新时间
 *
 * 【层级结构示例】
 *   DEFAULT（默认组织）
 *   └── COMPANY（公司）
 *       ├── TECH（技术部）
 *       │   ├── BACKEND（后端组）
 *       │   └── FRONTEND（前端组）
 *       └── SALES（销售部）
 *
 * 【权限继承】
 *   用户属于"后端组"，可以访问"技术部"和"公司"的文档
 */
@Data
@Entity
@Table(name = "organization_tags")
public class OrganizationTag {
    @Id
    @Column(name = "tag_id")
    private String tagId; // 标签唯一标识

    @Column(nullable = false)
    private String name; // 标签名称

    @Column(columnDefinition = "TEXT")
    private String description; // 描述

    @Column(name = "parent_tag", length = 255)
    private String parentTag; // 父标签ID

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy; // 创建者ID

    @CreationTimestamp
    private LocalDateTime createdAt; // 创建时间

    @UpdateTimestamp
    private LocalDateTime updatedAt; // 更新时间
} 