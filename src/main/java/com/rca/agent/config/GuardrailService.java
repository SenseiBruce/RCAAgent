package com.rca.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

/**
 * Enforces guardrails for the RCA agent — input validation, rate limiting, and access controls.
 */
@Service
public class GuardrailService {

    private static final Logger log = LoggerFactory.getLogger(GuardrailService.class);
    private final RcaProperties.GuardrailsProperties config;
    private final Semaphore concurrencyLimiter;
    private final List<Pattern> blockedPatterns;
    private final List<Pattern> allowedRepoPatterns;

    public GuardrailService(RcaProperties properties) {
        this.config = properties.getGuardrails();
        this.concurrencyLimiter = new Semaphore(config.getMaxConcurrentAnalyses());
        this.blockedPatterns = parsePatterns(config.getBlockedPatterns());
        this.allowedRepoPatterns = parsePatterns(config.getAllowedRepoPatterns());
    }

    public void validateInput(String input) {
        if (input == null || input.isBlank()) {
            throw new GuardrailViolation("Input cannot be empty");
        }
        if (input.length() > config.getMaxInputLength()) {
            throw new GuardrailViolation("Input exceeds max length of " + config.getMaxInputLength() + " characters");
        }
        for (Pattern pattern : blockedPatterns) {
            if (pattern.matcher(input).find()) {
                log.warn("Blocked pattern detected in input");
                throw new GuardrailViolation("Input contains blocked content");
            }
        }
    }

    public void validateRepoAccess(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) return;
        if (allowedRepoPatterns.isEmpty()) return;

        boolean allowed = allowedRepoPatterns.stream()
                .anyMatch(p -> p.matcher(repoUrl).matches());
        if (!allowed) {
            throw new GuardrailViolation("Repository not in allowed list: " + repoUrl);
        }
    }

    public void validateAutoFixAllowed() {
        if (!config.isAllowAutoFix()) {
            throw new GuardrailViolation("Auto-fix is disabled by guardrails configuration");
        }
    }

    public boolean requiresApproval() {
        return config.isRequireApprovalForPush();
    }

    public int getMaxFilesPerFix() {
        return config.getMaxFilesPerFix();
    }

    public int getMaxHistoryMessages() {
        return config.getMaxHistoryMessages();
    }

    public int getMaxLogLines() {
        return config.getMaxLogLines();
    }

    public boolean acquireAnalysisSlot() {
        return concurrencyLimiter.tryAcquire();
    }

    public void releaseAnalysisSlot() {
        concurrencyLimiter.release();
    }

    private List<Pattern> parsePatterns(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Pattern::compile)
                .toList();
    }

    public static class GuardrailViolation extends RuntimeException {
        public GuardrailViolation(String message) {
            super(message);
        }
    }
}
