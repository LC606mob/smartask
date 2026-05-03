package com.lcmob.smartask.repository;

import com.lcmob.smartask.model.FileUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 文件上传记录Repository
 *
 * 【职责】文件上传记录的数据库操作
 * 【设计思路】
 *   - 继承JpaRepository，提供基本CRUD
 *   - 自定义查询方法，支持按MD5、用户、权限等条件查询
 *
 * 【关键查询】
 *   - findByFileMd5：按文件MD5查询（用于去重）
 *   - findAccessibleFilesWithTags：查询用户可访问的文件（权限过滤）
 *   - findByUserId：查询用户上传的文件
 */
@Repository
public interface FileUploadRepository extends JpaRepository<FileUpload, Long> {
    Optional<FileUpload> findByFileMd5(String fileMd5);

    Optional<FileUpload> findByFileMd5AndUserId(String fileMd5, String userId);

    Optional<FileUpload> findByFileNameAndIsPublicTrue(String fileName);

    long countByFileMd5(String fileMd5);

    void deleteByFileMd5(String fileMd5);

    void deleteByFileMd5AndUserId(String fileMd5, String userId);

    /**
     * 查询用户自己的文件和公开文件
     */
    List<FileUpload> findByUserIdOrIsPublicTrue(String userId);

    /**
     * 查询用户可访问的所有文件（考虑层级标签权限）
     * 包括：1. 用户自己上传的文件
     * 2. 公开的文件
     *
     * @param userId     用户ID
     * @param orgTagList 用户有效的组织标签列表（包含层级结构）
     * @return 用户可访问的文件列表
     */
    @Query("SELECT f FROM FileUpload f WHERE f.userId = :userId OR f.isPublic = true")
    List<FileUpload> findAccessibleFilesWithTags(@Param("userId") String userId,
            @Param("orgTagList") List<String> orgTagList);

    /**
     * 查询用户可访问的所有文件（原始方法，保留向后兼容性）
     * 
     * @param userId     用户ID
     * @param orgTagList 用户所属的组织标签列表（逗号分隔）
     * @return 用户可访问的文件列表
     */
    @Query("SELECT f FROM FileUpload f WHERE f.userId = :userId OR f.isPublic = true")
    List<FileUpload> findAccessibleFiles(@Param("userId") String userId, @Param("orgTagList") List<String> orgTagList);

    /**
     * 查询用户自己上传的所有文件
     * 
     * @param userId 用户ID
     * @return 用户上传的文件列表
     */
    List<FileUpload> findByUserId(String userId);

    List<FileUpload> findByFileMd5In(List<String> md5List);

    long countByOrgTag(String orgTag);
}
