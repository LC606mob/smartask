package com.lcmob.smartask.repository;

import com.lcmob.smartask.model.DocumentVector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文档向量Repository
 *
 * 【职责】文档向量（文本分块）的数据库操作
 * 【设计思路】
 *   - 查询：按fileMd5获取文件的所有分块
 *   - 删除：删除文件时清理所有分块
 *
 * 【调用链】
 *   VectorizationService → DocumentVectorRepository（读取分块）
 *   DocumentService → DocumentVectorRepository（删除分块）
 */
public interface DocumentVectorRepository extends JpaRepository<DocumentVector, Long> {
    List<DocumentVector> findByFileMd5(String fileMd5); // 查询某文件的所有分块
    
    /**
     * 删除指定文件MD5的所有文档向量记录
     * 
     * @param fileMd5 文件MD5
     */
    @Transactional
    @Modifying
    @Query(value = "DELETE FROM document_vectors WHERE file_md5 = ?1", nativeQuery = true)
    void deleteByFileMd5(String fileMd5);
}
