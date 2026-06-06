package com.rca.agent.fix.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * GitLab implementation for creating merge requests via the GitLab REST API.
 * <p>
 * Supports both gitlab.com and self-hosted GitLab instances.
 */
@Component
public class GitLabPlatform implements GitPlatform {

    private static final Logger log = LoggerFactory.getLogger(GitLabPlatform.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(String repoUrl) {
        return repoUrl.contains("gitlab");
    }

    @Override
    public String createPullRequest(PrRequest request) {
        try {
            String baseUrl = extractBaseUrl(request.repoUrl());
            String projectPath = extractProjectPath(request.repoUrl());
            String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);

            WebClient client = WebClient.builder()
                    .baseUrl(baseUrl + "/api/v4")
                    .build();

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "source_branch", request.headBranch(),
                    "target_branch", request.baseBranch(),
                    "title", request.title(),
                    "description", request.body()
            ));

            String response = client.post()
                    .uri("/projects/" + encodedPath + "/merge_requests")
                    .header("PRIVATE-TOKEN", request.token())
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode mrJson = objectMapper.readTree(response);
            String url = mrJson.path("web_url").asText();
            log.info("Created GitLab MR: {}", url);
            return url;
        } catch (Exception e) {
            log.error("Failed to create GitLab MR", e);
            return null;
        }
    }

    @Override
    public String name() {
        return "gitlab";
    }

    /**
     * Extracts the base URL (scheme + host) from a GitLab repo URL.
     * Supports both gitlab.com and self-hosted instances.
     * <p>
     * Example: https://gitlab.company.com/team/project.git → https://gitlab.company.com
     */
    private String extractBaseUrl(String url) {
        // https://gitlab.com/owner/repo.git → https://gitlab.com
        String withoutProtocol = url.replaceAll("https?://", "");
        String host = withoutProtocol.split("/")[0];
        return "https://" + host;
    }

    /**
     * Extracts the project path from a GitLab repo URL.
     * <p>
     * Example: https://gitlab.com/team/subgroup/project.git → team/subgroup/project
     */
    private String extractProjectPath(String url) {
        return url.replaceAll("https?://[^/]+/", "")
                .replaceAll("\\.git$", "")
                .replaceAll("/$", "");
    }
}
