package com.lcmob.smartask.model;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Blob;

/**
 * 文档向量实体
 *
 * 【对应表】document_vectors
 * 【职责】存储文档的文本分块（向量化前的中间存储）
 *
 * 【字段说明】
 *   - vectorId：自增主键
 *   - fileMd5：文件MD5（关联FileUpload）
 *   - chunkId：分块序号（从1开始）
 *   - textContent：文本内容（用于后续向量化）
 *   - modelVersion：向量模型版本
 *   - userId：上传用户ID
 *   - orgTag：所属组织标签
 *   - isPublic：是否公开
 *
 * 【存储流程】
 *   1. 文档解析后，文本分块存储到此表
 *   2. 向量化服务读取分块，生成向量
 *   3. 向量存储到Elasticsearch
 *
 * 【注意】此表是中间存储，向量化完成后数据仍保留（用于重建索引）
 */
@Data
@Entity
@Table(name = "document_vectors")
public class DocumentVector {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long vectorId;

    @Column(nullable = false, length = 32)
    private String fileMd5;

    @Column(nullable = false)
    private Integer chunkId;

    @Lob
    private String textContent;

    @Column(length = 32)
    private String modelVersion;
    
    /**
     * 上传用户ID
     */
    @Column(nullable = false, name = "user_id", length = 64)
    private String userId;
    
    /**
     * 文件所属组织标签
     */
    @Column(name = "org_tag", length = 50)
    private String orgTag;
    
    /**
     * 文件是否公开
     */
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;
}