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
    private final ObjectMapper lenientMapper;

    public AutoFixService(LlmProvider llmProvider, RepoResolver repoResolver, List<GitPlatform> platforms) {
        this.llmProvider = llmProvider;
        this.repoResolver = repoResolver;
        this.platforms = platforms;
        this.lenientMapper = new ObjectMapper();
        this.lenientMapper.configure(
                com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
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

            // Resolve bare filenames to actual paths in the repo
            changes = resolveFilePaths(repoPath, changes);

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
            sb.append("CURRENT CODE AT ERROR LOCATIONS (you MUST use these exact file paths in your response):\n");
            request.codeSnippets().forEach(s ->
                    sb.append("--- ").append(s.filePath()).append(":").append(s.lineNumber()).append(" ---\n")
                            .append(s.snippet()).append("\n\n"));
        }

        sb.append("""
                
                Respond in this EXACT JSON format. Do NOT deviate from this structure:
                ```json
                {
                  "changes": [
                    {
                      "filePath": "src/main/java/com/rca/agent/service/RcaService.java",
                      "searchReplace": [
                        {
                          "search": "this.gitAnalyzer = gitAnalyzer;",
                          "replace": "this.gitAnalyzer = Objects.requireNonNull(gitAnalyzer, \\"gitAnalyzer must not be null\\");"
                        }
                      ]
                    }
                  ],
                  "commitMessage": "fix: add null check for gitAnalyzer"
                }
                ```
                
                CRITICAL rules:
                - "searchReplace" is a JSON ARRAY of objects, each with "search" and "replace" string fields
                - "search": exact existing code from the file (use \\n for newlines within the string)
                - "replace": the replacement code (use \\n for newlines within the string)
                - Do NOT use string concatenation (+) in JSON values
                - Do NOT use diff syntax (-, +, >) in values
                - The "filePath" MUST match the paths from the code snippets above
                - Do NOT create new files or packages
                """);

        return sb.toString();
    }

    private List<FileChange> parseFileChanges(String llmResponse) {
        try {
            String json = extractJson(llmResponse);
            JsonNode root = lenientMapper.readTree(json);
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
                if (filePath.isBlank()) continue;

                // Support both formats: searchReplace array (preferred) and flat search/replace
                JsonNode searchReplaceNode = change.path("searchReplace");
                if (!searchReplaceNode.isMissingNode() && searchReplaceNode.isArray()) {
                    List<SearchReplace> patches = new ArrayList<>();
                    for (JsonNode sr : searchReplaceNode) {
                        String search = sr.path("search").asText("");
                        String replace = sr.path("replace").asText("");
                        if (!search.isBlank()) {
                            patches.add(new SearchReplace(search, replace));
                        }
                    }
                    if (!patches.isEmpty()) {
                        changes.add(new FileChange(filePath, null, patches));
                    }
                } else if (change.has("search") && change.has("replace")) {
                    // Flat format: search/replace directly on the change object
                    String search = change.path("search").asText("");
                    String replace = change.path("replace").asText("");
                    if (!search.isBlank()) {
                        changes.add(new FileChange(filePath, null, List.of(new SearchReplace(search, replace))));
                    }
                } else {
                    // Legacy: full content mode
                    String content = change.path("content").asText(
                            change.path("code").asText(""));
                    if (!content.isBlank()) {
                        changes.add(new FileChange(filePath, content, List.of()));
                    }
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

    private void applyAndPush(Path repoPath, String branchName, List<FileChange> changes,
                              FixRequest request, String token) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            git.checkout().setCreateBranch(true).setName(branchName).call();

            for (FileChange change : changes) {
                Path filePath = repoPath.resolve(change.filePath());
                if (!change.patches().isEmpty()) {
                    // Search/replace mode: read existing file, apply patches
                    if (!Files.exists(filePath)) {
                        log.warn("Skipping patch for non-existent file: {}", change.filePath());
                        continue;
                    }
                    String existingContent = Files.readString(filePath);
                    for (SearchReplace patch : change.patches()) {
                        if (existingContent.contains(patch.search())) {
                            existingContent = existingContent.replace(patch.search(), patch.replace());
                        } else {
                            log.warn("Search text not found in {}: '{}'",
                                    change.filePath(), patch.search().substring(0, Math.min(50, patch.search().length())));
                        }
                    }
                    Files.writeString(filePath, existingContent);
                } else {
                    // Full content mode (legacy)
                    Files.createDirectories(filePath.getParent());
                    Files.writeString(filePath, change.content());
                }
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

    /**
     * Resolves file paths that the LLM returned as bare filenames (e.g. "RcaService.java")
     * to their actual relative path in the repo (e.g. "src/main/java/com/rca/agent/service/RcaService.java").
     */
    private List<FileChange> resolveFilePaths(Path repoPath, List<FileChange> changes) {
        List<FileChange> resolved = new ArrayList<>();
        for (FileChange change : changes) {
            String filePath = change.filePath();
            // If path has no directory separator, it's a bare filename — search the repo
            if (!filePath.contains("/") && !filePath.contains("\\")) {
                try {
                    var found = Files.walk(repoPath)
                            .filter(p -> p.getFileName().toString().equals(filePath))
                            .filter(p -> !p.toString().contains(".git"))
                            .findFirst();
                    if (found.isPresent()) {
                        String resolvedPath = repoPath.relativize(found.get()).toString().replace("\\\\", "/");
                        log.info("Resolved bare filename '{}' → '{}'", filePath, resolvedPath);
                        resolved.add(new FileChange(resolvedPath, change.content(), change.patches()));
                        continue;
                    }
                } catch (Exception e) {
                    log.debug("Failed to resolve path for {}: {}", filePath, e.getMessage());
                }
            }
            resolved.add(change);
        }
        return resolved;
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

    private record FileChange(String filePath, String content, List<SearchReplace> patches) {}
    private record SearchReplace(String search, String replace) {}
}
