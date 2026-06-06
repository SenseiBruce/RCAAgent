package com.rca.agent.fix;

import com.rca.agent.analyzer.git.RepoResolver;
import com.rca.agent.analyzer.git.RepoResolver.ResolvedRepo;
import com.rca.agent.fix.platform.GitPlatform;
import com.rca.agent.fix.platform.PrRequest;
import com.rca.agent.llm.LlmProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Sub-agent that generates code fixes based on RCA results.
 * <p>
 * Workflow:
 * <ol>
 *   <li>Ask LLM to generate code fix + unit tests based on root cause</li>
 *   <li>Apply changes to a new branch in the cloned repo</li>
 *   <li>Push the branch</li>
 *   <li>Create a pull/merge request via the appropriate platform (GitHub/GitLab)</li>
 * </ol>
 */
@Service
public class AutoFixService {

    private static final Logger log = LoggerFactory.getLogger(AutoFixService.class);

    private final LlmProvider llmProvider;
    private final RepoResolver repoResolver;
    private final List<GitPlatform> platforms;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AutoFixService(LlmProvider llmProvider, RepoResolver repoResolver, List<GitPlatform> platforms) {
        this.llmProvider = llmProvider;
        this.repoResolver = repoResolver;
        this.platforms = platforms;
    }

    /**
     * Generates a fix, pushes to a branch, and creates a PR/MR.
     *
     * @param request fix request with RCA context
     * @param token   platform token (GitHub PAT or GitLab private token)
     * @return response with PR/MR URL and summary
     */
    public FixResponse fix(FixRequest request, String token) {
        log.info("Starting auto-fix for: {}", request.rootCause());

        ResolvedRepo repo = null;
        try {
            repo = repoResolver.resolve(request.repoUrl(), request.branch());
            Path repoPath = Path.of(repo.localPath());

            // Step 1: Ask LLM to generate fix
            String fixPrompt = buildFixPrompt(request);
            log.debug("Fix prompt length: {} chars", fixPrompt.length());
            String llmResponse = llmProvider.analyze(fixPrompt);
            log.info("LLM response length: {} chars", llmResponse.length());
            List<FileChange> changes = parseFileChanges(llmResponse);

            if (changes.isEmpty()) {
                log.warn("No valid file changes parsed from LLM response. First 200 chars: {}",
                        llmResponse.substring(0, Math.min(200, llmResponse.length())));
                return new FixResponse(null, null, List.of(), "LLM could not generate a fix.");
            }

            // Step 2: Create branch, apply changes, commit, push
            String branchName = "fix/rca-" + System.currentTimeMillis();
            applyAndPush(repoPath, branchName, changes, request, token);

            // Step 3: Create PR/MR via appropriate platform
            String baseBranch = request.branch() != null ? request.branch() : "main";
            String prUrl = createPrOnPlatform(request.repoUrl(), branchName, baseBranch, request, token);

            List<String> filesChanged = changes.stream().map(FileChange::filePath).toList();
            log.info("Auto-fix complete. PR/MR: {}", prUrl);

            return new FixResponse(prUrl, branchName, filesChanged,
                    "Applied fix for: " + request.rootCause());

        } catch (Exception e) {
            log.error("Auto-fix failed", e);
            return new FixResponse(null, null, List.of(), "Auto-fix failed: " + e.getMessage());
        }
    }

    private String createPrOnPlatform(String repoUrl, String headBranch, String baseBranch,
                                      FixRequest request, String token) {
        GitPlatform platform = platforms.stream()
                .filter(p -> p.supports(repoUrl))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported git platform for URL: " + repoUrl));

        String title = "fix: " + truncate(request.rootCause(), 100);
        String body = buildPrBody(request);

        PrRequest prRequest = new PrRequest(repoUrl, headBranch, baseBranch, title, body, token);
        log.info("Creating PR/MR on platform: {}", platform.name());
        return platform.createPullRequest(prRequest);
    }

    private String buildFixPrompt(FixRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are a senior software engineer. Based on the root cause analysis below, generate the exact code changes needed to fix the issue.
                Also generate unit tests for the fix.
                
                ROOT CAUSE:
                %s
                
                ISSUE:
                %s
                
                RECOMMENDATIONS:
                %s
                
                """.formatted(request.rootCause(), request.issueDescription(),
                String.join("\n", request.recommendations() != null ? request.recommendations() : List.of())));

        if (request.codeSnippets() != null && !request.codeSnippets().isEmpty()) {
            sb.append("CURRENT CODE AT ERROR LOCATIONS:\n");
            request.codeSnippets().forEach(s ->
                    sb.append("--- ").append(s.filePath()).append(":").append(s.lineNumber()).append(" ---\n")
                            .append(s.snippet()).append("\n\n"));
        }

        sb.append("""
                
                Respond in this exact JSON format (array of file changes):
                {
                  "changes": [
                    {
                      "filePath": "relative/path/to/File.java",
                      "content": "full file content after fix"
                    }
                  ],
                  "commitMessage": "fix: description of what was fixed"
                }
                
                Important:
                - Include the COMPLETE file content for each changed file
                - Include unit test files as separate entries
                - Use proper package declarations and imports
                """);

        return sb.toString();
    }

    private List<FileChange> parseFileChanges(String llmResponse) {
        try {
            String json = extractJson(llmResponse);
            // LLMs often return literal newlines inside JSON string values — fix them
            json = fixUnescapedNewlines(json);
            JsonNode root = objectMapper.readTree(json);
            JsonNode changesNode = root.path("changes");
            if (!changesNode.isArray()) {
                log.warn("LLM response has no 'changes' array. Keys present: {}", root.fieldNames());
                return List.of();
            }

            List<FileChange> changes = new ArrayList<>();
            for (JsonNode change : changesNode) {
                String filePath = change.path("filePath").asText(
                        change.path("file_path").asText(
                                change.path("path").asText("")));
                String content = change.path("content").asText(
                        change.path("code").asText(""));
                if (!filePath.isBlank() && !content.isBlank()) {
                    changes.add(new FileChange(filePath, content));
                }
            }
            log.info("Parsed {} file changes from LLM response", changes.size());
            return changes;
        } catch (Exception e) {
            log.error("Failed to parse LLM fix response: {}", e.getMessage());
            log.debug("Raw LLM response (first 500 chars): {}",
                    llmResponse.substring(0, Math.min(500, llmResponse.length())));
            return List.of();
        }
    }

    /**
     * Fixes literal newlines/tabs inside JSON string values that LLMs produce.
     * Replaces unescaped control characters within quoted strings with their escape sequences.
     */
    private String fixUnescapedNewlines(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                sb.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }

            if (inString) {
                switch (c) {
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void applyAndPush(Path repoPath, String branchName, List<FileChange> changes,
                              FixRequest request, String token) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            git.checkout().setCreateBranch(true).setName(branchName).call();

            for (FileChange change : changes) {
                Path filePath = repoPath.resolve(change.filePath());
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, change.content());
                git.add().addFilepattern(change.filePath()).call();
            }

            git.commit()
                    .setMessage("fix: auto-fix for - " + truncate(request.rootCause(), 72))
                    .call();

            git.push()
                    .setRemote("origin")
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                    .setTimeout(30)
                    .call();

            log.info("Pushed branch {} with {} file changes", branchName, changes.size());
        }
    }

    private String buildPrBody(FixRequest request) {
        return """
                ## 🔍 Auto-Fix from RCA Agent
                
                ### Root Cause
                %s
                
                ### Issue
                %s
                
                ### Changes Made
                %s
                
                ---
                *This PR/MR was automatically generated by the RCA Agent.*
                """.formatted(
                request.rootCause(),
                request.issueDescription() != null ? request.issueDescription() : "N/A",
                request.recommendations() != null ? String.join("\n- ", request.recommendations()) : "N/A"
        );
    }

    private String extractJson(String response) {
        // Strip markdown code fences
        String cleaned = response;
        if (cleaned.contains("```")) {
            cleaned = cleaned.replaceAll("```(?:json)?\\s*", "").replaceAll("```\\s*$", "");
        }

        // Find the outermost balanced JSON object
        int start = cleaned.indexOf("{");
        if (start < 0) return response;

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return cleaned.substring(start, i + 1);
                }
            }
        }
        // Fallback to old logic if balanced parse fails
        int end = cleaned.lastIndexOf("}");
        if (start >= 0 && end > start) return cleaned.substring(start, end + 1);
        return response;
    }

    private String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private record FileChange(String filePath, String content) {}
}
