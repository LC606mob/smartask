package com.lcmob.smartask.service;

import com.lcmob.smartask.exception.CustomException;
import com.lcmob.smartask.model.Conversation;
import com.lcmob.smartask.model.User;
import com.lcmob.smartask.repository.ConversationRepository;
import com.lcmob.smartask.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史服务
 *
 * 【职责】管理用户的对话历史记录
 * 【设计思路】
 *   - 记录：每次AI回答完成后，将问答对保存到MySQL
 *   - 查询：支持按时间范围和用户筛选
 *   - 权限：普通用户只能查自己的，管理员可以查所有
 *
 * 【存储策略】
 *   - Redis：存储当前会话的最近20条消息（实时聊天用）
 *   - MySQL：持久化所有对话记录（历史查询用）
 *
 * 【调用链】
 *   ChatHandler → ConversationService（记录对话）
 *   AdminConversationController → ConversationService（查询历史）
 */
@Service
public class ConversationService {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 记录用户的对话历史。
     *
     * @param username 用户名
     * @param question 用户提问内容
     * @param answer 系统回答内容
     */
    public void recordConversation(String username, String question, String answer) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        Conversation conversation = new Conversation();
        conversation.setUser(user);
        conversation.setQuestion(question);
        conversation.setAnswer(answer);

        conversationRepository.save(conversation);
    }

    /**
     * 查询用户的对话历史。
     *
     * @param username 用户名
     * @param startDate 起始日期（可选）
     * @param endDate 结束日期（可选）
     * @return 符合条件的对话记录列表
     */
    public List<Conversation> getConversations(String username, LocalDateTime startDate, LocalDateTime endDate) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        // 检查用户角色，如果是管理员且username参数为"all"，则返回所有对话历史
        if (user.getRole() == User.Role.ADMIN && "all".equals(username)) {
            if (startDate != null && endDate != null) {
                return conversationRepository.findByTimestampBetween(startDate, endDate);
            } else {
                return conversationRepository.findAll();
            }
        } else {
            // 普通用户只能查看自己的对话历史
            if (startDate != null && endDate != null) {
                return conversationRepository.findByUserIdAndTimestampBetween(
                        user.getId(), startDate, endDate);
            } else {
                return conversationRepository.findByUserId(user.getId());
            }
        }
    }
    
    /**
     * 管理员查询所有用户的对话历史。
     *
     * @param adminUsername 管理员用户名
     * @param targetUsername 目标用户名（可选，如果提供则只查询该用户的对话历史）
     * @param startDate 起始日期（可选）
     * @param endDate 结束日期（可选）
     * @return 符合条件的对话记录列表
     */
    public List<Conversation> getAllConversations(String adminUsername, String targetUsername, 
                                                 LocalDateTime startDate, LocalDateTime endDate) {
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.NOT_FOUND));
        
        // 验证用户是否为管理员
        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Unauthorized access", HttpStatus.FORBIDDEN);
        }
        
        // 如果指定了目标用户，则只查询该用户的对话历史
        if (targetUsername != null && !targetUsername.isEmpty()) {
            User targetUser = userRepository.findByUsername(targetUsername)
                    .orElseThrow(() -> new CustomException("Target user not found", HttpStatus.NOT_FOUND));
            
            if (startDate != null && endDate != null) {
                return conversationRepository.findByUserIdAndTimestampBetween(
                        targetUser.getId(), startDate, endDate);
            } else {
                return conversationRepository.findByUserId(targetUser.getId());
            }
        } else {
            // 否则查询所有用户的对话历史
            if (startDate != null && endDate != null) {
                return conversationRepository.findByTimestampBetween(startDate, endDate);
            } else {
                return conversationRepository.findAll();
            }
        }
    }
}