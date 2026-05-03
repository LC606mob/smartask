package com.lcmob.smartask.controller;

import com.lcmob.smartask.exception.CustomException;
import com.lcmob.smartask.model.Conversation;
import com.lcmob.smartask.model.User;
import com.lcmob.smartask.repository.ConversationRepository;
import com.lcmob.smartask.repository.UserRepository;
import com.lcmob.smartask.utils.JwtUtils;
import com.lcmob.smartask.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员对话历史控制器
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminConversationController {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 管理员查询聊天记录
     */
    @GetMapping("/conversation")
    public ResponseEntity<?> getConversations(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) Long userid,
            @RequestParam(required = false) String start_date,
            @RequestParam(required = false) String end_date) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_CONVERSATIONS");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "ADMIN_GET_CONVERSATIONS", "token_validation",
                        "FAILED_INVALID_TOKEN");
                monitor.end("获取对话历史失败：无效token");
                throw new CustomException("无效的token", HttpStatus.UNAUTHORIZED);
            }

            User admin = userRepository.findByUsername(username)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));

            if (admin.getRole() != User.Role.ADMIN) {
                LogUtils.logUserOperation(username, "ADMIN_GET_CONVERSATIONS", "authorization", "FAILED_NOT_ADMIN");
                monitor.end("获取对话历史失败：非管理员");
                throw new CustomException("权限不足", HttpStatus.FORBIDDEN);
            }

            LogUtils.logBusiness("ADMIN_GET_CONVERSATIONS", username, "开始查询管理员对话历史");

            LocalDateTime startDateTime = null;
            LocalDateTime endDateTime = null;

            if (start_date != null && !start_date.trim().isEmpty()) {
                try {
                    startDateTime = parseDateTime(start_date);
                    LogUtils.logBusiness("ADMIN_GET_CONVERSATIONS", username, "解析起始时间: %s -> %s", start_date,
                            startDateTime);
                } catch (Exception e) {
                    LogUtils.logBusinessError("ADMIN_GET_CONVERSATIONS", username, "起始时间解析失败: %s", e, start_date);
                    throw new CustomException("起始时间格式错误: " + start_date, HttpStatus.BAD_REQUEST);
                }
            }

            if (end_date != null && !end_date.trim().isEmpty()) {
                try {
                    endDateTime = parseDateTime(end_date);
                    LogUtils.logBusiness("ADMIN_GET_CONVERSATIONS", username, "解析结束时间: %s -> %s", end_date,
                            endDateTime);
                } catch (Exception e) {
                    LogUtils.logBusinessError("ADMIN_GET_CONVERSATIONS", username, "结束时间解析失败: %s", e, end_date);
                    throw new CustomException("结束时间格式错误: " + end_date, HttpStatus.BAD_REQUEST);
                }
            }

            List<Conversation> conversations;
            if (userid != null) {
                // 查指定用户
                User targetUser = userRepository.findById(userid)
                        .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));
                if (startDateTime != null && endDateTime != null) {
                    conversations = conversationRepository.findByUserIdAndTimestampBetween(
                            userid, startDateTime, endDateTime);
                } else {
                    conversations = conversationRepository.findByUserId(userid);
                }
            } else {
                // 查所有用户
                if (startDateTime != null && endDateTime != null) {
                    conversations = conversationRepository.findByTimestampBetween(startDateTime, endDateTime);
                } else {
                    conversations = conversationRepository.findAll();
                }
            }

            List<Map<String, Object>> formattedConversations = new ArrayList<>();
            for (Conversation conversation : conversations) {
                Map<String, Object> userMessage = new HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", conversation.getQuestion());
                userMessage.put("username", conversation.getUser().getUsername());
                userMessage.put("timestamp",
                        conversation.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
                formattedConversations.add(userMessage);

                Map<String, Object> assistantMessage = new HashMap<>();
                assistantMessage.put("role", "assistant");
                assistantMessage.put("content", conversation.getAnswer());
                assistantMessage.put("timestamp",
                        conversation.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
                formattedConversations.add(assistantMessage);
            }

            LogUtils.logUserOperation(username, "ADMIN_GET_CONVERSATIONS", "conversation_list", "SUCCESS");
            LogUtils.logBusiness("ADMIN_GET_CONVERSATIONS", username, "成功获取对话历史，共 %d 条记录",
                    formattedConversations.size());
            monitor.end("获取对话历史成功");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取对话历史成功");
            response.put("data", formattedConversations);
            return ResponseEntity.ok(response);
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_GET_CONVERSATIONS", username, "获取对话历史失败: %s", e, e.getMessage());
            monitor.end("获取对话历史失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_CONVERSATIONS", username, "获取对话历史异常: %s", e, e.getMessage());
            monitor.end("获取对话历史异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateTimeStr + "T00:00:00",
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            } catch (Exception ex) {
                throw new CustomException("时间格式错误，请使用 yyyy-MM-dd 或 yyyy-MM-dd'T'HH:mm:ss 格式",
                        HttpStatus.BAD_REQUEST);
            }
        }
    }
}
