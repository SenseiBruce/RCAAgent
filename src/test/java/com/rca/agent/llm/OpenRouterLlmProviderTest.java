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

class OpenRouterLlmProviderTest {

    private MockWebServer mockServer;
    private OpenRouterLlmProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();

        RcaProperties properties = new RcaProperties();
        properties.getLlm().getOpenrouter().setApiKey("test-key");
        properties.getLlm().getOpenrouter().setModel("test-model");
        properties.getLlm().getOpenrouter().setBaseUrl(mockServer.url("/").toString());

        provider = new OpenRouterLlmProvider(properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    @Test
    void name_returnsOpenrouter() {
        assertThat(provider.name()).isEqualTo("openrouter");
    }

    @Test
    void analyze_sendsCorrectRequest() throws Exception {
        String responseJson = """
                {
                    "choices": [{
                        "message": {
                            "content": "This is the analysis result"
                        }
                    }]
                }
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        String result = provider.analyze("test prompt");

        assertThat(result).isEqualTo("This is the analysis result");

        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/chat/completions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("test-model");
        assertThat(body).contains("test prompt");
        assertThat(body).contains("max_tokens");
    }

    @Test
    void analyze_serverError_throwsException() {
        mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        assertThatThrownBy(() -> provider.analyze("test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("LLM analysis failed");
    }

    @Test
    void analyze_invalidJson_throwsException() {
        mockServer.enqueue(new MockResponse()
                .setBody("not json")
                .setHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> provider.analyze("test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("LLM analysis failed");
    }
}
