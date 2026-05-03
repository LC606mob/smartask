package com.lcmob.smartask.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI配置属性
 *
 * 【职责】绑定application.yml中的AI相关配置
 * 【设计思路】
 *   - Prompt配置：AI行为规则、引用格式、无结果提示
 *   - Generation配置：温度、最大token数、top-p等生成参数
 *
 * 【配置示例】
 *   ai:
 *     prompt:
 *       rules: "你是智问知识助手..."
 *       ref-start: "<<REF>>"
 *       ref-end: "<<END>>"
 *       no-result-text: "（本轮无检索结果）"
 *     generation:
 *       temperature: 0.3
 *       max-tokens: 2000
 *       top-p: 0.9
 */
@Component
@ConfigurationProperties(prefix = "ai")
@Data
public class AiProperties {

    // Prompt 配置对象，包含提示词相关的配置项
    private Prompt prompt = new Prompt();
    // 生成参数配置对象，包含 AI 生成文本时的参数设置
    private Generation generation = new Generation();

    /**
     * Prompt 配置内部类
     * 使用 @Data 注解自动生成 getter、setter 等方法
     */
    @Data
    public static class Prompt {
        /** 规则文案 */
        private String rules;
        /** 引用开始分隔符 */
        private String refStart;
        /** 引用结束分隔符 */
        private String refEnd;
        /** 无检索结果时的占位文案 */
        private String noResultText;
    }

    @Data
    public static class Generation {
        /** 采样温度 */
        private Double temperature = 0.3;
        /** 最大输出 tokens */
        private Integer maxTokens = 2000;
        /** nucleus top-p */
        private Double topP = 0.9;
    }
} 