package com.rca.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads and renders prompt templates from external files.
 * <p>
 * Prompts are loaded from:
 * <ol>
 *   <li>Custom file path specified via environment variable (highest priority)</li>
 *   <li>Classpath resource under {@code prompts/} (default)</li>
 * </ol>
 * Supports {@code {{variable}}} template placeholders.
 */
@Service
public class PromptService {

    private static final Logger log = LoggerFactory.getLogger(PromptService.class);

    private final String rcaPromptTemplate;

    public PromptService() {
        this.rcaPromptTemplate = loadPrompt("rca-analysis.txt", "RCA_PROMPT_PATH");
        log.info("Loaded RCA analysis prompt ({} chars)", rcaPromptTemplate.length());
    }

    /**
     * Renders the RCA analysis prompt with the given variables.
     *
     * @param variables map of placeholder names to values (without {{ }})
     * @return the fully rendered prompt string
     */
    public String renderRcaPrompt(Map<String, String> variables) {
        return render(rcaPromptTemplate, variables);
    }

    /**
     * Loads a prompt template, checking env override first, then classpath.
     *
     * @param classpathFile filename under {@code prompts/} on the classpath
     * @param envVariable   environment variable name for custom file path override
     * @return the prompt template content
     */
    String loadPrompt(String classpathFile, String envVariable) {
        // Check env variable for custom file path
        String customPath = System.getenv(envVariable);
        if (customPath != null && !customPath.isBlank()) {
            try {
                String content = Files.readString(Path.of(customPath), StandardCharsets.UTF_8);
                log.info("Loaded prompt from custom path: {} (via {})", customPath, envVariable);
                return content;
            } catch (IOException e) {
                log.warn("Failed to load prompt from {}: {}, falling back to classpath", customPath, e.getMessage());
            }
        }

        // Fall back to classpath
        try {
            var resource = new ClassPathResource("prompts/" + classpathFile);
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            log.info("Loaded prompt from classpath: prompts/{}", classpathFile);
            return content;
        } catch (IOException e) {
            log.error("Failed to load prompt from classpath: prompts/{}", classpathFile, e);
            throw new IllegalStateException("Cannot load prompt: " + classpathFile, e);
        }
    }

    private String render(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
