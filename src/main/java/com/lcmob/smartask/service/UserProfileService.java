package com.lcmob.smartask.service;

import com.lcmob.smartask.exception.CustomException;
import com.lcmob.smartask.model.OrganizationTag;
import com.lcmob.smartask.model.User;
import com.lcmob.smartask.repository.OrganizationTagRepository;
import com.lcmob.smartask.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户信息服务
 *
 * 【职责】处理用户信息查询、组织标签分配、主组织设置
 * 【设计思路】
 *   - 管理员可以为用户分配组织标签
 *   - 用户可以设置自己的主组织标签
 *   - 支持查询用户的组织标签信息
 *
 * 【组织标签的作用】
 *   - 用于文档权限控制
 *   - 支持多租户架构
 *   - 用户可以属于多个组织
 *
 * 【主组织标签】
 *   - 用户的默认组织，上传文档时自动关联
 *   - 用户可以切换主组织
 *   - 必须是用户已分配的组织标签
 *
 * 【调用链】
 *   AdminUserController → UserProfileService（用户管理）
 *   UserController → UserProfileService（个人信息）
 *   UploadController → UserProfileService（获取主组织）
 */
@Service
public class UserProfileService {

    private static final Logger logger = LoggerFactory.getLogger(UserProfileService.class);

    private static final String PRIVATE_TAG_PREFIX = "PRIVATE_";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationTagRepository organizationTagRepository;

    @Autowired
    private OrgTagCacheService orgTagCacheService;

    /**
     * 为用户分配组织标签
     */
    @Transactional
    public void assignOrgTagsToUser(Long userId, List<String> orgTags, String adminUsername) {
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.NOT_FOUND));

        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can assign organization tags", HttpStatus.FORBIDDEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        for (String tagId : orgTags) {
            if (!organizationTagRepository.existsByTagId(tagId)) {
                throw new CustomException("Organization tag " + tagId + " not found", HttpStatus.NOT_FOUND);
            }

            // 防止将其他用户的私有标签分配给目标用户
            if (tagId.startsWith(PRIVATE_TAG_PREFIX)) {
                String tagOwner = tagId.substring(PRIVATE_TAG_PREFIX.length());
                if (!tagOwner.equals(user.getUsername())) {
                    throw new CustomException("Cannot assign another user's private tag: " + tagId, HttpStatus.FORBIDDEN);
                }
            }
        }

        Set<String> existingTags = new HashSet<>();
        if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
            existingTags = new HashSet<>(Arrays.asList(user.getOrgTags().split(",")));
        }

        String privateTagId = PRIVATE_TAG_PREFIX + user.getUsername();
        boolean hasPrivateTag = existingTags.contains(privateTagId);

        Set<String> finalTags = new HashSet<>(orgTags);
        if (hasPrivateTag && !finalTags.contains(privateTagId)) {
            finalTags.add(privateTagId);
        }

        String orgTagsStr = String.join(",", finalTags);
        user.setOrgTags(orgTagsStr);

        if ((user.getPrimaryOrg() == null || user.getPrimaryOrg().isEmpty()) && !finalTags.isEmpty()) {
            if (hasPrivateTag) {
                user.setPrimaryOrg(privateTagId);
            } else {
                user.setPrimaryOrg(new ArrayList<>(finalTags).get(0));
            }
        }

        userRepository.save(user);

        // 先清除旧缓存，再写入新值，避免读到脏数据
        orgTagCacheService.deleteUserOrgTagsCache(user.getUsername());
        orgTagCacheService.deleteUserEffectiveTagsCache(user.getUsername());
        orgTagCacheService.cacheUserOrgTags(user.getUsername(), new ArrayList<>(finalTags));

        if (user.getPrimaryOrg() != null && !user.getPrimaryOrg().isEmpty()) {
            orgTagCacheService.cacheUserPrimaryOrg(user.getUsername(), user.getPrimaryOrg());
        }
    }

    /**
     * 获取用户的组织标签信息
     */
    public Map<String, Object> getUserOrgTags(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        List<String> orgTags = orgTagCacheService.getUserOrgTags(username);
        String primaryOrg = orgTagCacheService.getUserPrimaryOrg(username);

        if (orgTags == null || orgTags.isEmpty()) {
            orgTags = Arrays.asList(user.getOrgTags().split(","));
            orgTagCacheService.cacheUserOrgTags(username, orgTags);
        }

        if (primaryOrg == null || primaryOrg.isEmpty()) {
            primaryOrg = user.getPrimaryOrg();
            orgTagCacheService.cacheUserPrimaryOrg(username, primaryOrg);
        }

        List<Map<String, String>> orgTagDetails = new ArrayList<>();
        for (String tagId : orgTags) {
            OrganizationTag tag = organizationTagRepository.findByTagId(tagId).orElse(null);
            if (tag != null) {
                Map<String, String> tagInfo = new HashMap<>();
                tagInfo.put("tagId", tag.getTagId());
                tagInfo.put("name", tag.getName());
                tagInfo.put("description", tag.getDescription());
                orgTagDetails.add(tagInfo);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orgTags", orgTags);
        result.put("primaryOrg", primaryOrg);
        result.put("orgTagDetails", orgTagDetails);

        return result;
    }

    /**
     * 设置用户的主组织标签
     */
    public void setUserPrimaryOrg(String username, String primaryOrg) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        Set<String> userTags = new HashSet<>(Arrays.asList(user.getOrgTags().split(",")));
        if (!userTags.contains(primaryOrg)) {
            throw new CustomException("Organization tag not assigned to user", HttpStatus.BAD_REQUEST);
        }

        user.setPrimaryOrg(primaryOrg);
        userRepository.save(user);

        orgTagCacheService.cacheUserPrimaryOrg(username, primaryOrg);
    }

    /**
     * 获取用户的主组织标签
     */
    public String getUserPrimaryOrg(String userId) {
        User user;
        try {
            Long userIdLong = Long.parseLong(userId);
            user = userRepository.findById(userIdLong)
                .orElseThrow(() -> new CustomException("User not found with ID: " + userId, HttpStatus.NOT_FOUND));
        } catch (NumberFormatException e) {
            user = userRepository.findByUsername(userId)
                .orElseThrow(() -> new CustomException("User not found: " + userId, HttpStatus.NOT_FOUND));
        }

        String username = user.getUsername();

        String primaryOrg = orgTagCacheService.getUserPrimaryOrg(username);

        if (primaryOrg == null || primaryOrg.isEmpty()) {
            primaryOrg = user.getPrimaryOrg();

            if (primaryOrg == null || primaryOrg.isEmpty()) {
                String[] tags = user.getOrgTags().split(",");
                if (tags.length > 0) {
                    primaryOrg = tags[0];
                    user.setPrimaryOrg(primaryOrg);
                    userRepository.save(user);
                } else {
                    primaryOrg = "DEFAULT";
                }
            }

            orgTagCacheService.cacheUserPrimaryOrg(username, primaryOrg);
        }

        return primaryOrg;
    }

    /**
     * 获取用户列表，支持分页和过滤
     */
    public Map<String, Object> getUserList(String keyword, String orgTag, Integer status, int page, int size) {
        int pageIndex = page > 0 ? page - 1 : 0;
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by("createdAt").descending());

        Page<User> userPage;

        if (orgTag != null && !orgTag.isEmpty()) {
            List<User> allUsers = userRepository.findAll();
            List<User> filteredUsers = allUsers.stream()
                    .filter(user -> {
                        if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
                            Set<String> userTags = new HashSet<>(Arrays.asList(user.getOrgTags().split(",")));
                            if (!userTags.contains(orgTag)) {
                                return false;
                            }
                        } else {
                            return false;
                        }

                        if (keyword != null && !keyword.isEmpty()) {
                            boolean matchesKeyword = user.getUsername().contains(keyword);
                            if (!matchesKeyword) {
                                return false;
                            }
                        }

                        if (status != null) {
                            return user.getRole() == (status == 1 ? User.Role.USER : User.Role.ADMIN);
                        }

                        return true;
                    })
                    .collect(Collectors.toList());

            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), filteredUsers.size());

            List<User> pageContent = start < end ? filteredUsers.subList(start, end) : Collections.emptyList();
            userPage = new PageImpl<>(pageContent, pageable, filteredUsers.size());
        } else {
            userPage = userRepository.findAll(pageable);

            List<User> filteredUsers = userPage.getContent().stream()
                    .filter(user -> {
                        if (keyword != null && !keyword.isEmpty()) {
                            boolean matchesKeyword = user.getUsername().contains(keyword);
                            if (!matchesKeyword) {
                                return false;
                            }
                        }

                        if (status != null) {
                            return user.getRole() == (status == 1 ? User.Role.USER : User.Role.ADMIN);
                        }

                        return true;
                    })
                    .collect(Collectors.toList());

            userPage = new PageImpl<>(filteredUsers, pageable, filteredUsers.size());
        }

        List<Map<String, Object>> userList = userPage.getContent().stream()
                .map(user -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("userId", user.getId());
                    userMap.put("username", user.getUsername());

                    List<Map<String, String>> orgTagDetails = new ArrayList<>();
                    if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
                        Arrays.stream(user.getOrgTags().split(","))
                                .forEach(tagId -> {
                                    OrganizationTag tag = organizationTagRepository.findByTagId(tagId).orElse(null);
                                    if (tag != null) {
                                        Map<String, String> tagInfo = new HashMap<>();
                                        tagInfo.put("tagId", tag.getTagId());
                                        tagInfo.put("name", tag.getName());
                                        orgTagDetails.add(tagInfo);
                                    }
                                });
                    }

                    userMap.put("orgTags", orgTagDetails);
                    userMap.put("primaryOrg", user.getPrimaryOrg());
                    userMap.put("status", user.getRole() == User.Role.USER ? 1 : 0);
                    userMap.put("createdAt", user.getCreatedAt());

                    return userMap;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", userList);
        result.put("totalElements", userPage.getTotalElements());
        result.put("totalPages", userPage.getTotalPages());
        result.put("size", userPage.getSize());
        result.put("number", userPage.getNumber() + 1);

        return result;
    }
}
