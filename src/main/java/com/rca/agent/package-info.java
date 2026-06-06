/**
 * RCA Agent — Root Cause Analysis Service.
 * <p>
 * An intelligent agent that analyzes log files and git repositories
 * to automatically identify the root cause of production issues
 * using pluggable LLM (Large Language Model) providers.
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>{@link com.rca.agent.controller} — REST API endpoints</li>
 *   <li>{@link com.rca.agent.service} — RCA orchestration</li>
 *   <li>{@link com.rca.agent.llm} — Pluggable LLM providers (OpenRouter, OpenAI, Bedrock)</li>
 *   <li>{@link com.rca.agent.analyzer.log} — Multi-format log parsers</li>
 *   <li>{@link com.rca.agent.analyzer.git} — Git repository analysis (JGit)</li>
 *   <li>{@link com.rca.agent.model} — Request/response DTOs</li>
 *   <li>{@link com.rca.agent.config} — Externalized configuration</li>
 * </ul>
 */
package com.rca.agent;
