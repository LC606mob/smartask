package com.lcmob.smartask.repository;

import com.lcmob.smartask.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 查询 orgTags 字段包含指定 tagId 的用户
     * 匹配规则：tagId 在逗号分隔列表中（精确匹配，非子串）
     */
    @Query("SELECT u FROM User u WHERE u.orgTags LIKE CONCAT('%,', :tagId, ',%')" +
           " OR u.orgTags LIKE CONCAT(:tagId, ',%')" +
           " OR u.orgTags LIKE CONCAT('%,', :tagId)" +
           " OR u.orgTags = :tagId")
    java.util.List<User> findByOrgTagContaining(@Param("tagId") String tagId);

    /**
     * 查询 primaryOrg 为指定 tagId 的用户
     */
    java.util.List<User> findByPrimaryOrg(String primaryOrg);
}
