package com.lcmob.smartask.repository;

import com.lcmob.smartask.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户Repository
 *
 * 【职责】用户的数据库操作
 * 【设计思路】
 *   - 继承JpaRepository，提供基本CRUD
 *   - 自定义findByUsername方法，用于登录验证
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
}
