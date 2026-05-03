package com.lcmob.smartask.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置
 *
 * 【职责】配置RedisTemplate的序列化方式
 * 【设计思路】
 *   - Key：String序列化（可读性好）
 *   - Value：JSON序列化（支持复杂对象）
 *
 * 【使用场景】
 *   - 对话历史缓存
 *   - Token状态管理
 *   - 文件上传状态记录（Bitmap）
 *   - 组织标签缓存
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
