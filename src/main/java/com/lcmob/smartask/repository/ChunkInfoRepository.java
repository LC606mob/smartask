package com.lcmob.smartask.repository;

import com.lcmob.smartask.model.ChunkInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 文件分块信息Repository
 *
 * 【职责】文件分块信息的数据库操作
 * 【设计思路】
 *   - 按fileMd5查询所有分块，按chunkIndex排序
 *   - 用于文件合并时读取分片顺序
 */
public interface ChunkInfoRepository extends JpaRepository<ChunkInfo, Long> {
    List<ChunkInfo> findByFileMd5OrderByChunkIndexAsc(String fileMd5);
}
