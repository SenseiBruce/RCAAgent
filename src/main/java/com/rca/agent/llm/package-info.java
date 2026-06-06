/**
 * Pluggable LLM (Large Language Model) provider layer.
 * <p>
 * Supports multiple providers through the {@link com.rca.agent.llm.LlmProvider} interface.
 * The active provider is selected via the {@code rca.llm.provider} configuration property.
 *
 * <h2>Available Providers</h2>
 * <ul>
 *   <li>{@code openrouter} — OpenRouter (multi-model gateway)</li>
 *   <li>{@code openai} — OpenAI direct API</li>
 *   <li>{@code bedrock} — AWS Bedrock (Claude)</li>
 * </ul>
 */
package com.rca.agent.llm;
