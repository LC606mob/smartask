package com.lcmob.smartask.controller;

import com.lcmob.smartask.service.HybridSearchService;
import com.lcmob.smartask.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.lcmob.smartask.entity.SearchResult;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * 搜索控制器
 *
 * 【职责】提供知识库混合检索接口
 * 【设计思路】
 *   采用混合搜索策略，结合向量搜索和文本搜索：
 *   - 向量搜索（KNN）：捕捉语义相似性，即使关键词不同也能找到相关内容
 *   - 文本搜索（BM25）：精确匹配关键词，确保字面相关性
 *   - 两者结合，兼顾语义理解和精确匹配
 *
 * 【权限控制】
 *   - 已登录用户：搜索自己的文档 + 公开文档 + 组织内文档
 *   - 未登录用户：只能搜索公开文档
 *
 * 【调用链】
 *   前端 → SearchController → HybridSearchService → EmbeddingClient（向量化）
 *                                                      → Elasticsearch（搜索）
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    @Autowired
    private HybridSearchService hybridSearchService;

    /**
     * 混合检索接口
     * 
     * URL: /api/v1/search/hybrid
     * Method: GET
     * Parameters:
     *   - query: 搜索查询字符串（必需）
     *   - topK: 返回结果数量（可选，默认10）
     * 
     * 示例: /api/v1/search/hybrid?query=人工智能的发展&topK=10
     * 
     * Response:
     * [
     *   {
     *     "fileMd5": "abc123...",
     *     "chunkId": 1,
     *     "textContent": "人工智能是未来科技发展的核心方向。",
     *     "score": 0.92,
     *     "userId": "user123",
     *     "orgTag": "TECH_DEPT",
     *     "isPublic": true
     *   }
     * ]
     */
    @GetMapping("/hybrid")
    public Map<String, Object> hybridSearch(@RequestParam String query,
                                            @RequestParam(defaultValue = "10") int topK,
                                            @RequestAttribute(value = "userId", required = false) String userId) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("HYBRID_SEARCH");
        try {
            LogUtils.logBusiness("HYBRID_SEARCH", userId != null ? userId : "anonymous", 
                    "开始混合检索: query=%s, topK=%d", query, topK);
            
            List<SearchResult> results;
            if (userId != null) {
                // 如果有用户ID，使用带权限的搜索
                results = hybridSearchService.searchWithPermission(query, userId, topK);
            } else {
                // 如果没有用户ID，使用普通搜索（仅公开内容）
                results = hybridSearchService.search(query, topK);
            }
            
            LogUtils.logUserOperation(userId != null ? userId : "anonymous", "HYBRID_SEARCH", 
                    "search_query", "SUCCESS");
            LogUtils.logBusiness("HYBRID_SEARCH", userId != null ? userId : "anonymous", 
                    "混合检索完成: 返回结果数量=%d", results.size());
            monitor.end("混合检索成功");
            
            // 构造统一响应结构
            Map<String, Object> responseBody = new HashMap<>(4);
            responseBody.put("code", 200);
            responseBody.put("message", "success");
            responseBody.put("data", results);
            
            return responseBody;
        } catch (Exception e) {
            LogUtils.logBusinessError("HYBRID_SEARCH", userId != null ? userId : "anonymous", 
                    "混合检索失败: query=%s", e, query);
            monitor.end("混合检索失败: " + e.getMessage());
            
            // 构造错误响应结构，保持与前端解析一致
            Map<String, Object> errorBody = new HashMap<>(4);
            errorBody.put("code", 500);
            errorBody.put("message", e.getMessage());
            errorBody.put("data", Collections.emptyList());
            return errorBody;
        }
    }
}
