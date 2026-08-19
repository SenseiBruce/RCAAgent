package com.rca.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "rca.llm.provider=fake")
class RcaAgentApplicationTest {

  @Test
  void contextLoads() {}

  @Test
  void main_runsWithoutError() {
    try (ConfigurableApplicationContext ctx =
        SpringApplication.run(
            RcaAgentApplication.class, "--server.port=0", "--rca.llm.provider=fake")) {
      assertThat(ctx.isActive()).isTrue();
    }
  }
}
