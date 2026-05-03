package com.lcmob.smartask.config;

import com.lcmob.smartask.handler.ChatWebSocketHandler;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * WebSocket配置
 *
 * 【职责】注册WebSocket端点和处理器
 * 【设计思路】
 *   - 端点：/chat/{token}（token用于身份验证）
 *   - 处理器：ChatWebSocketHandler
 *   - 允许所有来源访问（生产环境应限制）
 *
 * 【连接流程】
 *   前端 → ws://host/chat/{jwtToken} → ChatWebSocketHandler
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/chat/{token}")
                .setAllowedOrigins("*"); // 允许所有来源访问，生产环境应该限制
    }
}
