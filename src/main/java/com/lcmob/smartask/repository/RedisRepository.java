package com.lcmob.smartask.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcmob.smartask.entity.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis Repository
 *
 * 【职责】封装Redis操作，管理对话历史
 * 【设计思路】
 *   - 存储当前会话的对话历史（最近20条消息）
 *   - 支持获取当前会话ID和历史记录
 *
 * 【缓存结构】
 *   - user:{userId}:current_conversation：当前会话ID
 *   - conversation:{conversationId}：对话历史（JSON数组）
 *
 * 【过期策略】
 *   - 会话ID：7天过期
 *   - 对话历史：7天过期
 */
@Repository
public class RedisRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRepository(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public String getCurrentConversationId(String userId) {
        return (String) redisTemplate.opsForValue().get("user:" + userId + ":current_conversation");
    }

    public List<Message> getConversationHistory(String conversationId) {
        String json = (String) redisTemplate.opsForValue().get("conversation:" + conversationId);
        try {
            return json == null ? new ArrayList<>() : objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, Message.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse conversation history", e);
        }
    }

    public void saveConversationHistory(String conversationId, List<Message> messages) throws JsonProcessingException {
        redisTemplate.opsForValue().set("conversation:" + conversationId, objectMapper.writeValueAsString(messages), Duration.ofDays(7));
    }
}
