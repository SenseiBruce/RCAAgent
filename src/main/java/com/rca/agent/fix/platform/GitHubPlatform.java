package com.rca.agent.fix.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * GitHub implementation for creating pull requests via the GitHub REST API.
 */
@Component
public class GitHubPlatform implements GitPlatform {

    private static final Logger log = LoggerFactory.getLogger(GitHubPlatform.class);
    private final WebClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GitHubPlatform() {
        this.client = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .build();
    }

    @Override
    public boolean supports(String repoUrl) {
        return repoUrl.contains("github.com");
    }

    @Override
    public String createPullRequest(PrRequest request) {
        try {
            String ownerRepo = extractOwnerRepo(request.repoUrl());
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "title", request.title(),
                    "body", request.body(),
                    "head", request.headBranch(),
                    "base", request.baseBranch()
            ));

            String response = client.post()
                    .uri("/repos/" + ownerRepo + "/pulls")
                    .header("Authorization", "Bearer " + request.token())
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode prJson = objectMapper.readTree(response);
            String url = prJson.path("html_url").asText();
            log.info("Created GitHub PR: {}", url);
            return url;
        } catch (Exception e) {
            log.error("Failed to create GitHub PR", e);
            return null;
        }
    }

    @Override
    public String name() {
        return "github";
    }

    private String extractOwnerRepo(String url) {
        return url.replaceAll("https://github\\.com/", "")
                .replaceAll("\\.git$", "")
                .replaceAll("/$", "");
    }
}
