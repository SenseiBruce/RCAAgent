package com.rca.agent.llm;

public interface LlmProvider {

    String analyze(String prompt);

    String name();
}
