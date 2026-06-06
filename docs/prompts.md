# System Prompts Configuration

## Overview

All system prompts used by the RCA Agent are externalized to files under `src/main/resources/prompts/`. 
They can be overridden at runtime via environment variables or by placing custom files on the classpath.

## Prompt Files

| File | Purpose | Env Override |
|------|---------|--------------|
| `prompts/rca-analysis.txt` | Main RCA analysis prompt sent to the LLM | `RCA_PROMPT_PATH` |

## Template Variables

Prompts use `{{variable}}` placeholders that are replaced at runtime:

| Variable | Description |
|----------|-------------|
| `{{issueDescription}}` | User-provided issue description |
| `{{logSummary}}` | Parsed and summarized log entries (errors/warnings) |
| `{{codeContext}}` | Source code snippets at error locations with line markers |
| `{{gitSummary}}` | Recent commits + git blame for referenced files |

## Customization

### Option 1: Override via environment variable

Set `RCA_PROMPT_PATH` to an absolute file path:

```bash
# .env
RCA_PROMPT_PATH=/path/to/my/custom-prompt.txt
```

### Option 2: Override via classpath

Place a file at `prompts/rca-analysis.txt` in a higher-priority classpath location (e.g., `config/` directory next to the JAR).

### Option 3: Edit the default

Modify `src/main/resources/prompts/rca-analysis.txt` directly.

## Default Prompt

```
You are a Root Cause Analysis expert. Analyze the following information and identify the root cause.
Pay special attention to the SOURCE CODE CONTEXT — it shows the actual code at the error locations.
Correlate the code with the git blame to identify who introduced the bug and when.

ISSUE DESCRIPTION:
{{issueDescription}}

{{logSummary}}

{{codeContext}}

{{gitSummary}}

Respond in this exact JSON format:
{
  "rootCause": "Clear explanation of the root cause, referencing specific code lines",
  "severity": "CRITICAL|HIGH|MEDIUM|LOW",
  "evidenceFromLogs": ["relevant log line 1", "relevant log line 2"],
  "recommendations": ["specific fix recommendation referencing the code", "recommendation 2"]
}
```

## Adding New Prompts

To add a new prompt:

1. Create a `.txt` file in `src/main/resources/prompts/`
2. Add a corresponding env variable for override in `RcaProperties`
3. Load it via `PromptService.loadPrompt("filename.txt")`
4. Document it in this file
