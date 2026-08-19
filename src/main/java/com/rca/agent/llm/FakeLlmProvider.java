package com.rca.agent.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Offline LLM stand-in that never opens a network connection.
 *
 * <p>Activated with {@code rca.llm.provider=fake} or Spring profile {@code offline}. Used by the
 * test suite, Docker Compose default, and local demos without API keys.
 */
@Component
@ConditionalOnProperty(name = "rca.llm.provider", havingValue = "fake")
public class FakeLlmProvider implements LlmProvider {

  static final String DEFAULT_ANALYSIS =
      """
            {
              "rootCause": "Simulated root cause from FakeLlmProvider (no live LLM call)",
              "severity": "MEDIUM",
              "evidenceFromLogs": ["Offline fake provider — inspect logs and git locally"],
              "recommendations": ["Set LLM_PROVIDER=openrouter|openai|bedrock with a real API key for production analysis"]
            }
            """;

  @Override
  public String analyze(String prompt) {
    if (prompt == null || prompt.isBlank()) {
      return DEFAULT_ANALYSIS;
    }
    String snippet = prompt.length() > 80 ? prompt.substring(0, 80) + "..." : prompt;
    return """
                {
                  "rootCause": "Offline analysis of: %s",
                  "severity": "MEDIUM",
                  "evidenceFromLogs": ["FakeLlmProvider processed the prompt without network access"],
                  "recommendations": ["Replace fake provider with a live LLM for real RCA"]
                }
                """
        .formatted(escapeJson(snippet));
  }

  @Override
  public String name() {
    return "fake";
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
  }
}
