package com.rca.agent.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RcaPropertiesTest {

    @Test
    void defaults_areSetCorrectly() {
        RcaProperties props = new RcaProperties();

        assertThat(props.getLlm().getProvider()).isEqualTo("openrouter");
        assertThat(props.getLlm().getBedrock().getRegion()).isEqualTo("us-east-1");
        assertThat(props.getLlm().getBedrock().getModelId()).isEqualTo("anthropic.claude-3-sonnet-20240229-v1:0");
        assertThat(props.getLlm().getOpenai().getModel()).isEqualTo("gpt-4");
        assertThat(props.getLlm().getOpenai().getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(props.getLlm().getOpenrouter().getModel()).isEqualTo("anthropic/claude-3.5-sonnet");
        assertThat(props.getLlm().getOpenrouter().getBaseUrl()).isEqualTo("https://openrouter.ai/api/v1");
        assertThat(props.getGit().getDefaultBranch()).isEqualTo("main");
        assertThat(props.getGit().getMaxCommits()).isEqualTo(50);
        assertThat(props.getLog().getMaxFileSizeMb()).isEqualTo(100);
    }

    @Test
    void setters_workCorrectly() {
        RcaProperties props = new RcaProperties();

        props.getLlm().setProvider("openai");
        assertThat(props.getLlm().getProvider()).isEqualTo("openai");

        props.getLlm().getBedrock().setRegion("eu-west-1");
        assertThat(props.getLlm().getBedrock().getRegion()).isEqualTo("eu-west-1");

        props.getLlm().getBedrock().setModelId("new-model");
        assertThat(props.getLlm().getBedrock().getModelId()).isEqualTo("new-model");

        props.getLlm().getOpenai().setApiKey("key123");
        assertThat(props.getLlm().getOpenai().getApiKey()).isEqualTo("key123");

        props.getLlm().getOpenai().setModel("gpt-3.5");
        assertThat(props.getLlm().getOpenai().getModel()).isEqualTo("gpt-3.5");

        props.getLlm().getOpenai().setBaseUrl("http://custom");
        assertThat(props.getLlm().getOpenai().getBaseUrl()).isEqualTo("http://custom");

        props.getLlm().getOpenrouter().setApiKey("or-key");
        assertThat(props.getLlm().getOpenrouter().getApiKey()).isEqualTo("or-key");

        props.getLlm().getOpenrouter().setModel("meta/llama");
        assertThat(props.getLlm().getOpenrouter().getModel()).isEqualTo("meta/llama");

        props.getLlm().getOpenrouter().setBaseUrl("http://custom-or");
        assertThat(props.getLlm().getOpenrouter().getBaseUrl()).isEqualTo("http://custom-or");

        props.getGit().setDefaultBranch("develop");
        assertThat(props.getGit().getDefaultBranch()).isEqualTo("develop");

        props.getGit().setMaxCommits(100);
        assertThat(props.getGit().getMaxCommits()).isEqualTo(100);

        props.getLog().setMaxFileSizeMb(500);
        assertThat(props.getLog().getMaxFileSizeMb()).isEqualTo(500);
    }

    @Test
    void topLevelSetters_workCorrectly() {
        RcaProperties props = new RcaProperties();

        var newLlm = new RcaProperties.LlmProperties();
        newLlm.setProvider("bedrock");
        props.setLlm(newLlm);
        assertThat(props.getLlm().getProvider()).isEqualTo("bedrock");

        var newGit = new RcaProperties.GitProperties();
        newGit.setDefaultBranch("release");
        props.setGit(newGit);
        assertThat(props.getGit().getDefaultBranch()).isEqualTo("release");

        var newLog = new RcaProperties.LogProperties();
        newLog.setMaxFileSizeMb(200);
        props.setLog(newLog);
        assertThat(props.getLog().getMaxFileSizeMb()).isEqualTo(200);
    }

    @Test
    void llmSetters_forNestedObjects() {
        RcaProperties props = new RcaProperties();

        var newBedrock = new RcaProperties.BedrockProperties();
        newBedrock.setRegion("ap-southeast-1");
        props.getLlm().setBedrock(newBedrock);
        assertThat(props.getLlm().getBedrock().getRegion()).isEqualTo("ap-southeast-1");

        var newOpenai = new RcaProperties.OpenAiProperties();
        newOpenai.setModel("gpt-4o");
        props.getLlm().setOpenai(newOpenai);
        assertThat(props.getLlm().getOpenai().getModel()).isEqualTo("gpt-4o");

        var newOpenrouter = new RcaProperties.OpenRouterProperties();
        newOpenrouter.setModel("google/gemini");
        props.getLlm().setOpenrouter(newOpenrouter);
        assertThat(props.getLlm().getOpenrouter().getModel()).isEqualTo("google/gemini");
    }

    @Test
    void apiKey_defaultsToNull() {
        RcaProperties props = new RcaProperties();
        assertThat(props.getLlm().getOpenai().getApiKey()).isNull();
        assertThat(props.getLlm().getOpenrouter().getApiKey()).isNull();
    }
}
