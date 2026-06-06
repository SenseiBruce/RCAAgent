package com.rca.agent.analyzer.log;

import java.util.List;

public interface LogParser {

    boolean canParse(String content);

    List<LogEntry> parse(String content);
}
