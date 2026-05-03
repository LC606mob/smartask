package com.lcmob.smartask.config;

import com.lcmob.smartask.service.CustomUserDetailsService;
import com.lcmob.smartask.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器
 *
 * 【职责】从请求头提取JWT Token，验证用户身份，设置SecurityContext
 * 【设计思路】
 *   - 每次请求都会执行（OncePerRequestFilter）
 *   - 支持Token自动刷新（无感知续期）
 *   - Token过期后有宽限期（可刷新）
 *
 * 【Token刷新策略】
 *   1. Token有效且即将过期：主动刷新，通过响应头返回新Token
 *   2. Token已过期但在宽限期内：刷新Token，通过响应头返回新Token
 *   3. Token过期且超过宽限期：拒绝请求
 *
 * 【调用链】
 *   请求 → JwtAuthenticationFilter → SecurityContext → Controller
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils; // 用于生成和解析 JWT Token

    @Autowired
    private CustomUserDetailsService userDetailsService; // 加载用户详细信息

    /**
     * 每次请求都会调用此方法，用于解析 JWT Token 并设置用户认证信息。
     * 实现无感知的token自动刷新机制。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // 从请求头中提取 JWT Token
            String token = extractToken(request);
            if (token != null) {
                String newToken = null;
                String username = null;
                
                // 首先检查token是否有效
                if (jwtUtils.validateToken(token)) {
                    // Token有效，检查是否需要预刷新
                    if (jwtUtils.shouldRefreshToken(token)) {
                        newToken = jwtUtils.refreshToken(token);
                        if (newToken != null) {
                            logger.info("Token auto-refreshed proactively");
                        }
                    }
                    username = jwtUtils.extractUsernameFromToken(token);
                } else {
                    // Token无效/过期，检查宽限内可以刷新
                    if (jwtUtils.canRefreshExpiredToken(token)) {
                        newToken = jwtUtils.refreshToken(token);
                        if (newToken != null) {
                            logger.info("Expired token refreshed within grace period");
                            username = jwtUtils.extractUsernameFromToken(newToken);
                        }
                    }
                }
                
                // 如果有新token，通过响应头返回给前端
                if (newToken != null) {
                    response.setHeader("New-Token", newToken);
                }
                
                // 设置用户认证信息
                if (username != null && !username.isEmpty()) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
            filterChain.doFilter(request, response); // 继续执行过滤链
        } catch (Exception e) {
            // 记录错误日志
            logger.error("Cannot set user authentication: {}", e);
        }
    }

    /**
     * 从请求头中提取 JWT Token。
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // 去掉 "Bearer " 前缀
        }
        return null;
    }
}
