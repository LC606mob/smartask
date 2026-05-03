package com.lcmob.smartask.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO配置
 *
 * 【职责】创建和配置MinIO客户端
 * 【设计思路】
 *   - MinIO用于存储上传的文件
 *   - 支持预签名URL生成（用于下载/预览）
 *
 * 【配置来源】
 *   - minio.endpoint：MinIO服务地址
 *   - minio.accessKey/secretKey：认证信息
 *   - minio.publicUrl：公共访问地址
 *
 * 【存储桶】
 *   - uploads：存储所有上传的文件
 */
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.accessKey}")
    private String accessKey;

    @Value("${minio.secretKey}")
    private String secretKey;

    @Value("${minio.publicUrl}")
    private String publicUrl;


    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public String minioPublicUrl() {
        return publicUrl;
    }
}
