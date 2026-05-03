package com.lcmob.smartask.controller;

import com.lcmob.smartask.config.KafkaConfig;
import com.lcmob.smartask.model.FileProcessingTask;
import com.lcmob.smartask.model.FileUpload;
import com.lcmob.smartask.repository.FileUploadRepository;
import com.lcmob.smartask.service.FileTypeValidationService;
import com.lcmob.smartask.service.UploadService;
import com.lcmob.smartask.service.UserProfileService;
import com.lcmob.smartask.service.ChatHandler;
import com.lcmob.smartask.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 文件上传控制器
 *
 * 【职责】处理文件分片上传、断点续传、文件合并等操作
 * 【设计思路】
 *   采用分片上传机制，支持大文件上传和断点续传：
 *   1. 前端将大文件切分为多个分片（默认5MB/片）
 *   2. 逐个上传分片到MinIO临时存储
 *   3. 所有分片上传完成后，调用合并接口
 *   4. 合并后发送Kafka消息，触发后续的解析和向量化流程
 *
 * 【核心流程】
 *   前端分片 → uploadChunk() → MinIO临时存储
 *   合并请求 → mergeFile() → MinIO永久存储 → Kafka消息 → 异步处理
 *
 * 【调用链】
 *   前端 → UploadController → UploadService → MinIO
 *                           → KafkaTemplate → FileProcessingConsumer
 */
@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    @Autowired
    private UploadService uploadService; // 处理分片上传和合并逻辑

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate; // 发送Kafka消息，触发异步处理

    @Autowired
    private KafkaConfig kafkaConfig; // Kafka主题配置

    @Autowired
    private UserProfileService userProfileService; // 获取用户组织信息，用于权限控制

    @Autowired
    private FileUploadRepository fileUploadRepository; // 文件上传记录持久化

    @Autowired
    private FileTypeValidationService fileTypeValidationService; // 验证文件类型是否支持

    @Autowired
    private ChatHandler chatHandler; // 聊天处理服务（仅用于测试CompletableFuture示例）

    /**
     * 构造函数，初始化必要的服务组件
     */
    public UploadController(UploadService uploadService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.uploadService = uploadService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 上传文件分片
     *
     * 【业务逻辑】
     *   1. 第一个分片（chunkIndex=0）时验证文件类型是否支持
     *   2. 如果未指定组织标签，自动使用用户的主组织标签
     *   3. 调用UploadService保存分片到MinIO
     *   4. 返回已上传分片列表和进度
     *
     * 【为什么在第一个分片验证】
     *   - 减少不必要的验证开销
     *   - 尽早失败，避免后续分片白白上传
     *
     * @param fileMd5 文件的MD5值，用于唯一标识文件（前端计算）
     * @param chunkIndex 分片索引，从0开始
     * @param totalSize 文件总大小（字节）
     * @param fileName 原始文件名
     * @param orgTag 组织标签，用于权限控制
     * @param isPublic 是否公开，公开文档所有用户都可搜索到
     * @param file 分片文件内容
     * @param userId 当前用户ID（从JWT自动提取）
     */
    @PostMapping("/chunk")
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @RequestParam("fileMd5") String fileMd5,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("totalSize") long totalSize,
            @RequestParam("fileName") String fileName,
            @RequestParam(value = "totalChunks", required = false) Integer totalChunks,
            @RequestParam(value = "orgTag", required = false) String orgTag,
            @RequestParam(value = "isPublic", required = false, defaultValue = "false") boolean isPublic,
            @RequestParam("file") MultipartFile file,
            @RequestAttribute("userId") String userId) throws IOException {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("UPLOAD_CHUNK");
        try {
            // 文件类型验证（仅在第一个分片时进行验证）
            if (chunkIndex == 0) {
                FileTypeValidationService.FileTypeValidationResult validationResult = 
                    fileTypeValidationService.validateFileType(fileName);
                
                LogUtils.logBusiness("UPLOAD_CHUNK", userId, "文件类型验证结果: fileName=%s, valid=%s, fileType=%s, message=%s", 
                        fileName, validationResult.isValid(), validationResult.getFileType(), validationResult.getMessage());
                
                if (!validationResult.isValid()) {
                    LogUtils.logBusinessError("UPLOAD_CHUNK", userId, "文件类型验证失败: fileName=%s, fileType=%s", 
                            new RuntimeException(validationResult.getMessage()), fileName, validationResult.getFileType());
                    monitor.end("文件类型验证失败: " + validationResult.getMessage());
                    
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("code", HttpStatus.BAD_REQUEST.value());
                    errorResponse.put("message", validationResult.getMessage());
                    errorResponse.put("fileType", validationResult.getFileType());
                    errorResponse.put("supportedTypes", fileTypeValidationService.getSupportedFileTypes());
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
                }
            }
            
            String fileType = getFileType(fileName);
            String contentType = file.getContentType();
            
            LogUtils.logBusiness("UPLOAD_CHUNK", userId, "接收到分片上传请求: fileMd5=%s, chunkIndex=%d, fileName=%s, fileType=%s, contentType=%s, fileSize=%d, totalSize=%d, orgTag=%s, isPublic=%s", 
                    fileMd5, chunkIndex, fileName, fileType, contentType, file.getSize(), totalSize, orgTag, isPublic);
        
        // 如果未指定组织标签，则获取用户的主组织标签
        if (orgTag == null || orgTag.isEmpty()) {
            try {
                    LogUtils.logBusiness("UPLOAD_CHUNK", userId, "组织标签未指定，尝试获取用户主组织标签: fileName=%s", fileName);
                String primaryOrg = userProfileService.getUserPrimaryOrg(userId);
                orgTag = primaryOrg;
                    LogUtils.logBusiness("UPLOAD_CHUNK", userId, "成功获取用户主组织标签: fileName=%s, orgTag=%s", fileName, orgTag);
            } catch (Exception e) {
                    LogUtils.logBusinessError("UPLOAD_CHUNK", userId, "获取用户主组织标签失败: fileName=%s", e, fileName);
                    monitor.end("获取主组织标签失败: " + e.getMessage());
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                errorResponse.put("message", "获取用户主组织标签失败: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        }
        
            LogUtils.logFileOperation(userId, "UPLOAD_CHUNK", fileName, fileMd5, "PROCESSING");
        
            uploadService.uploadChunk(fileMd5, chunkIndex, totalSize, fileName, file, orgTag, isPublic, userId);
            
            List<Integer> uploadedChunks = uploadService.getUploadedChunks(fileMd5, userId);
            int actualTotalChunks = uploadService.getTotalChunks(fileMd5, userId);
            double progress = calculateProgress(uploadedChunks, actualTotalChunks);
            
            LogUtils.logBusiness("UPLOAD_CHUNK", userId, "分片上传成功: fileMd5=%s, fileName=%s, fileType=%s, chunkIndex=%d, 进度=%.2f%%", 
                    fileMd5, fileName, fileType, chunkIndex, progress);
            monitor.end("分片上传成功");
            
            // 构建数据对象
            Map<String, Object> data = new HashMap<>();
            data.put("uploaded", uploadedChunks);
            data.put("progress", progress);
            
            // 构建统一响应格式
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "分片上传成功");
            response.put("data", data);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            String fileType = getFileType(fileName);
            LogUtils.logBusinessError("UPLOAD_CHUNK", userId, "分片上传失败: fileMd5=%s, fileName=%s, fileType=%s, chunkIndex=%d", e, fileMd5, fileName, fileType, chunkIndex);
            monitor.end("分片上传失败: " + e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            errorResponse.put("message", "分片上传失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 获取文件上传状态接口
     * 查询指定文件的已上传分片和上传进度
     *
     * @param fileMd5 文件的MD5值，用于唯一标识文件
     * @return 返回包含已上传分片和上传进度的响应
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getUploadStatus(@RequestParam("file_md5") String fileMd5, @RequestAttribute("userId") String userId) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_UPLOAD_STATUS");
        try {
            // 获取文件信息
            String fileName = "unknown";
            String fileType = "unknown";
            try {
                Optional<FileUpload> fileUpload = fileUploadRepository.findByFileMd5(fileMd5);
                if (fileUpload.isPresent()) {
                    fileName = fileUpload.get().getFileName();
                    fileType = getFileType(fileName);
                }
            } catch (Exception e) {
                // 获取文件信息失败不影响状态查询，继续处理
                LogUtils.logBusiness("GET_UPLOAD_STATUS", "system", "获取文件信息失败，使用默认值: fileMd5=%s, 错误=%s", fileMd5, e.getMessage());
            }
            
            LogUtils.logBusiness("GET_UPLOAD_STATUS", "system", "获取文件上传状态: fileMd5=%s, fileName=%s, fileType=%s", fileMd5, fileName, fileType);
            
            List<Integer> uploadedChunks = uploadService.getUploadedChunks(fileMd5, userId);
            int totalChunks = uploadService.getTotalChunks(fileMd5, userId);
            double progress = calculateProgress(uploadedChunks, totalChunks);
            
            LogUtils.logBusiness("GET_UPLOAD_STATUS", "system", "文件上传状态: fileMd5=%s, fileName=%s, fileType=%s, 已上传=%d/%d, 进度=%.2f%%", 
                    fileMd5, fileName, fileType, uploadedChunks.size(), totalChunks, progress);
            monitor.end("获取上传状态成功");
            
            // 构建数据对象
            Map<String, Object> data = new HashMap<>();
            data.put("uploaded", uploadedChunks);
            data.put("progress", progress);
            data.put("fileName", fileName);
            data.put("fileType", fileType);
            
            // 构建统一响应格式
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取上传状态成功");
            response.put("data", data);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_UPLOAD_STATUS", "system", "获取文件上传状态失败: fileMd5=%s", e, fileMd5);
            monitor.end("获取上传状态失败: " + e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            errorResponse.put("message", "获取上传状态失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 合并文件分片
     *
     * 【业务逻辑】
     *   1. 验证文件记录存在且属于当前用户
     *   2. 检查所有分片是否已上传完成
     *   3. 调用UploadService将分片合并为完整文件
     *   4. 发送Kafka消息，触发异步的解析和向量化流程
     *
     * 【为什么用Kafka】
     *   - 文件解析和向量化是耗时操作（可能需要几分钟）
     *   - 使用Kafka实现异步处理，避免前端长时间等待
     *   - 支持失败重试和死信队列
     *
     * 【事务说明】
     *   使用@Transactional确保Kafka消息发送和数据库操作的原子性
     */
    @Transactional
    @PostMapping("/merge")
    public ResponseEntity<Map<String, Object>> mergeFile(
            @RequestBody MergeRequest request,
            @RequestAttribute("userId") String userId) {
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("MERGE_FILE");
        try {
            String fileType = getFileType(request.fileName());
            LogUtils.logBusiness("MERGE_FILE", userId, "接收到合并文件请求: fileMd5=%s, fileName=%s, fileType=%s", 
                    request.fileMd5(), request.fileName(), fileType);
            
            // 检查文件完整性和权限
            LogUtils.logBusiness("MERGE_FILE", userId, "检查文件记录和权限: fileMd5=%s, fileName=%s", request.fileMd5(), request.fileName());
            FileUpload fileUpload = fileUploadRepository.findByFileMd5AndUserId(request.fileMd5(), userId)
                    .orElseThrow(() -> {
                        LogUtils.logUserOperation(userId, "MERGE_FILE", request.fileMd5(), "FAILED_FILE_NOT_FOUND");
                        return new RuntimeException("文件记录不存在");
                    });
                    
            // 确保用户有权限操作该文件
            if (!fileUpload.getUserId().equals(userId)) {
                LogUtils.logUserOperation(userId, "MERGE_FILE", request.fileMd5(), "FAILED_PERMISSION_DENIED");
                LogUtils.logBusiness("MERGE_FILE", userId, "权限验证失败: 尝试合并不属于自己的文件, fileMd5=%s, fileName=%s, 实际所有者=%s", 
                        request.fileMd5(), request.fileName(), fileUpload.getUserId());
                monitor.end("合并失败：权限不足");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("code", HttpStatus.FORBIDDEN.value());
                errorResponse.put("message", "没有权限操作此文件");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
            }
            
            LogUtils.logBusiness("MERGE_FILE", userId, "权限验证通过，开始合并文件: fileMd5=%s, fileName=%s, fileType=%s", request.fileMd5(), request.fileName(), fileType);
            
            // 检查分片是否全部上传完成
            List<Integer> uploadedChunks = uploadService.getUploadedChunks(request.fileMd5(), userId);
            int totalChunks = uploadService.getTotalChunks(request.fileMd5(), userId);
            LogUtils.logBusiness("MERGE_FILE", userId, "分片上传状态: fileMd5=%s, fileName=%s, 已上传=%d/%d", 
                    request.fileMd5(), request.fileName(), uploadedChunks.size(), totalChunks);
            
            if (uploadedChunks.size() < totalChunks) {
                LogUtils.logUserOperation(userId, "MERGE_FILE", request.fileMd5(), "FAILED_INCOMPLETE_CHUNKS");
                monitor.end("合并失败：分片未全部上传");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("code", HttpStatus.BAD_REQUEST.value());
                errorResponse.put("message", "文件分片未全部上传，无法合并");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }

            // 合并文件
            LogUtils.logBusiness("MERGE_FILE", userId, "开始合并文件分片: fileMd5=%s, fileName=%s, fileType=%s, 分片数量=%d", request.fileMd5(), request.fileName(), fileType, totalChunks);
            String objectUrl = uploadService.mergeChunks(request.fileMd5(), request.fileName(), userId);
            LogUtils.logFileOperation(userId, "MERGE", request.fileName(), request.fileMd5(), "SUCCESS");

            // 发送任务到 Kafka，包含完整的权限信息
            LogUtils.logBusiness("MERGE_FILE", userId, "创建文件处理任务: fileMd5=%s, fileName=%s, fileType=%s, orgTag=%s, isPublic=%s", 
                    request.fileMd5(), request.fileName(), fileType, fileUpload.getOrgTag(), fileUpload.isPublic());
            
            FileProcessingTask task = new FileProcessingTask(
                    request.fileMd5(),
                    objectUrl,
                    request.fileName(),
                    fileUpload.getUserId(),
                    fileUpload.getOrgTag(),
                    fileUpload.isPublic()
            );
            
            LogUtils.logBusiness("MERGE_FILE", userId, "发送文件处理任务到Kafka(事务): topic=%s, fileMd5=%s, fileName=%s", 
                    kafkaConfig.getFileProcessingTopic(), request.fileMd5(), request.fileName());
            kafkaTemplate.executeInTransaction(kt -> {
                kt.send(kafkaConfig.getFileProcessingTopic(), task);
                return true;
            });
            LogUtils.logBusiness("MERGE_FILE", userId, "文件处理任务已发送: fileMd5=%s, fileName=%s, fileType=%s", request.fileMd5(), request.fileName(), fileType);

            // 构建数据对象
            Map<String, Object> data = new HashMap<>();
            data.put("object_url", objectUrl);
            
            // 构建统一响应格式
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "文件合并成功，任务已发送到 Kafka");
            response.put("data", data);
            
            LogUtils.logUserOperation(userId, "MERGE_FILE", request.fileMd5(), "SUCCESS");
            monitor.end("文件合并成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            String fileType = getFileType(request.fileName());
            LogUtils.logBusinessError("MERGE_FILE", userId, "文件合并失败: fileMd5=%s, fileName=%s, fileType=%s", e, 
                    request.fileMd5(), request.fileName(), fileType);
            monitor.end("文件合并失败: " + e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            errorResponse.put("message", "文件合并失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 计算上传进度
     * 根据已上传的分片数量和总分片数量计算进度百分比
     *
     * @param uploadedChunks 已上传的分片列表
     * @param totalChunks 总分片数量
     * @return 返回上传进度的百分比
     */
    private double calculateProgress(List<Integer> uploadedChunks, int totalChunks) {
        if (totalChunks == 0) {
            LogUtils.logBusiness("CALCULATE_PROGRESS", "system", "计算上传进度时总分片数为0");
            return 0.0;
        }
        return (double) uploadedChunks.size() / totalChunks * 100;
    }

    /**
     * 合并请求的辅助类，包含文件的MD5值和文件名
     */
    public record MergeRequest(String fileMd5, String fileName) {}

    /**
     * 获取支持的文件类型列表接口
     * 返回系统支持的文件类型及其扩展名列表
     *
     * @return 返回支持的文件类型信息
     */
    @GetMapping("/supported-types")
    public ResponseEntity<Map<String, Object>> getSupportedFileTypes() {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_SUPPORTED_TYPES");
        try {
            LogUtils.logBusiness("GET_SUPPORTED_TYPES", "system", "获取支持的文件类型列表");
            
            Set<String> supportedTypes = fileTypeValidationService.getSupportedFileTypes();
            Set<String> supportedExtensions = fileTypeValidationService.getSupportedExtensions();
            
            // 构建数据对象
            Map<String, Object> data = new HashMap<>();
            data.put("supportedTypes", supportedTypes);
            data.put("supportedExtensions", supportedExtensions);
            data.put("description", "系统支持的文档类型文件，这些文件可以被解析并进行向量化处理");
            
            // 构建统一响应格式
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取支持的文件类型成功");
            response.put("data", data);
            
            LogUtils.logBusiness("GET_SUPPORTED_TYPES", "system", "成功返回支持的文件类型: 类型数量=%d, 扩展名数量=%d", 
                    supportedTypes.size(), supportedExtensions.size());
            monitor.end("获取支持的文件类型成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_SUPPORTED_TYPES", "system", "获取支持的文件类型失败", e);
            monitor.end("获取支持的文件类型失败: " + e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            errorResponse.put("message", "获取支持的文件类型失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 根据文件名获取文件类型
     * 根据文件扩展名返回对应的文件类型描述
     *
     * @param fileName 文件名
     * @return 文件类型描述
     */
    private String getFileType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unknown";
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return "unknown";
        }
        
        String extension = fileName.substring(lastDotIndex + 1).toLowerCase();
        
        // 根据文件扩展名返回文件类型
        switch (extension) {
            case "pdf":
                return "PDF文档";
            case "doc":
            case "docx":
                return "Word文档";
            case "xls":
            case "xlsx":
                return "Excel表格";
            case "ppt":
            case "pptx":
                return "PowerPoint演示文稿";
            case "txt":
                return "文本文件";
            case "md":
                return "Markdown文档";
            case "jpg":
            case "jpeg":
                return "JPEG图片";
            case "png":
                return "PNG图片";
            case "gif":
                return "GIF图片";
            case "bmp":
                return "BMP图片";
            case "svg":
                return "SVG图片";
            case "mp4":
                return "MP4视频";
            case "avi":
                return "AVI视频";
            case "mov":
                return "MOV视频";
            case "wmv":
                return "WMV视频";
            case "mp3":
                return "MP3音频";
            case "wav":
                return "WAV音频";
            case "flac":
                return "FLAC音频";
            case "zip":
                return "ZIP压缩包";
            case "rar":
                return "RAR压缩包";
            case "7z":
                return "7Z压缩包";
            case "tar":
                return "TAR压缩包";
            case "gz":
                return "GZ压缩包";
            case "json":
                return "JSON文件";
            case "xml":
                return "XML文件";
            case "csv":
                return "CSV文件";
            case "html":
            case "htm":
                return "HTML文件";
            case "css":
                return "CSS文件";
            case "js":
                return "JavaScript文件";
            case "java":
                return "Java源码";
            case "py":
                return "Python源码";
            case "cpp":
            case "c":
                return "C/C++源码";
            case "sql":
                return "SQL文件";
            default:
                return extension.toUpperCase() + "文件";
        }
    }
    
    /**
     * 测试CompletableFuture.supplyAsync()和join()的示例端点
     * @param input 输入参数
     * @return 处理结果
     */
    @GetMapping("/test-supply-async")
    public ResponseEntity<Map<String, Object>> testSupplyAsync(@RequestParam String input) {
        try {
            // 调用ChatHandler中的示例方法
            String result = chatHandler.supplyAsyncWithJoinExample(input);
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "CompletableFuture.supplyAsync()和join()测试成功");
            response.put("data", Map.of(
                "input", input,
                "result", result
            ));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            errorResponse.put("message", "测试失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}

