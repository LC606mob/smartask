package com.lcmob.smartask.service;

import com.lcmob.smartask.client.EmbeddingClient;
import com.lcmob.smartask.model.DocumentVector;
import com.lcmob.smartask.entity.EsDocument;
import com.lcmob.smartask.entity.TextChunk;
import com.lcmob.smartask.repository.DocumentVectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * 向量化服务
 *
 * 【职责】将文档的文本分块转换为向量，并存储到Elasticsearch
 * 【设计思路】
 *   1. 从MySQL获取文档的文本分块
 *   2. 调用EmbeddingClient批量生成向量
 *   3. 将向量和文本一起存储到Elasticsearch
 *
 * 【为什么需要向量化】
 *   - 向量可以捕捉文本的语义信息
 *   - 支持语义搜索（即使关键词不同，也能找到相关内容）
 *   - 与BM25文本搜索结合，实现混合搜索
 *
 * 【存储结构】
 *   Elasticsearch文档包含：
 *   - textContent：原始文本（用于BM25搜索）
 *   - vector：向量表示（用于KNN搜索）
 *   - fileMd5、userId、orgTag、isPublic：元数据（用于权限过滤）
 *
 * 【调用链】
 *   Kafka消费者 → VectorizationService → EmbeddingClient（生成向量）
 *                                      → ElasticsearchService（存储）
 */
@Service
public class VectorizationService {

    private static final Logger logger = LoggerFactory.getLogger(VectorizationService.class);

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private DocumentVectorRepository documentVectorRepository;

    /**
     * 执行向量化操作
     * @param fileMd5 文件指纹
     * @param userId 上传用户ID
     * @param orgTag 组织标签
     * @param isPublic 是否公开
     */
    public void vectorize(String fileMd5, String userId, String orgTag, boolean isPublic) {
        try {
            logger.info("开始向量化文件，fileMd5: {}, userId: {}, orgTag: {}, isPublic: {}", 
                       fileMd5, userId, orgTag, isPublic);
                       
            // 获取文件分块内容
            List<TextChunk> chunks = fetchTextChunks(fileMd5);
            if (chunks == null || chunks.isEmpty()) {
                logger.warn("未找到分块内容，fileMd5: {}", fileMd5);
                return;
            }

            // 提取文本内容
            List<String> texts = chunks.stream()
                    .map(TextChunk::getContent)
                    .toList();

            // 调用外部模型生成向量
            List<float[]> vectors = embeddingClient.embed(texts);

            // 构建 Elasticsearch 文档并存储
            List<EsDocument> esDocuments = IntStream.range(0, chunks.size())
                    .mapToObj(i -> new EsDocument(
                            UUID.randomUUID().toString(),
                            fileMd5,
                            chunks.get(i).getChunkId(),
                            chunks.get(i).getContent(),
                            vectors.get(i),
                            "deepseek-embed", // 更新为 DeepSeek 的模型版本
                            userId,
                            orgTag,
                            isPublic
                    ))
                    .toList();

            elasticsearchService.bulkIndex(esDocuments); // 批量存储到 Elasticsearch

            logger.info("向量化完成，fileMd5: {}", fileMd5);
        } catch (Exception e) {
            logger.error("向量化失败，fileMd5: {}", fileMd5, e);
            throw new RuntimeException("向量化失败", e);
        }
    }
    

    /**
     * 获取文件分块内容
     * @param fileMd5 文件指纹
     * @return 分块内容列表
     */
    // 从数据库获取分块内容
    private List<TextChunk> fetchTextChunks(String fileMd5) {
        // 调用 Repository 查询数据
        List<DocumentVector> vectors = documentVectorRepository.findByFileMd5(fileMd5);

        // 转换为 TextChunk 列表
        return vectors.stream()
                .map(vector -> new TextChunk(
                        vector.getChunkId(),
                        vector.getTextContent()
                ))
                .toList();
    }
}