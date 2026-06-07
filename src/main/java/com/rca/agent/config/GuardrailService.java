package com.rca.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

/**
 * Enforces guardrails for the RCA agent — prompt injection detection, topic enforcement,
 * input validation, rate limiting, and access controls.
 */
@Service
public class GuardrailService {

    private static final Logger log = LoggerFactory.getLogger(GuardrailService.class);
    private final RcaProperties.GuardrailsProperties config;
    private final Semaphore concurrencyLimiter;
    private final List<Pattern> blockedPatterns;
    private final List<Pattern> allowedRepoPatterns;

    // Built-in prompt injection patterns (always active)
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore (all )?(previous|above|prior) (instructions|prompts|rules)"),
            Pattern.compile("(?i)disregard (your|all|the) (instructions|rules|guidelines|programming)"),
            Pattern.compile("(?i)you are now (a |an )?(?!rca|root cause)"),
            Pattern.compile("(?i)act as (a |an )?(?!rca|root cause)"),
            Pattern.compile("(?i)pretend (you are|to be|you're) (a |an )?(?!rca|root cause)"),
            Pattern.compile("(?i)forget (your|all|everything|prior|previous)"),
            Pattern.compile("(?i)new (instructions|role|persona|identity)"),
            Pattern.compile("(?i)override (your|system|the) (prompt|instructions|rules)"),
            Pattern.compile("(?i)reveal (your|the|system) (prompt|instructions|system message)"),
            Pattern.compile("(?i)what (is|are) your (system |initial )?(prompt|instructions)"),
            Pattern.compile("(?i)output (your|the) (system |initial )?(prompt|instructions)"),
            Pattern.compile("(?i)repeat (your|the) (system |initial )?(prompt|instructions)"),
            Pattern.compile("(?i)jailbreak"),
            Pattern.compile("(?i)DAN (mode|prompt)"),
            Pattern.compile("(?i)developer mode")
    );

    // Off-topic patterns — requests clearly unrelated to RCA/debugging
    private static final List<Pattern> OFF_TOPIC_PATTERNS = List.of(
            Pattern.compile("(?i)write (me |a )?(poem|song|story|essay|joke|recipe)"),
            Pattern.compile("(?i)(compose|generate) (a )?(poem|song|story|essay|creative)"),
            Pattern.compile("(?i)help me (with |)(homework|assignment|exam|test answers)"),
            Pattern.compile("(?i)(translate|summarize) (this |the )?(book|article|novel|chapter)"),
            Pattern.compile("(?i)what('s| is) (the meaning of life|your opinion on|your favorite)"),
            Pattern.compile("(?i)(tell|give) me (a |)(joke|riddle|fun fact)"),
            Pattern.compile("(?i)role ?play"),
            Pattern.compile("(?i)generate (an? )?(image|picture|photo|video|audio)"),
            Pattern.compile("(?i)(hack|exploit|attack|phish|crack|bypass security|sql inject)"),
            Pattern.compile("(?i)(create|write|generate) (a |)(malware|virus|trojan|ransomware|keylogger)"),
            Pattern.compile("(?i)how (to|do I) (hack|exploit|attack|phish|ddos)")
    );

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
        checkPromptInjection(input);
        checkOffTopic(input);
        for (Pattern pattern : blockedPatterns) {
            if (pattern.matcher(input).find()) {
                log.warn("Custom blocked pattern detected in input");
                throw new GuardrailViolation("Input contains blocked content");
            }
        }
    }

    private void checkPromptInjection(String input) {
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("Prompt injection attempt detected");
                throw new GuardrailViolation("I can only help with root cause analysis and debugging. This request is not allowed.");
            }
        }
    }

    private void checkOffTopic(String input) {
        for (Pattern pattern : OFF_TOPIC_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("Off-topic request rejected: {}", input.substring(0, Math.min(50, input.length())));
                throw new GuardrailViolation("I'm an RCA assistant — I can only help investigate production issues, analyze logs, and debug errors. Please ask something related to incident investigation.");
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
