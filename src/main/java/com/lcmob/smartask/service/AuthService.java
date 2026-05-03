package com.lcmob.smartask.service;

import com.lcmob.smartask.exception.CustomException;
import com.lcmob.smartask.model.OrganizationTag;
import com.lcmob.smartask.model.User;
import com.lcmob.smartask.repository.OrganizationTagRepository;
import com.lcmob.smartask.repository.UserRepository;
import com.lcmob.smartask.utils.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 认证服务
 *
 * 【职责】处理用户注册、登录、身份验证
 * 【设计思路】
 *   - 注册：创建用户 + 自动分配私人组织标签
 *   - 登录：验证用户名密码，返回用户名（由JwtUtils生成token）
 *   - 组织标签：每个用户自动获得一个私人空间标签
 *
 * 【组织标签初始化】
 *   1. 确保默认组织标签（DEFAULT）存在
 *   2. 为新用户创建私人组织标签（PRIVATE_{username}）
 *   3. 将私人标签设为用户的主组织
 *
 * 【为什么需要私人组织标签】
 *   - 用户上传的文档默认关联到私人组织
 *   - 只有用户本人可以访问
 *   - 后续可以将文档分享到其他组织
 *
 * 【调用链】
 *   UserController → AuthService → UserRepository
 *                                → OrganizationTagRepository
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private static final String DEFAULT_ORG_TAG = "DEFAULT";
    private static final String DEFAULT_ORG_NAME = "默认组织";
    private static final String DEFAULT_ORG_DESCRIPTION = "系统默认组织标签，自动分配给所有新用户";
    private static final String PRIVATE_TAG_PREFIX = "PRIVATE_";
    private static final String PRIVATE_ORG_NAME_SUFFIX = "的私人空间";
    private static final String PRIVATE_ORG_DESCRIPTION = "用户的私人组织标签，仅用户本人可访问";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{2,31}$");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationTagRepository organizationTagRepository;

    @Autowired
    private OrgTagCacheService orgTagCacheService;

    /**
     * 注册新用户
     */
    @Transactional
    public void registerUser(String username, String password) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new CustomException(
                "Username must be 3-32 characters, start with a letter, and contain only letters, digits, hyphens, or underscores",
                HttpStatus.BAD_REQUEST);
        }

        if (userRepository.findByUsername(username).isPresent()) {
            throw new CustomException("Username already exists", HttpStatus.BAD_REQUEST);
        }

        ensureDefaultOrgTagExists();

        User user = new User();
        user.setUsername(username);
        user.setPassword(PasswordUtil.encode(password));
        user.setRole(User.Role.USER);

        userRepository.save(user);

        String privateTagId = PRIVATE_TAG_PREFIX + username;
        createPrivateOrgTag(privateTagId, username, user);

        user.setOrgTags(privateTagId);
        user.setPrimaryOrg(privateTagId);

        userRepository.save(user);

        orgTagCacheService.cacheUserOrgTags(username, List.of(privateTagId));
        orgTagCacheService.cacheUserPrimaryOrg(username, privateTagId);

        logger.info("User registered successfully with private organization tag: {}", username);
    }

    /**
     * 创建管理员用户
     */
    public void createAdminUser(String username, String password, String creatorUsername) {
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new CustomException("Creator not found", HttpStatus.NOT_FOUND));

        if (creator.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can create admin accounts", HttpStatus.FORBIDDEN);
        }

        if (userRepository.findByUsername(username).isPresent()) {
            throw new CustomException("Username already exists", HttpStatus.BAD_REQUEST);
        }

        User adminUser = new User();
        adminUser.setUsername(username);
        adminUser.setPassword(PasswordUtil.encode(password));
        adminUser.setRole(User.Role.ADMIN);
        userRepository.save(adminUser);
    }

    /**
     * 对用户进行认证
     */
    public String authenticateUser(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("Invalid username or password", HttpStatus.UNAUTHORIZED));

        if (!PasswordUtil.matches(password, user.getPassword())) {
            throw new CustomException("Invalid username or password", HttpStatus.UNAUTHORIZED);
        }

        return user.getUsername();
    }

    private void createPrivateOrgTag(String privateTagId, String username, User owner) {
        if (!organizationTagRepository.existsByTagId(privateTagId)) {
            logger.info("Creating private organization tag for user: {}", username);

            OrganizationTag privateTag = new OrganizationTag();
            privateTag.setTagId(privateTagId);
            privateTag.setName(username + PRIVATE_ORG_NAME_SUFFIX);
            privateTag.setDescription(PRIVATE_ORG_DESCRIPTION);
            privateTag.setCreatedBy(owner);

            organizationTagRepository.save(privateTag);
            logger.info("Private organization tag created successfully for user: {}", username);
        }
    }

    private void ensureDefaultOrgTagExists() {
        if (!organizationTagRepository.existsByTagId(DEFAULT_ORG_TAG)) {
            logger.info("Creating default organization tag");

            Optional<User> adminUser = userRepository.findAll().stream()
                    .filter(user -> User.Role.ADMIN.equals(user.getRole()))
                    .findFirst();

            User creator;
            if (adminUser.isPresent()) {
                creator = adminUser.get();
            } else {
                creator = createSystemAdminIfNotExists();
            }

            OrganizationTag defaultTag = new OrganizationTag();
            defaultTag.setTagId(DEFAULT_ORG_TAG);
            defaultTag.setName(DEFAULT_ORG_NAME);
            defaultTag.setDescription(DEFAULT_ORG_DESCRIPTION);
            defaultTag.setCreatedBy(creator);

            organizationTagRepository.save(defaultTag);
            logger.info("Default organization tag created successfully");
        }
    }

    private User createSystemAdminIfNotExists() {
        String systemAdminUsername = "system_admin";

        return userRepository.findByUsername(systemAdminUsername)
                .orElseGet(() -> {
                    logger.info("Creating system admin user");
                    User systemAdmin = new User();
                    systemAdmin.setUsername(systemAdminUsername);
                    String randomPassword = generateRandomPassword();
                    systemAdmin.setPassword(PasswordUtil.encode(randomPassword));
                    systemAdmin.setRole(User.Role.ADMIN);

                    logger.info("System admin created successfully");
                    return userRepository.save(systemAdmin);
                });
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            int index = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }
}
