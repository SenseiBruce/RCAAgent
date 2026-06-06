package com.rca.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the RCA (Root Cause Analysis) Agent application.
 * <p>
 * This Spring Boot service analyzes log files and git repositories
 * to automatically identify the root cause of production issues
 * using pluggable LLM providers.
 *
 * @author RCA Agent Team
 * @version 1.0.0
 */
@SpringBootApplication
public class RcaAgentApplication {

    /**
     * Starts the RCA Agent Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(RcaAgentApplication.class, args);
    }
}
