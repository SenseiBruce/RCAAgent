package com.rca.agent.fix;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request to auto-fix an issue based on RCA results.
 *
 * @param repoUrl git repository URL (must be a remote URL for PR creation)
 * @param branch base branch to create the fix from
 * @param rootCause the identified root cause from RCA
 * @param recommendations fix recommendations from RCA
 * @param codeSnippets code snippets at error locations
 * @param issueDescription original issue description
 */
public record FixRequest(
    @NotNull(message = "Repository URL is required")
        @NotEmpty(message = "Repository URL is required")
        @NotBlank(message = "Repository URL is required")
        @Pattern(
            regexp = "^(https?://|ssh://|git@).+",
            message = "Repository URL must be a git remote")
        String repoUrl,
    @Size(max = 200) String branch,
    @NotNull(message = "Root cause is required") @NotBlank(message = "Root cause is required")
        String rootCause,
    List<String> recommendations,
    List<CodeSnippetRef> codeSnippets,
    String issueDescription) {
  public record CodeSnippetRef(String filePath, int lineNumber, String snippet) {}
}
