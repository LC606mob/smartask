package com.lcmob.smartask.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 文件分块信息实体
 *
 * 【对应表】chunk_info
 * 【职责】记录文件分片的元数据（用于断点续传和合并）
 *
 * 【字段说明】
 *   - id：自增主键
 *   - fileMd5：文件MD5（关联FileUpload）
 *   - chunkIndex：分块序号（从0开始）
 *   - chunkMd5：分块MD5（用于完整性校验）
 *   - storagePath：MinIO存储路径（如"chunks/abc123/0"）
 *
 * 【存储流程】
 *   1. 上传分片时，记录分片信息到此表
 *   2. 合并时，按chunkIndex顺序读取所有分片
 *   3. 合并完成后，清理分片文件和此表记录
 */
@Data
@Entity
@Table(name = "chunk_info")
public class ChunkInfo {
    /**
     * 分块信息的唯一标识符
     * 由数据库自动生成，用于唯一确定一个分块信息
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 文件的MD5值
     * 用于标识一个文件，同一个文件的MD5值相同，不同文件的MD5值不同
     */
    private String fileMd5;

    /**
     * 分块的索引号
     * 表示文件中的第几个分块，用于保持分块的顺序
     */
    private int chunkIndex;

    /**
     * 分块的MD5值
     * 每个分块的唯一标识，用于校验分块的完整性和正确性
     */
    private String chunkMd5;

    /**
     * 分块的存储路径
     * 表示分块在系统中的存储位置，可以是绝对路径或相对路径
     */
    private String storagePath;
}

