package com.lcmob.smartask.config;

import com.lcmob.smartask.model.User;
import com.lcmob.smartask.repository.UserRepository;
import com.lcmob.smartask.utils.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 管理员账号初始化器
 *
 * 【职责】应用启动时自动创建管理员账号（如果不存在）
 * 【设计思路】
 *   - 实现CommandLineRunner，应用启动时执行
 *   - 优先级最高（@Order(1)），确保在其他初始化器之前运行
 *   - 从配置文件读取管理员信息
 *
 * 【配置来源】
 *   - admin.username：管理员用户名（默认admin）
 *   - admin.password：管理员密码（默认admin123）
 *   - admin.primary-org：主组织标签
 *   - admin.org-tags：所属组织标签
 */
@Component
@Order(1)
public class AdminUserInitializer implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(AdminUserInitializer.class);

    @Autowired
    private UserRepository userRepository;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:admin123}")
    private String adminPassword;

    @Value("${admin.primary-org:default}")
    private String adminPrimaryOrg;

    @Value("${admin.org-tags:default,admin}")
    private String adminOrgTags;

    @Override
    public void run(String... args) throws Exception {
        logger.info("检查管理员账号是否存在: {}", adminUsername);
        Optional<User> existingAdmin = userRepository.findByUsername(adminUsername);

        if (existingAdmin.isPresent()) {
            logger.info("管理员账号 '{}' 已存在，跳过创建步骤", adminUsername);
            warnIfDefaultPassword();
            return;
        }

        try {
            logger.info("开始创建管理员账号: {}", adminUsername);
            User adminUser = new User();
            adminUser.setUsername(adminUsername);
            adminUser.setPassword(PasswordUtil.encode(adminPassword));
            adminUser.setRole(User.Role.ADMIN);
            adminUser.setPrimaryOrg(adminPrimaryOrg);
            adminUser.setOrgTags(adminOrgTags);

            userRepository.save(adminUser);
            logger.info("管理员账号 '{}' 创建成功", adminUsername);
        } catch (Exception e) {
            logger.error("创建管理员账号失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法创建管理员账号", e);
        }

        warnIfDefaultPassword();
    }

    private void warnIfDefaultPassword() {
        if ("admin123".equals(adminPassword)) {
            logger.warn("============================================================");
            logger.warn("  安全警告: 管理员正在使用默认密码 'admin123'");
            logger.warn("  请在 application.yml 中设置 admin.password 为强密码");
            logger.warn("============================================================");
        }
    }
} 