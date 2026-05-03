package com.lcmob.smartask.controller;

import com.lcmob.smartask.model.User;
import com.lcmob.smartask.repository.UserRepository;
import com.lcmob.smartask.service.UserProfileService;
import com.lcmob.smartask.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerTest {

    private MockMvc mockMvc;
    private UserRepository userRepository;
    private UserProfileService userProfileService;
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userProfileService = mock(UserProfileService.class);
        jwtUtils = mock(JwtUtils.class);

        AdminUserController controller = new AdminUserController();
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "userProfileService", userProfileService);
        ReflectionTestUtils.setField(controller, "jwtUtils", jwtUtils);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void usersListAliasReturnsAdminUserList() throws Exception {
        User admin = new User();
        admin.setUsername("admin");
        admin.setRole(User.Role.ADMIN);

        when(jwtUtils.extractUsernameFromToken("token")).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userProfileService.getUserList(any(), eq("default"), any(), eq(1), eq(999)))
                .thenReturn(Map.of(
                        "content", List.of(),
                        "totalElements", 0L,
                        "totalPages", 0,
                        "size", 999,
                        "number", 1));

        mockMvc.perform(get("/api/v1/admin/users/list")
                        .header("Authorization", "Bearer token")
                        .param("page", "1")
                        .param("size", "999")
                        .param("orgTag", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void assignOrgTagsUpdatesUserTags() throws Exception {
        when(jwtUtils.extractUsernameFromToken("token")).thenReturn("admin");

        mockMvc.perform(put("/api/v1/admin/users/2/org-tags")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content("{\"orgTags\":[\"default\",\"admin\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userProfileService).assignOrgTagsToUser(2L, List.of("default", "admin"), "admin");
    }
}
