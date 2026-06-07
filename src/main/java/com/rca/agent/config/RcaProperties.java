package com.rca.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the RCA Agent.
 * <p>
 * Binds to the {@code rca.*} prefix in application configuration.
 * All values can be overridden via environment variables or {@code .env} file.
 *
 * <pre>
 * rca:
 *   llm:
 *     provider: openrouter
 *   git:
 *     default-branch: main
 *     max-commits: 50
 *   log:
 *     max-file-size-mb: 100
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "rca")
public class RcaProperties {

    private LlmProperties llm = new LlmProperties();
    private GitProperties git = new GitProperties();
    private LogProperties log = new LogProperties();
    private GuardrailsProperties guardrails = new GuardrailsProperties();

    public LlmProperties getLlm() { return llm; }
    public void setLlm(LlmProperties llm) { this.llm = llm; }
    public GitProperties getGit() { return git; }
    public void setGit(GitProperties git) { this.git = git; }
    public LogProperties getLog() { return log; }
    public void setLog(LogProperties log) { this.log = log; }
    public GuardrailsProperties getGuardrails() { return guardrails; }
    public void setGuardrails(GuardrailsProperties guardrails) { this.guardrails = guardrails; }

    public static class LlmProperties {
        private String provider = "openrouter";
        private int maxTokens = 16384;
        private BedrockProperties bedrock = new BedrockProperties();
        private OpenAiProperties openai = new OpenAiProperties();
        private OpenRouterProperties openrouter = new OpenRouterProperties();

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
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
        private String repoUrl;
        private String githubToken;

        public String getDefaultBranch() { return defaultBranch; }
        public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
        public int getMaxCommits() { return maxCommits; }
        public void setMaxCommits(int maxCommits) { this.maxCommits = maxCommits; }
        public String getRepoUrl() { return repoUrl; }
        public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
        public String getGithubToken() { return githubToken; }
        public void setGithubToken(String githubToken) { this.githubToken = githubToken; }
    }

    public static class LogProperties {
        private int maxFileSizeMb = 100;

        public int getMaxFileSizeMb() { return maxFileSizeMb; }
        public void setMaxFileSizeMb(int maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }
    }

    public static class GuardrailsProperties {
        private int maxInputLength = 50000;
        private int maxLogLines = 5000;
        private int maxHistoryMessages = 20;
        private int maxConcurrentAnalyses = 5;
        private String blockedPatterns = "";
        private boolean allowAutoFix = true;
        private boolean requireApprovalForPush = false;
        private int maxFilesPerFix = 10;
        private String allowedRepoPatterns = "";

        public int getMaxInputLength() { return maxInputLength; }
        public void setMaxInputLength(int maxInputLength) { this.maxInputLength = maxInputLength; }
        public int getMaxLogLines() { return maxLogLines; }
        public void setMaxLogLines(int maxLogLines) { this.maxLogLines = maxLogLines; }
        public int getMaxHistoryMessages() { return maxHistoryMessages; }
        public void setMaxHistoryMessages(int maxHistoryMessages) { this.maxHistoryMessages = maxHistoryMessages; }
        public int getMaxConcurrentAnalyses() { return maxConcurrentAnalyses; }
        public void setMaxConcurrentAnalyses(int maxConcurrentAnalyses) { this.maxConcurrentAnalyses = maxConcurrentAnalyses; }
        public String getBlockedPatterns() { return blockedPatterns; }
        public void setBlockedPatterns(String blockedPatterns) { this.blockedPatterns = blockedPatterns; }
        public boolean isAllowAutoFix() { return allowAutoFix; }
        public void setAllowAutoFix(boolean allowAutoFix) { this.allowAutoFix = allowAutoFix; }
        public boolean isRequireApprovalForPush() { return requireApprovalForPush; }
        public void setRequireApprovalForPush(boolean requireApprovalForPush) { this.requireApprovalForPush = requireApprovalForPush; }
        public int getMaxFilesPerFix() { return maxFilesPerFix; }
        public void setMaxFilesPerFix(int maxFilesPerFix) { this.maxFilesPerFix = maxFilesPerFix; }
        public String getAllowedRepoPatterns() { return allowedRepoPatterns; }
        public void setAllowedRepoPatterns(String allowedRepoPatterns) { this.allowedRepoPatterns = allowedRepoPatterns; }
    }
}
