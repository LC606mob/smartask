package com.lcmob.smartask.service;

import com.lcmob.smartask.exception.CustomException;
import com.lcmob.smartask.model.OrganizationTag;
import com.lcmob.smartask.model.User;
import com.lcmob.smartask.repository.FileUploadRepository;
import com.lcmob.smartask.repository.OrganizationTagRepository;
import com.lcmob.smartask.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 组织标签服务
 *
 * 【职责】处理组织标签的CRUD操作和层级管理
 * 【设计思路】
 *   - 支持树形结构（通过parentTag实现父子关系）
 *   - 只有管理员可以创建/修改/删除标签
 *   - 删除时检查是否有子标签或关联用户
 *
 * 【层级关系的作用】
 *   - 支持组织架构（如：公司 → 部门 → 小组）
 *   - 用户可以访问父组织下的所有文档
 *   - 搜索时自动包含子组织的文档
 *
 * 【删除约束】
 *   - 不能删除默认组织标签（DEFAULT）
 *   - 不能删除有子标签的标签
 *   - 不能删除已分配给用户的标签
 *   - 不能删除有文档关联的标签
 *
 * 【调用链】
 *   AdminOrgTagController → OrgTagService → OrganizationTagRepository
 */
@Service
public class OrgTagService {

    private static final Logger logger = LoggerFactory.getLogger(OrgTagService.class);

    private static final String DEFAULT_ORG_TAG = "DEFAULT";

    @Autowired
    private OrganizationTagRepository organizationTagRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrgTagCacheService orgTagCacheService;

    @Autowired
    private FileUploadRepository fileUploadRepository;

    /**
     * 创建组织标签
     */
    @Transactional
    public OrganizationTag createOrganizationTag(String tagId, String name, String description,
                                                String parentTag, String creatorUsername) {
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new CustomException("Creator not found", HttpStatus.NOT_FOUND));

        if (creator.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can create organization tags", HttpStatus.FORBIDDEN);
        }

        if (organizationTagRepository.existsByTagId(tagId)) {
            throw new CustomException("Tag ID already exists", HttpStatus.BAD_REQUEST);
        }

        if (parentTag != null && !parentTag.isEmpty()) {
            organizationTagRepository.findByTagId(parentTag)
                    .orElseThrow(() -> new CustomException("Parent tag not found", HttpStatus.NOT_FOUND));
        }

        OrganizationTag tag = new OrganizationTag();
        tag.setTagId(tagId);
        tag.setName(name);
        tag.setDescription(description);
        tag.setParentTag(parentTag);
        tag.setCreatedBy(creator);

        OrganizationTag savedTag = organizationTagRepository.save(tag);

        orgTagCacheService.invalidateAllEffectiveTagsCache();

        return savedTag;
    }

    /**
     * 更新组织标签
     */
    @Transactional
    public OrganizationTag updateOrganizationTag(String tagId, String name, String description,
                                                String parentTag, String adminUsername) {
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.NOT_FOUND));

        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can update organization tags", HttpStatus.FORBIDDEN);
        }

        OrganizationTag tag = organizationTagRepository.findByTagId(tagId)
                .orElseThrow(() -> new CustomException("Organization tag not found", HttpStatus.NOT_FOUND));

        if (parentTag != null && !parentTag.isEmpty()) {
            if (tagId.equals(parentTag)) {
                throw new CustomException("A tag cannot be its own parent", HttpStatus.BAD_REQUEST);
            }

            organizationTagRepository.findByTagId(parentTag)
                    .orElseThrow(() -> new CustomException("Parent tag not found", HttpStatus.NOT_FOUND));

            if (wouldFormCycle(tagId, parentTag)) {
                throw new CustomException("Setting this parent would create a cycle in the tag hierarchy", HttpStatus.BAD_REQUEST);
            }
        }

        if (name != null && !name.isEmpty()) {
            tag.setName(name);
        }

        if (description != null) {
            tag.setDescription(description);
        }

        tag.setParentTag(parentTag);

        OrganizationTag updatedTag = organizationTagRepository.save(tag);

        orgTagCacheService.invalidateAllEffectiveTagsCache();

        return updatedTag;
    }

    /**
     * 删除组织标签
     */
    @Transactional
    public void deleteOrganizationTag(String tagId, String adminUsername) {
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.NOT_FOUND));

        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Only administrators can delete organization tags", HttpStatus.FORBIDDEN);
        }

        OrganizationTag tag = organizationTagRepository.findByTagId(tagId)
                .orElseThrow(() -> new CustomException("Organization tag not found", HttpStatus.NOT_FOUND));

        if (DEFAULT_ORG_TAG.equals(tagId)) {
            throw new CustomException("Cannot delete the default organization tag", HttpStatus.BAD_REQUEST);
        }

        List<OrganizationTag> children = organizationTagRepository.findByParentTag(tagId);
        if (!children.isEmpty()) {
            throw new CustomException("Cannot delete a tag with child tags", HttpStatus.BAD_REQUEST);
        }

        // 检查是否有用户关联了该标签（使用 SQL 查询替代全表扫描）
        List<User> usersWithTag = userRepository.findByOrgTagContaining(tagId);
        if (!usersWithTag.isEmpty()) {
            throw new CustomException("Cannot delete a tag that is assigned to users", HttpStatus.CONFLICT);
        }

        List<User> usersWithPrimaryOrg = userRepository.findByPrimaryOrg(tagId);
        if (!usersWithPrimaryOrg.isEmpty()) {
            throw new CustomException("Cannot delete a tag that is used as primary organization", HttpStatus.CONFLICT);
        }

        // 检查是否有文档关联了该标签
        long fileCount = fileUploadRepository.countByOrgTag(tagId);
        if (fileCount > 0) {
            throw new CustomException("Cannot delete a tag that is associated with " + fileCount + " documents", HttpStatus.CONFLICT);
        }

        organizationTagRepository.delete(tag);

        orgTagCacheService.invalidateAllEffectiveTagsCache();

        logger.info("Organization tag deleted successfully: {}", tagId);
    }

    /**
     * 获取组织标签树结构
     */
    public List<Map<String, Object>> getOrganizationTagTree() {
        List<OrganizationTag> rootTags = organizationTagRepository.findByParentTag(null);
        return buildTagTreeRecursive(rootTags);
    }

    private List<Map<String, Object>> buildTagTreeRecursive(List<OrganizationTag> tags) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (OrganizationTag tag : tags) {
            Map<String, Object> node = new HashMap<>();
            node.put("tagId", tag.getTagId());
            node.put("name", tag.getName());
            node.put("description", tag.getDescription());
            node.put("parentTag", tag.getParentTag());

            List<OrganizationTag> children = organizationTagRepository.findByParentTag(tag.getTagId());
            if (!children.isEmpty()) {
                node.put("children", buildTagTreeRecursive(children));
            }

            result.add(node);
        }

        return result;
    }

    private boolean wouldFormCycle(String tagId, String newParentId) {
        String currentParentId = newParentId;

        while (currentParentId != null && !currentParentId.isEmpty()) {
            if (tagId.equals(currentParentId)) {
                return true;
            }

            Optional<OrganizationTag> parentTag = organizationTagRepository.findByTagId(currentParentId);
            if (parentTag.isEmpty()) {
                break;
            }

            currentParentId = parentTag.get().getParentTag();
        }

        return false;
    }
}
