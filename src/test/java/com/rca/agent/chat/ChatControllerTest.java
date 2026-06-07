package com.rca.agent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void chat_validRequest_returnsOk() throws Exception {
        when(chatService.chat(any())).thenReturn(
                ChatResponse.reply("How can I help?", "session-123"));

        mockMvc.perform(post("/api/v1/rca/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hello\", \"sessionId\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("How can I help?"))
                .andExpect(jsonPath("$.sessionId").value("session-123"))
                .andExpect(jsonPath("$.action").doesNotExist());
    }

    @Test
    void chat_withSessionId_returnsOk() throws Exception {
        when(chatService.chat(any())).thenReturn(
                ChatResponse.reply("I see.", "existing-session"));

        mockMvc.perform(post("/api/v1/rca/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"NPE on login\", \"sessionId\": \"existing-session\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("existing-session"));
    }

    @Test
    void chat_actionResponse_includesAction() throws Exception {
        when(chatService.chat(any())).thenReturn(
                ChatResponse.withAction("Analysis complete!", "session-1", "rca_complete"));

        mockMvc.perform(post("/api/v1/rca/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"analyze\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("rca_complete"))
                .andExpect(jsonPath("$.message").value("Analysis complete!"));
    }

    @Test
    void chat_blankMessage_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/rca/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_missingMessage_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/rca/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\": \"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_emptyBody_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/rca/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_serviceThrows_returns500() throws Exception {
        when(chatService.chat(any())).thenThrow(new RuntimeException("LLM down"));

        mockMvc.perform(post("/api/v1/rca/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"test\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }
}
