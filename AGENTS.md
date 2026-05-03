# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## 项目概述

SmartAsk（派聪明）是一个企业级 AI 知识库管理系统，采用 RAG（检索增强生成）技术。核心流程：文档上传 → 解析分块 → 向量化 → 混合检索 → AI 生成回答。

## 常用开发命令

### 后端（Spring Boot）
```bash
mvn spring-boot:run                              # 启动应用
mvn spring-boot:run -Dspring-boot.run.profiles=dev  # 指定 profile
mvn clean package                                # 构建
mvn test                                         # 运行所有测试
mvn test -Dtest=UserServiceTest                  # 运行单个测试类
```

### 前端（Vue 3 + TypeScript）
```bash
cd frontend
pnpm install                                     # 安装依赖
pnpm dev                                         # 开发服务器（端口 9527）
pnpm build                                       # 生产构建
pnpm typecheck                                   # 类型检查
pnpm lint                                        # ESLint 修复
```

### Docker 环境
```bash
cd docs && docker-compose up -d                  # 启动 MySQL/Redis/ES/Kafka/MinIO
```

## 架构要点

### 数据库架构

数据库使用 MySQL 8.0，主要表结构：

- **users**：用户表，包含角色（USER/ADMIN）和组织标签
- **organization_tags**：组织标签表，支持层级关系（parent_tag）
- **file_upload**：文件上传记录表，包含 MD5、状态、权限控制
- **chunk_info**：文件分块信息表，记录分块索引和存储路径
- **document_vectors**：文档向量存储表，关联文件和用户信息

详细 DDL 请参考 `docs/databases/ddl.sql`

### RAG 管道（核心流程）

1. **文档上传**：前端分片上传 → `UploadController` → MinIO 存储
2. **文件处理**：Kafka 异步消费 → `FileProcessingConsumer` → `ParseService` 流式解析
3. **分块策略**：采用"父文档-子切片"策略，父块 1MB，子块 512 字符，使用 HanLP 中文分词
4. **向量化**：`EmbeddingClient` 调用 DashScope text-embedding-v4（维度 2048，批次大小 10）
5. **索引存储**：`ElasticsearchService` 批量写入 `knowledge_base` 索引
6. **混合检索**：`HybridSearchService` 结合 KNN 向量召回 + BM25 文本匹配 + 权限过滤
7. **AI 生成**：`DeepSeekClient` 流式调用 DashScope qwen-plus，通过 WebSocket 推送前端

### 关键技术决策

- **AI 服务**：统一使用阿里云 DashScope 兼容模式（`dashscope.aliyuncs.com/compatible-mode/v1`），LLM 用 qwen-plus，Embedding 用 text-embedding-v4
- **混合搜索**：KNN 召回窗口 = topK × 30，BM25 rescore 权重 1.0，KNN 权重 0.2
- **WebSocket 通信**：`ChatWebSocketHandler` 处理连接，JWT 从 URL 路径提取用户身份
  - 连接地址：`ws://localhost:8081/chat/{jwtToken}`
  - 前端使用 `@vueuse/core` 的 `useWebSocket`，自动重连（3 秒间隔，最多 5 次）
- **对话历史**：Redis 缓存当前会话（7 天过期），同时持久化到 MySQL，最多保留 20 条消息
- **文件存储**：MinIO 对象路径格式 `merged/{fileName}`，预签名 URL 有效期 1 小时

### 多租户权限模型

- **组织标签（OrgTag）**：支持层级关系，`OrgTagCacheService` 缓存有效标签
- **文档权限**：`FileUpload` 实体有 `userId`、`orgTag`、`isPublic` 字段
- **查询过滤**：用户可访问 = 自己的文档 OR 公开文档 OR 所属组织的文档
- **Spring Security 过滤链**：`JwtAuthenticationFilter` → `OrgTagAuthorizationFilter`

### 前端架构

- **框架**：Vue 3 Composition API + TypeScript + Naive UI
- **状态管理**：Pinia，核心 store 包括 `auth`、`chat`、`knowledge-base`
- **WebSocket**：使用 `@vueuse/core` 的 `useWebSocket`，自动重连（3 秒间隔，最多 5 次）
- **文件上传**：MD5 去重 + 分片上传 + 断点续传，通过 `knowledge-base` store 管理
- **路由**：`@elegant-router/vue` 自动生成，静态路由模式，首页为 `/chat`
- **Monorepo 结构**：使用 pnpm workspace，包含以下 packages：
  - `@sa/axios`：HTTP 客户端封装
  - `@sa/hooks`：通用 Vue hooks
  - `@sa/materials`：UI 组件库
  - `@sa/color`：颜色处理工具
  - `@sa/scripts`：构建和开发脚本

### API 路径约定

- 后端 API 前缀：`/api/v1`
- 用户认证：`/api/v1/users/login`、`/api/v1/users/register`
- 文档管理：`/api/v1/documents/**`
- 文件上传：`/api/v1/upload/**`
- WebSocket：`/chat/{jwtToken}`
- 搜索接口：`/api/search/**`

### 环境配置

- **前端开发环境**：`frontend/.env.test`，API 基础地址 `http://localhost:8081/api/v1`
- **前端生产环境**：`frontend/.env.prod`
- **后端配置文件**：
  - `src/main/resources/application.yml`：主配置
  - `src/main/resources/application-dev.yml`：开发环境配置
  - `src/main/resources/application-docker.yml`：Docker 环境配置

### 配置文件位置

- 后端主配置：`src/main/resources/application.yml`
- 前端环境变量：`frontend/.env`（开发）、`frontend/.env.prod`（生产）
- AI 提示词规则：`application.yml` 中 `ai.prompt.rules` 配置段
- ES 索引映射：`src/main/resources/es-mappings/`

## 关键依赖版本

- Spring Boot 3.4.2 / Java 17
- Elasticsearch 8.10.0（带 IK 中文分词插件）
- Kafka 3.2.1（主题：`file-processing`、`vectorization`）
- MinIO 8.5.12
- Vue 3.5.13 / Vite 6.3.5 / Pinia 3.0.2
