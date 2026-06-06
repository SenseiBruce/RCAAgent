package com.rca.agent.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmProviderInterfaceTest {

    @Test
    void interface_hasCorrectMethods() {
        LlmProvider provider = new LlmProvider() {
            @Override
            public String analyze(String prompt) {
                return "result for: " + prompt;
            }

            @Override
            public String name() {
                return "test-provider";
            }
        };

        assertThat(provider.name()).isEqualTo("test-provider");
        assertThat(provider.analyze("hello")).isEqualTo("result for: hello");
    }
}
