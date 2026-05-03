package com.lcmob.smartask.service;

import com.lcmob.smartask.client.DeepSeekClient;
import com.lcmob.smartask.model.Conversation;
import com.lcmob.smartask.model.User;
import com.lcmob.smartask.repository.ConversationRepository;
import com.lcmob.smartask.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatHandlerTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private HybridSearchService searchService;
    private DeepSeekClient deepSeekClient;
    private ConversationRepository conversationRepository;
    private UserRepository userRepository;
    private ChatHandler chatHandler;
    private WebSocketSession session;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        searchService = mock(HybridSearchService.class);
        deepSeekClient = mock(DeepSeekClient.class);
        conversationRepository = mock(ConversationRepository.class);
        userRepository = mock(UserRepository.class);
        session = mock(WebSocketSession.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(searchService.searchWithPermission(anyString(), anyString(), any(Integer.class))).thenReturn(Collections.emptyList());
        when(session.getId()).thenReturn("session-1");

        User user = new User();
        user.setId(3L);
        user.setUsername("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        chatHandler = new ChatHandler(redisTemplate, searchService, deepSeekClient, conversationRepository, userRepository);
    }

    @Test
    void processMessageDoesNotPersistEmptyAnswerBeforeStreamCompletes() throws Exception {
        doAnswer(invocation -> null)
                .when(deepSeekClient)
                .streamResponse(anyString(), anyString(), anyList(), any(), any(), any());

        chatHandler.processMessage("testuser", "什么是redis", session);

        Thread.sleep(5500);

        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void processMessagePersistsFullAnswerWhenStreamCompletes() {
        doAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(3);
            Runnable onComplete = invocation.getArgument(5);
            onChunk.accept("完整");
            onChunk.accept("回答");
            onComplete.run();
            return null;
        })
                .when(deepSeekClient)
                .streamResponse(anyString(), anyString(), anyList(), any(), any(), any());

        chatHandler.processMessage("testuser", "什么是redis", session);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertEquals("完整回答", captor.getValue().getAnswer());
    }
}
