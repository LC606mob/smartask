package com.lcmob.smartask.repository;

import com.lcmob.smartask.model.OrganizationTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 组织标签Repository
 *
 * 【职责】组织标签的数据库操作
 * 【设计思路】
 *   - 支持按tagId查询
 *   - 支持查询子标签（按parentTag）
 *   - 支持检查tagId是否存在
 */
public interface OrganizationTagRepository extends JpaRepository<OrganizationTag, String> {
    Optional<OrganizationTag> findByTagId(String tagId);
    List<OrganizationTag> findByParentTag(String parentTag);
    boolean existsByTagId(String tagId);
} 