package com.lcmob.smartask.controller;

import com.lcmob.smartask.service.ParseService;
import com.lcmob.smartask.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档解析控制器
 *
 * 【职责】提供文档解析接口，将上传的文档解析为文本分块
 * 【设计思路】
 *   - 接收文件流，调用ParseService进行解析
 *   - 解析后的文本分块存储到MySQL，供后续向量化使用
 *
 * 【注意】
 *   此接口主要用于手动触发解析，正常流程是通过Kafka异步触发
 *
 * 【调用链】
 *   前端 → ParseController → ParseService → Apache Tika（文档解析）
 */
@RestController
@RequestMapping("/api/v1/parse")
public class ParseController {

    @Autowired
    private ParseService parseService;

    @PostMapping
    public ResponseEntity<String> parseDocument(@RequestParam("file") MultipartFile file,
                                                @RequestParam("file_md5") String fileMd5,
                                                @RequestAttribute(value = "userId", required = false) String userId) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("PARSE_DOCUMENT");
        try {
            LogUtils.logBusiness("PARSE_DOCUMENT", userId != null ? userId : "system", 
                    "开始解析文档: fileMd5=%s, fileName=%s, fileSize=%d", 
                    fileMd5, file.getOriginalFilename(), file.getSize());
            
            parseService.parseAndSave(fileMd5, file.getInputStream());
            
            LogUtils.logFileOperation(userId != null ? userId : "system", "PARSE", 
                    file.getOriginalFilename(), fileMd5, "SUCCESS");
            LogUtils.logUserOperation(userId != null ? userId : "system", "PARSE_DOCUMENT", 
                    fileMd5, "SUCCESS");
            monitor.end("文档解析成功");
            
            return ResponseEntity.ok("文档解析成功");
        } catch (Exception e) {
            LogUtils.logBusinessError("PARSE_DOCUMENT", userId != null ? userId : "system", 
                    "文档解析失败: fileMd5=%s, fileName=%s", e, fileMd5, file.getOriginalFilename());
            LogUtils.logFileOperation(userId != null ? userId : "system", "PARSE", 
                    file.getOriginalFilename(), fileMd5, "FAILED");
            monitor.end("文档解析失败: " + e.getMessage());
            
            return ResponseEntity.badRequest().body("文档解析失败：" + e.getMessage());
        }
    }
}