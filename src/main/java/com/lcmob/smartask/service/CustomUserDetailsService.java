package com.lcmob.smartask.service;

import com.lcmob.smartask.model.User;
import com.lcmob.smartask.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security用户DetailsService
 *
 * 【职责】实现Spring Security的UserDetailsService接口，加载用户信息用于认证
 * 【设计思路】
 *   - 从数据库查询用户信息
 *   - 将用户角色转换为Spring Security的权限格式
 *   - 支持USER和ADMIN两种角色
 *
 * 【角色权限映射】
 *   - USER → ROLE_USER（普通用户）
 *   - ADMIN → ROLE_ADMIN（管理员）
 *
 * 【调用链】
 *   Spring Security → CustomUserDetailsService → UserRepository
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository; // 用于访问用户数据

    /**
     * 根据用户名加载用户详细信息。
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 从数据库中查找用户
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // 返回 Spring Security 所需的 UserDetails 对象
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                getAuthorities(user.getRole()) // 获取用户的角色权限
        );
    }

    /**
     * 将用户的角色转换为 Spring Security 的权限格式。
     */
    private Collection<? extends GrantedAuthority> getAuthorities(User.Role role) {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}