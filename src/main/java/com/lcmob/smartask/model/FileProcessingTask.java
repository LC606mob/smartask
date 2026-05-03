package com.lcmob.smartask.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件处理任务
 *
 * 【职责】作为Kafka消息体，传递文件处理任务信息
 * 【设计思路】
 *   - 文件合并完成后，发送此任务到Kafka
 *   - Kafka消费者接收任务，触发解析和向量化
 *
 * 【字段说明】
 *   - fileMd5：文件MD5（唯一标识）
 *   - filePath：MinIO文件路径
 *   - fileName：原始文件名
 *   - userId：上传用户ID
 *   - orgTag：所属组织标签
 *   - isPublic：是否公开
 *
 * 【消息流转】
 *   UploadController → Kafka → FileProcessingConsumer
 *                            → ParseService（解析）
 *                            → VectorizationService（向量化）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileProcessingTask {
    private String fileMd5; // 文件的 MD5 校验值
    private String filePath; // 文件存储路径
    private String fileName; // 文件名
    private String userId;   // 上传用户ID
    private String orgTag;   // 文件所属组织标签
    private boolean isPublic; // 文件是否公开
    
    /**
     * 向后兼容的构造函数
     */
    public FileProcessingTask(String fileMd5, String filePath, String fileName) {
        this.fileMd5 = fileMd5;
        this.filePath = filePath;
        this.fileName = fileName;
        this.userId = null;
        this.orgTag = "DEFAULT";
        this.isPublic = false;
    }
}
