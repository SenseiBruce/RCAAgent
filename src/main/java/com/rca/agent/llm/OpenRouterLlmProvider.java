package com.rca.agent.llm;

import com.rca.agent.config.RcaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@ConditionalOnProperty(name = "rca.llm.provider", havingValue = "openrouter")
public class OpenRouterLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterLlmProvider.class);
    private final WebClient webClient;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenRouterLlmProvider(RcaProperties properties) {
        var openRouterProps = properties.getLlm().getOpenrouter();
        this.model = openRouterProps.getModel();
        this.webClient = WebClient.builder()
                .baseUrl(openRouterProps.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + openRouterProps.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("Initialized OpenRouter LLM provider with model: {}", model);
    }

    @Override
    public String analyze(String prompt) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode message = messages.addObject();
            message.put("role", "user");
            message.put("content", prompt);
            requestBody.put("max_tokens", 4096);

            String response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(objectMapper.writeValueAsString(requestBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode responseJson = objectMapper.readTree(response);
            return responseJson.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("OpenRouter API call failed", e);
            throw new RuntimeException("LLM analysis failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String name() {
        return "openrouter";
    }
}
