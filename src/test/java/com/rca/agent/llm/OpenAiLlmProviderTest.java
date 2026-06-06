package com.rca.agent.llm;

import com.rca.agent.config.RcaProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiLlmProviderTest {

    private MockWebServer mockServer;
    private OpenAiLlmProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();

        RcaProperties properties = new RcaProperties();
        properties.getLlm().getOpenai().setApiKey("sk-test-key");
        properties.getLlm().getOpenai().setModel("gpt-4");
        properties.getLlm().getOpenai().setBaseUrl(mockServer.url("/").toString());

        provider = new OpenAiLlmProvider(properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    @Test
    void name_returnsOpenai() {
        assertThat(provider.name()).isEqualTo("openai");
    }

    @Test
    void analyze_sendsCorrectRequest() throws Exception {
        String responseJson = """
                {
                    "choices": [{
                        "message": {
                            "content": "OpenAI analysis"
                        }
                    }]
                }
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        String result = provider.analyze("analyze this");

        assertThat(result).isEqualTo("OpenAI analysis");

        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/chat/completions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sk-test-key");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("gpt-4");
        assertThat(body).contains("analyze this");
    }

    @Test
    void analyze_serverError_throwsException() {
        mockServer.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> provider.analyze("test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("LLM analysis failed");
    }

    @Test
    void analyze_malformedResponse_throwsException() {
        mockServer.enqueue(new MockResponse()
                .setBody("{invalid")
                .setHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> provider.analyze("test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("LLM analysis failed");
    }
}
