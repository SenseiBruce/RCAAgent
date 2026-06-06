package com.rca.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rca")
public class RcaProperties {

    private LlmProperties llm = new LlmProperties();
    private GitProperties git = new GitProperties();
    private LogProperties log = new LogProperties();

    public LlmProperties getLlm() { return llm; }
    public void setLlm(LlmProperties llm) { this.llm = llm; }
    public GitProperties getGit() { return git; }
    public void setGit(GitProperties git) { this.git = git; }
    public LogProperties getLog() { return log; }
    public void setLog(LogProperties log) { this.log = log; }

    public static class LlmProperties {
        private String provider = "openrouter";
        private BedrockProperties bedrock = new BedrockProperties();
        private OpenAiProperties openai = new OpenAiProperties();
        private OpenRouterProperties openrouter = new OpenRouterProperties();

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public BedrockProperties getBedrock() { return bedrock; }
        public void setBedrock(BedrockProperties bedrock) { this.bedrock = bedrock; }
        public OpenAiProperties getOpenai() { return openai; }
        public void setOpenai(OpenAiProperties openai) { this.openai = openai; }
        public OpenRouterProperties getOpenrouter() { return openrouter; }
        public void setOpenrouter(OpenRouterProperties openrouter) { this.openrouter = openrouter; }
    }

    public static class BedrockProperties {
        private String region = "us-east-1";
        private String modelId = "anthropic.claude-3-sonnet-20240229-v1:0";

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getModelId() { return modelId; }
        public void setModelId(String modelId) { this.modelId = modelId; }
    }

    public static class OpenAiProperties {
        private String apiKey;
        private String model = "gpt-4";
        private String baseUrl = "https://api.openai.com/v1";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class OpenRouterProperties {
        private String apiKey;
        private String model = "anthropic/claude-3.5-sonnet";
        private String baseUrl = "https://openrouter.ai/api/v1";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class GitProperties {
        private String defaultBranch = "main";
        private int maxCommits = 50;

        public String getDefaultBranch() { return defaultBranch; }
        public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
        public int getMaxCommits() { return maxCommits; }
        public void setMaxCommits(int maxCommits) { this.maxCommits = maxCommits; }
    }

    public static class LogProperties {
        private int maxFileSizeMb = 100;

        public int getMaxFileSizeMb() { return maxFileSizeMb; }
        public void setMaxFileSizeMb(int maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }
    }
}
