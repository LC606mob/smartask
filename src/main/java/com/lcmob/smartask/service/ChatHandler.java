package com.lcmob.smartask.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcmob.smartask.client.DeepSeekClient;
import com.lcmob.smartask.entity.SearchResult;
import com.lcmob.smartask.model.Conversation;
import com.lcmob.smartask.model.User;
import com.lcmob.smartask.repository.ConversationRepository;
import com.lcmob.smartask.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天处理服务
 *
 * 【职责】处理用户与AI的对话，包括消息处理、历史管理、流式响应
 * 【设计思路】
 *   采用RAG（检索增强生成）模式：
 *   1. 接收用户消息
 *   2. 从知识库检索相关文档（混合搜索）
 *   3. 将检索结果作为上下文，调用AI生成回答
 *   4. 流式返回AI响应，并保存对话历史
 *
 * 【核心流程】
 *   用户消息 → 获取/创建会话ID → 获取历史记录
 *            → 混合搜索知识库 → 构建上下文
 *            → 调用DeepSeek API（流式）
 *            → 实时推送响应到前端
 *            → 完成后保存对话历史（Redis + MySQL）
 *
 * 【存储策略】
 *   - Redis：存储当前会话的最近20条消息（7天过期）
 *   - MySQL：持久化所有对话记录（用于历史查询和审计）
 *
 * 【为什么用双写】
 *   - Redis：高性能，适合实时读取（聊天时需要加载历史）
 *   - MySQL：持久化，适合历史查询（管理员查看所有记录）
 *
 * 【调用链】
 *   WebSocket → ChatHandler → HybridSearchService（检索）
 *                           → DeepSeekClient（AI生成）
 *                           → Redis（缓存历史）
 *                           → MySQL（持久化历史）
 */
@Service
public class ChatHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatHandler.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int MAX_SNIPPET_LEN = 300;

    private final RedisTemplate<String, String> redisTemplate;
    private final HybridSearchService searchService;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    private final Map<String, StringBuilder> responseBuilders = new ConcurrentHashMap<>();
    private final Map<String, Boolean> stopFlags = new ConcurrentHashMap<>();

    public ChatHandler(RedisTemplate<String, String> redisTemplate,
            HybridSearchService searchService,
            DeepSeekClient deepSeekClient,
            ConversationRepository conversationRepository,
            UserRepository userRepository) {
        this.redisTemplate = redisTemplate;
        this.searchService = searchService;
        this.deepSeekClient = deepSeekClient;
        this.objectMapper = new ObjectMapper();
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
    }

    public void processMessage(String userId, String userMessage, WebSocketSession session) {
        String sessionId = session.getId();
        logger.info("Start processing chat message. userId={}, sessionId={}", userId, sessionId);

        try {
            String conversationId = getOrCreateConversationId(userId);
            responseBuilders.put(sessionId, new StringBuilder());

            List<Map<String, String>> history = getConversationHistory(conversationId);
            List<SearchResult> searchResults = searchService.searchWithPermission(userMessage, userId, 5);
            String context = buildContext(searchResults);

            deepSeekClient.streamResponse(userMessage, context, history,
                    chunk -> {
                        StringBuilder responseBuilder = responseBuilders.get(sessionId);
                        if (responseBuilder != null) {
                            responseBuilder.append(chunk);
                        }
                        sendResponseChunk(session, chunk);
                    },
                    error -> {
                        handleError(session, error);
                        sendCompletionNotification(session);
                        responseBuilders.remove(sessionId);
                    },
                    () -> {
                        StringBuilder responseBuilder = responseBuilders.remove(sessionId);
                        String completeResponse = responseBuilder != null ? responseBuilder.toString() : "";

                        logger.info("AI stream completed. userId={}, sessionId={}, answerLength={}",
                                userId, sessionId, completeResponse.length());
                        sendCompletionNotification(session);
                        updateConversationHistory(conversationId, userId, userMessage, completeResponse);
                    });
        } catch (Exception e) {
            logger.error("Failed to process chat message. userId={}, sessionId={}", userId, sessionId, e);
            handleError(session, e);
            responseBuilders.remove(sessionId);
        }
    }

    private String getOrCreateConversationId(String userId) {
        String key = "user:" + userId + ":current_conversation";
        String conversationId = redisTemplate.opsForValue().get(key);

        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(key, conversationId, Duration.ofDays(7));
            logger.info("Created conversation id. userId={}, conversationId={}", userId, conversationId);
        }

        return conversationId;
    }

    private List<Map<String, String>> getConversationHistory(String conversationId) {
        String key = "conversation:" + conversationId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {
            });
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse conversation history. conversationId={}", conversationId, e);
            return new ArrayList<>();
        }
    }

    private void updateConversationHistory(String conversationId, String userId, String userMessage, String response) {
        String key = "conversation:" + conversationId;
        List<Map<String, String>> history = getConversationHistory(conversationId);
        String currentTimestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);

        Map<String, String> userMsgMap = new HashMap<>();
        userMsgMap.put("role", "user");
        userMsgMap.put("content", userMessage);
        userMsgMap.put("timestamp", currentTimestamp);
        history.add(userMsgMap);

        Map<String, String> assistantMsgMap = new HashMap<>();
        assistantMsgMap.put("role", "assistant");
        assistantMsgMap.put("content", response);
        assistantMsgMap.put("timestamp", currentTimestamp);
        history.add(assistantMsgMap);

        if (history.size() > MAX_HISTORY_MESSAGES) {
            history = history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size());
        }

        try {
            String json = objectMapper.writeValueAsString(history);
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(7));
            saveConversationToDatabase(userId, userMessage, response, conversationId);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize conversation history. conversationId={}", conversationId, e);
        } catch (Exception e) {
            logger.error("Failed to save conversation history. conversationId={}", conversationId, e);
        }
    }

    private void saveConversationToDatabase(String userId, String userMessage, String response, String conversationId) {
        try {
            User user;
            if (userId.matches("\\d+")) {
                user = userRepository.findById(Long.parseLong(userId)).orElse(null);
            } else {
                user = userRepository.findByUsername(userId).orElse(null);
            }

            if (user == null) {
                logger.warn("User not found, skip database conversation save. userId={}, conversationId={}",
                        userId, conversationId);
                return;
            }

            Conversation conversation = new Conversation();
            conversation.setUser(user);
            conversation.setQuestion(userMessage);
            conversation.setAnswer(response);
            conversationRepository.save(conversation);
        } catch (Exception e) {
            logger.error("Failed to save conversation to database. userId={}, conversationId={}",
                    userId, conversationId, e);
        }
    }

    private String buildContext(List<SearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < searchResults.size(); i++) {
            SearchResult result = searchResults.get(i);
            String snippet = result.getTextContent();
            if (snippet != null && snippet.length() > MAX_SNIPPET_LEN) {
                snippet = snippet.substring(0, MAX_SNIPPET_LEN) + "...";
            }
            String fileLabel = result.getFileName() != null ? result.getFileName() : "unknown";
            context.append(String.format("[%d] (%s) %s%n", i + 1, fileLabel, snippet != null ? snippet : ""));
        }
        return context.toString();
    }

    private void sendResponseChunk(WebSocketSession session, String chunk) {
        try {
            if (Boolean.TRUE.equals(stopFlags.get(session.getId()))) {
                return;
            }

            Map<String, String> chunkResponse = Map.of("chunk", chunk);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(chunkResponse)));
        } catch (Exception e) {
            logger.error("Failed to send response chunk. sessionId={}", session.getId(), e);
        }
    }

    private void sendCompletionNotification(WebSocketSession session) {
        try {
            Map<String, Object> notification = Map.of(
                    "type", "completion",
                    "status", "finished",
                    "message", "response completed",
                    "timestamp", System.currentTimeMillis(),
                    "date", LocalDateTime.now().toString());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(notification)));
        } catch (Exception e) {
            logger.error("Failed to send completion notification. sessionId={}", session.getId(), e);
        }
    }

    private void handleError(WebSocketSession session, Throwable error) {
        logger.error("AI service error. sessionId={}", session.getId(), error);
        try {
            Map<String, String> errorResponse = Map.of("error", "AI service is temporarily unavailable");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
        } catch (Exception e) {
            logger.error("Failed to send error message. sessionId={}", session.getId(), e);
        }
    }

    public void stopResponse(String userId, WebSocketSession session) {
        String sessionId = session.getId();
        logger.info("Received stop request. userId={}, sessionId={}", userId, sessionId);
        stopFlags.put(sessionId, true);

        try {
            Map<String, Object> response = Map.of(
                    "type", "stop",
                    "message", "response stopped",
                    "timestamp", System.currentTimeMillis(),
                    "date", LocalDateTime.now().toString());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (Exception e) {
            logger.error("Failed to send stop confirmation. sessionId={}", sessionId, e);
        }

        new Thread(() -> {
            try {
                Thread.sleep(2000);
                stopFlags.remove(sessionId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public String supplyAsyncWithJoinExample(String input) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Async task interrupted", e);
            }
            return input.toUpperCase() + "_PROCESSED";
        });

        return future.join();
    }
}
