package com.lcmob.smartask.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 对话记录实体
 *
 * 【对应表】conversations
 * 【职责】持久化存储用户与AI的对话记录
 *
 * 【字段说明】
 *   - id：自增主键
 *   - user：关联用户（多对一）
 *   - question：用户提问内容
 *   - answer：AI回答内容
 *   - timestamp：对话时间戳（自动填充）
 *
 * 【索引】
 *   - user_id：加速按用户查询
 *   - timestamp：加速按时间范围查询
 *
 * 【与Redis的关系】
 *   - Redis存储当前会话的最近20条消息（实时聊天用）
 *   - MySQL存储所有对话记录（历史查询用）
 */
@Data
@Entity
@Table(name = "conversations", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_timestamp", columnList = "timestamp")
})
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 对话记录唯一标识

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 关联用户

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question; // 用户提问内容

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer; // 系统回答内容

    @CreationTimestamp
    private LocalDateTime timestamp; // 对话时间戳
}