package com.rca.agent.llm;

import com.rca.agent.config.RcaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * LLM provider implementation for AWS Bedrock.
 * <p>
 * Integrates with Amazon Bedrock's InvokeModel API using Claude's Messages format.
 * Activated when {@code rca.llm.provider=bedrock} (default).
 * Requires valid AWS credentials configured via the default credential provider chain.
 */
@Component
@ConditionalOnProperty(name = "rca.llm.provider", havingValue = "bedrock", matchIfMissing = true)
public class BedrockLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(BedrockLlmProvider.class);
    private final BedrockRuntimeClient client;
    private final String modelId;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BedrockLlmProvider(RcaProperties properties) {
        var bedrockProps = properties.getLlm().getBedrock();
        this.modelId = bedrockProps.getModelId();
        this.client = BedrockRuntimeClient.builder()
                .region(Region.of(bedrockProps.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        log.info("Initialized Bedrock LLM provider with model: {}", modelId);
    }

    @Override
    public String analyze(String prompt) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("anthropic_version", "bedrock-2023-05-31");
            requestBody.put("max_tokens", 4096);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode message = messages.addObject();
            message.put("role", "user");
            ArrayNode content = message.putArray("content");
            ObjectNode textContent = content.addObject();
            textContent.put("type", "text");
            textContent.put("text", prompt);

            InvokeModelResponse response = client.invokeModel(InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .body(SdkBytes.fromUtf8String(objectMapper.writeValueAsString(requestBody)))
                    .build());

            JsonNode responseJson = objectMapper.readTree(response.body().asUtf8String());
            return responseJson.path("content").get(0).path("text").asText();
        } catch (Exception e) {
            log.error("Bedrock API call failed", e);
            throw new RuntimeException("LLM analysis failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String name() {
        return "bedrock";
    }
}
