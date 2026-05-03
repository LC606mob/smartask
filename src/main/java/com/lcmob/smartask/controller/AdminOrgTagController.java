package com.lcmob.smartask.controller;

import com.lcmob.smartask.exception.CustomException;
import com.lcmob.smartask.model.OrganizationTag;
import com.lcmob.smartask.model.User;
import com.lcmob.smartask.repository.OrganizationTagRepository;
import com.lcmob.smartask.repository.UserRepository;
import com.lcmob.smartask.service.OrgTagService;
import com.lcmob.smartask.utils.JwtUtils;
import com.lcmob.smartask.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员组织标签控制器
 *
 * 【职责】管理员对组织标签的CRUD操作
 * 【设计思路】
 *   - 组织标签支持树形结构（通过parentTag实现层级关系）
 *   - 用于多租户权限控制：用户只能访问自己组织的文档
 *
 * 【组织标签的作用】
 *   - 文档上传时关联组织标签
 *   - 搜索时按组织标签过滤结果
 *   - 支持父子标签继承（用户可访问子组织的文档）
 *
 * 【调用链】
 *   管理员前端 → AdminOrgTagController → OrgTagService → OrganizationTagRepository
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminOrgTagController {

    @Autowired
    private OrganizationTagRepository organizationTagRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrgTagService orgTagService;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 获取组织标签树结构
     */
    @GetMapping("/org-tags/tree")
    public ResponseEntity<?> getOrgTagsTree(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_ORG_TAGS_TREE");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "ADMIN_GET_ORG_TAGS_TREE", "token_validation",
                        "FAILED_INVALID_TOKEN");
                monitor.end("获取组织标签树失败：无效token");
                throw new CustomException("无效的token", HttpStatus.UNAUTHORIZED);
            }

            User admin = userRepository.findByUsername(username)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));

            if (admin.getRole() != User.Role.ADMIN) {
                LogUtils.logUserOperation(username, "ADMIN_GET_ORG_TAGS_TREE", "authorization", "FAILED_NOT_ADMIN");
                monitor.end("获取组织标签树失败：非管理员");
                throw new CustomException("权限不足", HttpStatus.FORBIDDEN);
            }

            List<Map<String, Object>> tagTree = orgTagService.getOrganizationTagTree();

            LogUtils.logUserOperation(username, "ADMIN_GET_ORG_TAGS_TREE", "org_tag_tree", "SUCCESS");
            LogUtils.logBusiness("ADMIN_GET_ORG_TAGS_TREE", username, "成功获取组织标签树");
            monitor.end("获取组织标签树成功");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取组织标签树成功");
            response.put("data", tagTree);
            return ResponseEntity.ok(response);
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_GET_ORG_TAGS_TREE", username, "获取组织标签树失败: %s", e, e.getMessage());
            monitor.end("获取组织标签树失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ORG_TAGS_TREE", username, "获取组织标签树异常: %s", e, e.getMessage());
            monitor.end("获取组织标签树异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    /**
     * 获取组织标签列表
     */
    @GetMapping("/org-tags")
    public ResponseEntity<?> getOrgTagsList(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_ORG_TAGS_LIST");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "ADMIN_GET_ORG_TAGS_LIST", "token_validation",
                        "FAILED_INVALID_TOKEN");
                monitor.end("获取组织标签列表失败：无效token");
                throw new CustomException("无效的token", HttpStatus.UNAUTHORIZED);
            }

            User admin = userRepository.findByUsername(username)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));

            if (admin.getRole() != User.Role.ADMIN) {
                LogUtils.logUserOperation(username, "ADMIN_GET_ORG_TAGS_LIST", "authorization", "FAILED_NOT_ADMIN");
                monitor.end("获取组织标签列表失败：非管理员");
                throw new CustomException("权限不足", HttpStatus.FORBIDDEN);
            }

            List<OrganizationTag> tags = organizationTagRepository.findAll();

            LogUtils.logUserOperation(username, "ADMIN_GET_ORG_TAGS_LIST", "org_tag_list", "SUCCESS");
            LogUtils.logBusiness("ADMIN_GET_ORG_TAGS_LIST", username, "成功获取组织标签列表，共 %d 条记录",
                    tags.size());
            monitor.end("获取组织标签列表成功");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取组织标签列表成功");
            response.put("data", tags);
            return ResponseEntity.ok(response);
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_GET_ORG_TAGS_LIST", username, "获取组织标签列表失败: %s", e, e.getMessage());
            monitor.end("获取组织标签列表失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ORG_TAGS_LIST", username, "获取组织标签列表异常: %s", e, e.getMessage());
            monitor.end("获取组织标签列表异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    /**
     * 创建组织标签
     */
    @PostMapping("/org-tags")
    public ResponseEntity<?> createOrgTag(
            @RequestHeader("Authorization") String token,
            @RequestBody OrgTagRequest request) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_CREATE_ORG_TAG");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "ADMIN_CREATE_ORG_TAG", "token_validation",
                        "FAILED_INVALID_TOKEN");
                monitor.end("创建组织标签失败：无效token");
                throw new CustomException("无效的token", HttpStatus.UNAUTHORIZED);
            }

            if (request.tagId() == null || request.tagId().isEmpty() ||
                    request.name() == null || request.name().isEmpty()) {
                LogUtils.logUserOperation(username, "ADMIN_CREATE_ORG_TAG", "validation", "FAILED_EMPTY_PARAMS");
                monitor.end("创建组织标签失败：参数为空");
                return ResponseEntity.badRequest()
                        .body(Map.of("code", 400, "message", "标签ID和名称不能为空"));
            }

            OrganizationTag tag = orgTagService.createOrganizationTag(
                    request.tagId(), request.name(), request.description(),
                    request.parentTag(), username);

            LogUtils.logUserOperation(username, "ADMIN_CREATE_ORG_TAG", request.tagId(), "SUCCESS");
            LogUtils.logBusiness("ADMIN_CREATE_ORG_TAG", username, "成功创建组织标签: %s", request.tagId());
            monitor.end("创建组织标签成功");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "创建组织标签成功");
            response.put("data", tag);
            return ResponseEntity.ok(response);
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ORG_TAG", username, "创建组织标签失败: %s", e, e.getMessage());
            monitor.end("创建组织标签失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ORG_TAG", username, "创建组织标签异常: %s", e, e.getMessage());
            monitor.end("创建组织标签异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    /**
     * 更新组织标签
     */
    @PutMapping("/org-tags/{tagId}")
    public ResponseEntity<?> updateOrgTag(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId,
            @RequestBody OrgTagRequest request) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_UPDATE_ORG_TAG");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "ADMIN_UPDATE_ORG_TAG", "token_validation",
                        "FAILED_INVALID_TOKEN");
                monitor.end("更新组织标签失败：无效token");
                throw new CustomException("无效的token", HttpStatus.UNAUTHORIZED);
            }

            OrganizationTag tag = orgTagService.updateOrganizationTag(
                    tagId, request.name(), request.description(),
                    request.parentTag(), username);

            LogUtils.logUserOperation(username, "ADMIN_UPDATE_ORG_TAG", tagId, "SUCCESS");
            LogUtils.logBusiness("ADMIN_UPDATE_ORG_TAG", username, "成功更新组织标签: %s", tagId);
            monitor.end("更新组织标签成功");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "更新组织标签成功");
            response.put("data", tag);
            return ResponseEntity.ok(response);
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_ORG_TAG", username, "更新组织标签失败: %s", e, e.getMessage());
            monitor.end("更新组织标签失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_ORG_TAG", username, "更新组织标签异常: %s", e, e.getMessage());
            monitor.end("更新组织标签异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    /**
     * 删除组织标签
     */
    @DeleteMapping("/org-tags/{tagId}")
    public ResponseEntity<?> deleteOrgTag(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_DELETE_ORG_TAG");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "ADMIN_DELETE_ORG_TAG", "token_validation",
                        "FAILED_INVALID_TOKEN");
                monitor.end("删除组织标签失败：无效token");
                throw new CustomException("无效的token", HttpStatus.UNAUTHORIZED);
            }

            orgTagService.deleteOrganizationTag(tagId, username);

            LogUtils.logUserOperation(username, "ADMIN_DELETE_ORG_TAG", tagId, "SUCCESS");
            LogUtils.logBusiness("ADMIN_DELETE_ORG_TAG", username, "成功删除组织标签: %s", tagId);
            monitor.end("删除组织标签成功");

            return ResponseEntity.ok(Map.of("code", 200, "message", "删除组织标签成功"));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_DELETE_ORG_TAG", username, "删除组织标签失败: %s", e, e.getMessage());
            monitor.end("删除组织标签失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_DELETE_ORG_TAG", username, "删除组织标签异常: %s", e, e.getMessage());
            monitor.end("删除组织标签异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    // 请求记录类
    record OrgTagRequest(String tagId, String name, String description, String parentTag) {}
}
