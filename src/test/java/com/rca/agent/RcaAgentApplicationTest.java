package com.rca.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "rca.llm.provider=openrouter",
        "rca.llm.openrouter.api-key=test-key",
        "rca.llm.openrouter.model=test-model",
        "rca.llm.openrouter.base-url=http://localhost:1234"
})
class RcaAgentApplicationTest {

    @Test
    void contextLoads() {
    }

    @Test
    void main_runsWithoutError() {
        // Just verify main method exists and is callable
        RcaAgentApplication.main(new String[]{"--server.port=0", "--rca.llm.provider=openrouter", "--rca.llm.openrouter.api-key=test", "--rca.llm.openrouter.base-url=http://localhost:1234"});
    }
}
