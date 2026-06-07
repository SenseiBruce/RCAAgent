package com.rca.agent.chat;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ChatModelTest {

    @Test
    void chatMessage_userFactory_setsRoleAndTimestamp() {
        ChatMessage msg = ChatMessage.user("hello");

        assertThat(msg.role()).isEqualTo("user");
        assertThat(msg.content()).isEqualTo("hello");
        assertThat(msg.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void chatMessage_assistantFactory_setsRole() {
        ChatMessage msg = ChatMessage.assistant("response");

        assertThat(msg.role()).isEqualTo("assistant");
        assertThat(msg.content()).isEqualTo("response");
    }

    @Test
    void chatResponse_reply_hasNoAction() {
        ChatResponse resp = ChatResponse.reply("hello", "session-1");

        assertThat(resp.message()).isEqualTo("hello");
        assertThat(resp.sessionId()).isEqualTo("session-1");
        assertThat(resp.action()).isNull();
    }

    @Test
    void chatResponse_withAction_includesAction() {
        ChatResponse resp = ChatResponse.withAction("done", "session-2", "rca_complete");

        assertThat(resp.message()).isEqualTo("done");
        assertThat(resp.sessionId()).isEqualTo("session-2");
        assertThat(resp.action()).isEqualTo("rca_complete");
    }

    @Test
    void chatRequest_recordAccessors() {
        ChatRequest req = new ChatRequest("test message", "session-abc");

        assertThat(req.message()).isEqualTo("test message");
        assertThat(req.sessionId()).isEqualTo("session-abc");
    }

    @Test
    void chatRequest_nullSessionId() {
        ChatRequest req = new ChatRequest("hello", null);

        assertThat(req.sessionId()).isNull();
    }
}
