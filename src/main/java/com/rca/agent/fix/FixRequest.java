package com.rca.agent.fix;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request to auto-fix an issue based on RCA results.
 *
 * @param repoUrl          git repository URL (must be a remote URL for PR creation)
 * @param branch           base branch to create the fix from
 * @param rootCause        the identified root cause from RCA
 * @param recommendations  fix recommendations from RCA
 * @param codeSnippets     code snippets at error locations
 * @param issueDescription original issue description
 */
public record FixRequest(
        @NotBlank(message = "Repository URL is required")
        String repoUrl,
        String branch,
        @NotBlank(message = "Root cause is required")
        String rootCause,
        List<String> recommendations,
        List<CodeSnippetRef> codeSnippets,
        String issueDescription
) {
    public record CodeSnippetRef(String filePath, int lineNumber, String snippet) {}
}
