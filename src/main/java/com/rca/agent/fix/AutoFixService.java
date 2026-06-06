package com.rca.agent.fix;

import com.rca.agent.analyzer.git.RepoResolver;
import com.rca.agent.analyzer.git.RepoResolver.ResolvedRepo;
import com.rca.agent.llm.LlmProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

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
 *   <li>Push the branch to GitHub</li>
 *   <li>Create a pull request via GitHub API</li>
 * </ol>
 */
@Service
public class AutoFixService {

    private static final Logger log = LoggerFactory.getLogger(AutoFixService.class);

    private final LlmProvider llmProvider;
    private final RepoResolver repoResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient githubClient;

    public AutoFixService(LlmProvider llmProvider, RepoResolver repoResolver) {
        this.llmProvider = llmProvider;
        this.repoResolver = repoResolver;
        this.githubClient = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .build();
    }

    /**
     * Generates a fix, pushes to a branch, and creates a PR.
     *
     * @param request the fix request with RCA context
     * @param githubToken GitHub personal access token for push and PR creation
     * @return response with PR URL and summary
     */
    public FixResponse fix(FixRequest request, String githubToken) {
        log.info("Starting auto-fix for: {}", request.rootCause());

        ResolvedRepo repo = null;
        try {
            // Step 1: Resolve repo (clone/use cached)
            repo = repoResolver.resolve(request.repoUrl(), request.branch());
            Path repoPath = Path.of(repo.localPath());

            // Step 2: Ask LLM to generate fix
            String fixPrompt = buildFixPrompt(request);
            String llmResponse = llmProvider.analyze(fixPrompt);
            List<FileChange> changes = parseFileChanges(llmResponse);

            if (changes.isEmpty()) {
                return new FixResponse(null, null, List.of(), "LLM could not generate a fix.");
            }

            // Step 3: Create branch, apply changes, commit, push
            String branchName = "fix/rca-" + System.currentTimeMillis();
            applyAndPush(repoPath, branchName, changes, request, githubToken);

            // Step 4: Create PR via GitHub API
            String prUrl = createPullRequest(request.repoUrl(), branchName,
                    request.branch() != null ? request.branch() : "main",
                    request, githubToken);

            List<String> filesChanged = changes.stream().map(FileChange::filePath).toList();
            log.info("Auto-fix complete. PR: {}", prUrl);

            return new FixResponse(prUrl, branchName, filesChanged,
                    "Applied fix for: " + request.rootCause());

        } catch (Exception e) {
            log.error("Auto-fix failed", e);
            return new FixResponse(null, null, List.of(), "Auto-fix failed: " + e.getMessage());
        }
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
            JsonNode root = objectMapper.readTree(json);
            JsonNode changesNode = root.path("changes");
            if (!changesNode.isArray()) return List.of();

            List<FileChange> changes = new ArrayList<>();
            for (JsonNode change : changesNode) {
                String filePath = change.path("filePath").asText("");
                String content = change.path("content").asText("");
                if (!filePath.isBlank() && !content.isBlank()) {
                    changes.add(new FileChange(filePath, content));
                }
            }
            return changes;
        } catch (Exception e) {
            log.error("Failed to parse LLM fix response", e);
            return List.of();
        }
    }

    private void applyAndPush(Path repoPath, String branchName, List<FileChange> changes,
                              FixRequest request, String githubToken) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            // Create and checkout new branch
            git.checkout().setCreateBranch(true).setName(branchName).call();

            // Apply file changes
            for (FileChange change : changes) {
                Path filePath = repoPath.resolve(change.filePath());
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, change.content());
                git.add().addFilepattern(change.filePath()).call();
            }

            // Commit
            git.commit()
                    .setMessage("fix: auto-fix for - " + truncate(request.rootCause(), 72))
                    .call();

            // Push
            git.push()
                    .setRemote("origin")
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubToken, ""))
                    .setTimeout(30)
                    .call();

            log.info("Pushed branch {} with {} file changes", branchName, changes.size());
        }
    }

    private String createPullRequest(String repoUrl, String headBranch, String baseBranch,
                                     FixRequest request, String githubToken) {
        try {
            String ownerRepo = extractOwnerRepo(repoUrl);
            String title = "fix: " + truncate(request.rootCause(), 100);
            String body = buildPrBody(request);

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "title", title,
                    "body", body,
                    "head", headBranch,
                    "base", baseBranch
            ));

            String response = githubClient.post()
                    .uri("/repos/" + ownerRepo + "/pulls")
                    .header("Authorization", "Bearer " + githubToken)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode prJson = objectMapper.readTree(response);
            return prJson.path("html_url").asText();
        } catch (Exception e) {
            log.error("Failed to create PR", e);
            return null;
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
                *This PR was automatically generated by the RCA Agent.*
                """.formatted(
                request.rootCause(),
                request.issueDescription() != null ? request.issueDescription() : "N/A",
                request.recommendations() != null ? String.join("\n- ", request.recommendations()) : "N/A"
        );
    }

    private String extractOwnerRepo(String url) {
        // https://github.com/owner/repo.git → owner/repo
        return url.replaceAll("https://github\\.com/", "")
                .replaceAll("\\.git$", "")
                .replaceAll("/$", "");
    }

    private String extractJson(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) return response.substring(start, end + 1);
        return response;
    }

    private String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private record FileChange(String filePath, String content) {}
}
