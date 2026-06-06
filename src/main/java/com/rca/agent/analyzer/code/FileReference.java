package com.rca.agent.analyzer.code;

/**
 * Represents a source file reference extracted from a log error message.
 *
 * @param filePath  relative or class-based file path (e.g., "PaymentService.java")
 * @param lineNumber the line number referenced in the error (0 if not available)
 */
public record FileReference(
        String filePath,
        int lineNumber
) {}
