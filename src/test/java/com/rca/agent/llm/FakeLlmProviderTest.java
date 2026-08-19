package com.rca.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FakeLlmProviderTest {

  private final FakeLlmProvider provider = new FakeLlmProvider();

  @Test
  void name_isFake() {
    assertThat(provider.name()).isEqualTo("fake");
  }

  @Test
  void analyze_blankPrompt_returnsDefaultJson() {
    String result = provider.analyze("  ");
    assertThat(result).contains("FakeLlmProvider").contains("\"severity\": \"MEDIUM\"");
  }

  @Test
  void analyze_includesSanitizedPromptSnippet() {
    String result = provider.analyze("NullPointerException in \"UserService\"");
    assertThat(result).contains("NullPointerException").doesNotContain("\"UserService\"");
    assertThat(result).contains("\\\"UserService\\\"");
  }
}
