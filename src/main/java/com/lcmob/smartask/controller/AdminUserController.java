package com.lcmob.smartask.controller;

import com.lcmob.smartask.exception.CustomException;
import com.lcmob.smartask.model.User;
import com.lcmob.smartask.repository.UserRepository;
import com.lcmob.smartask.service.UserProfileService;
import com.lcmob.smartask.utils.JwtUtils;
import com.lcmob.smartask.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理员用户管理控制器
 *
 * 【职责】管理员对用户的查询和管理操作
 * 【设计思路】
 *   - 支持分页、搜索、按组织标签过滤
 *   - 只有ADMIN角色可以访问
 *
 * 【调用链】
 *   管理员前端 → AdminUserController → UserProfileService → UserRepository
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminUserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 获取用户列表（支持分页、搜索和过滤）
     */
    @GetMapping({"/users", "/users/list"})
    public ResponseEntity<?> getUsers(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orgTag,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_USERS");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "ADMIN_GET_USERS", "token_validation", "FAILED_INVALID_TOKEN");
                monitor.end("获取用户列表失败：无效token");
                throw new CustomException("无效的token", HttpStatus.UNAUTHORIZED);
            }

            User admin = userRepository.findByUsername(username)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));

            if (admin.getRole() != User.Role.ADMIN) {
                LogUtils.logUserOperation(username, "ADMIN_GET_USERS", "authorization", "FAILED_NOT_ADMIN");
                monitor.end("获取用户列表失败：非管理员");
                throw new CustomException("权限不足", HttpStatus.FORBIDDEN);
            }

            LogUtils.logBusiness("ADMIN_GET_USERS", username,
                    "开始查询用户列表，keyword=%s, orgTag=%s, status=%s, page=%d, size=%d", keyword, orgTag, status,
                    page, size);

            Map<String, Object> result = userProfileService.getUserList(keyword, orgTag, status, page, size);

            LogUtils.logUserOperation(username, "ADMIN_GET_USERS", "user_list", "SUCCESS");
            LogUtils.logBusiness("ADMIN_GET_USERS", username, "成功获取用户列表，共 %d 条记录",
                    ((java.util.List<?>) result.get("content")).size());
            monitor.end("获取用户列表成功");

            Map<String, Object> response = new java.util.HashMap<>();
            response.put("code", 200);
            response.put("message", "获取用户列表成功");
            response.put("data", result);
            return ResponseEntity.ok(response);
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_GET_USERS", username, "获取用户列表失败: %s", e, e.getMessage());
            monitor.end("获取用户列表失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_USERS", username, "获取用户列表异常: %s", e, e.getMessage());
            monitor.end("获取用户列表异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    @PutMapping("/users/{userId}/org-tags")
    public ResponseEntity<?> assignOrgTags(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId,
            @RequestBody AssignOrgTagsRequest request) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_ASSIGN_ORG_TAGS");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "ADMIN_ASSIGN_ORG_TAGS", "token_validation", "FAILED_INVALID_TOKEN");
                monitor.end("assign org tags failed: invalid token");
                throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
            }

            if (request == null || request.getOrgTags() == null || request.getOrgTags().isEmpty()) {
                monitor.end("assign org tags failed: empty orgTags");
                throw new CustomException("orgTags must not be empty", HttpStatus.BAD_REQUEST);
            }

            userProfileService.assignOrgTagsToUser(userId, request.getOrgTags(), username);

            LogUtils.logUserOperation(username, "ADMIN_ASSIGN_ORG_TAGS", "user_org_tags", "SUCCESS");
            LogUtils.logBusiness("ADMIN_ASSIGN_ORG_TAGS", username,
                    "Assigned org tags to userId=%d, orgTags=%s", userId, request.getOrgTags());
            monitor.end("assign org tags success");

            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "Assign organization tags success",
                    "data", Map.of()));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_ASSIGN_ORG_TAGS", username,
                    "assign org tags failed: %s", e, e.getMessage());
            monitor.end("assign org tags failed: " + e.getMessage());
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_ASSIGN_ORG_TAGS", username,
                    "assign org tags error: %s", e, e.getMessage());
            monitor.end("assign org tags error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    public static class AssignOrgTagsRequest {
        private List<String> orgTags;

        public List<String> getOrgTags() {
            return orgTags;
        }

        public void setOrgTags(List<String> orgTags) {
            this.orgTags = orgTags;
        }
    }
}
