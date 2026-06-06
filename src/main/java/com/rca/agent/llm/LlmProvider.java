package com.rca.agent.llm;

/**
 * Abstraction for LLM (Large Language Model) providers.
 * <p>
 * Implementations of this interface integrate with specific LLM services
 * (e.g., OpenRouter, OpenAI, AWS Bedrock) to perform root cause analysis.
 * The active provider is determined by the {@code rca.llm.provider} configuration property.
 */
public interface LlmProvider {

    /**
     * Sends a prompt to the LLM and returns the analysis response.
     *
     * @param prompt the formatted prompt containing issue details, log summaries, and git context
     * @return the LLM's analysis response as a raw string (expected to be JSON)
     * @throws RuntimeException if the API call fails
     */
    String analyze(String prompt);

    /**
     * Returns the identifier name of this provider.
     *
     * @return provider name (e.g., "openrouter", "openai", "bedrock")
     */
    String name();
}
