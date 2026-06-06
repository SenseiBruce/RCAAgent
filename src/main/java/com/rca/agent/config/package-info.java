/**
 * Externalized configuration for the RCA Agent.
 * <p>
 * All configuration is bound from {@code application.yml} and environment variables.
 * Sensitive values (API keys) are loaded from a {@code .env} file (gitignored)
 * via the spring-dotenv library.
 */
package com.rca.agent.config;
