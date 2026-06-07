# RCA Agent Chat — Test Scenarios (Real Repo)

These scenarios use actual file paths and class names from this repo so
the auto-fix feature can locate and patch the real files.

---

## Scenario 1: OpenRouter API Rate Limit (LLM Provider)

```
The RCA agent is failing intermittently with 429 errors when calling the LLM. Repo: https://github.com/SenseiBruce/RCAAgent branch feat/externalize-prompts

2026-06-07T10:00:01.000Z ERROR [http-nio-8080-exec-3] com.rca.agent.llm.OpenRouterLlmProvider - OpenRouter API call failed
org.springframework.web.reactive.function.client.WebClientResponseException$TooManyRequests: 429 Too Many Requests
    at org.springframework.web.reactive.function.client.WebClientResponseException.create(WebClientResponseException.java:201)
    at com.rca.agent.llm.OpenRouterLlmProvider.analyze(OpenRouterLlmProvider.java:58)
    at com.rca.agent.service.RcaService.doAnalyze(RcaService.java:103)
2026-06-07T10:00:02.000Z ERROR [http-nio-8080-exec-5] com.rca.agent.llm.OpenRouterLlmProvider - OpenRouter API call failed
org.springframework.web.reactive.function.client.WebClientResponseException$TooManyRequests: 429 Too Many Requests
    at com.rca.agent.llm.OpenRouterLlmProvider.analyze(OpenRouterLlmProvider.java:58)
2026-06-07T10:00:03.000Z INFO  [http-nio-8080-exec-7] com.rca.agent.service.RcaService - Starting RCA analysis for issue: login broken
```

**Expected:** Identifies missing retry/backoff in OpenRouterLlmProvider.analyze(). Auto-fix can modify the real file.

---

## Scenario 2: Git Clone Timeout (RepoResolver)

```
RCA analysis hangs and eventually times out when users provide repo URLs. Repo: https://github.com/SenseiBruce/RCAAgent branch feat/externalize-prompts

2026-06-07T14:30:00.000Z INFO  [http-nio-8080-exec-1] com.rca.agent.analyzer.git.RepoResolver - Cloning remote repo https://github.com/large-org/monorepo.git to /tmp/rca-repo-123
2026-06-07T14:31:00.000Z ERROR [http-nio-8080-exec-1] com.rca.agent.analyzer.git.RepoResolver - Clone failed
org.eclipse.jgit.api.errors.TransportException: https://github.com/large-org/monorepo.git: Connection timed out after 60000ms
    at org.eclipse.jgit.api.CloneCommand.call(CloneCommand.java:200)
    at com.rca.agent.analyzer.git.RepoResolver.cloneRemote(RepoResolver.java:97)
    at com.rca.agent.analyzer.git.RepoResolver.resolve(RepoResolver.java:62)
    at com.rca.agent.service.RcaService.resolveRepo(RcaService.java:110)
2026-06-07T14:31:00.100Z ERROR [http-nio-8080-exec-1] com.rca.agent.service.RcaService - Failed to resolve repo: https://github.com/large-org/monorepo.git
```

**Expected:** Identifies timeout in RepoResolver.cloneRemote(). Auto-fix should add better timeout handling or shallow clone depth.

---

## Scenario 3: Log File Too Large (LogAnalyzerService)

```
Users are getting 400 errors when submitting log files for analysis. Repo: https://github.com/SenseiBruce/RCAAgent branch feat/externalize-prompts

2026-06-07T09:15:00.000Z INFO  [http-nio-8080-exec-2] com.rca.agent.controller.RcaController - Received RCA request for issue: app crashing
2026-06-07T09:15:00.100Z ERROR [http-nio-8080-exec-2] com.rca.agent.analyzer.log.LogAnalyzerService - Log file exceeds max size
java.lang.IllegalArgumentException: Log file exceeds max size: 150MB
    at com.rca.agent.analyzer.log.LogAnalyzerService.analyzeFromFile(LogAnalyzerService.java:47)
    at com.rca.agent.service.RcaService.parseLogEntries(RcaService.java:118)
    at com.rca.agent.service.RcaService.doAnalyze(RcaService.java:92)
2026-06-07T09:15:00.200Z WARN  [http-nio-8080-exec-2] c.r.a.controller.GlobalExceptionHandler - IllegalArgumentException: Log file exceeds max size: 150MB
```

**Expected:** Identifies strict file size limit in LogAnalyzerService. Auto-fix could modify the error message or add tail-reading logic.

---

## Scenario 4: NPE in Chat Service (Missing Session)

```
Chat endpoint is returning 500 errors for some users. Repo: https://github.com/SenseiBruce/RCAAgent branch feat/externalize-prompts

2026-06-07T11:00:00.000Z INFO  [http-nio-8080-exec-4] com.rca.agent.chat.ChatController - Received chat request
2026-06-07T11:00:00.050Z ERROR [http-nio-8080-exec-4] com.rca.agent.chat.ChatService - Chat failed
java.lang.NullPointerException: Cannot invoke "java.util.List.add(Object)" because "history" is null
    at com.rca.agent.chat.ChatService.chat(ChatService.java:44)
    at com.rca.agent.chat.ChatController.chat(ChatController.java:20)
2026-06-07T11:00:00.051Z ERROR [http-nio-8080-exec-4] c.r.a.controller.GlobalExceptionHandler - Unhandled exception
```

**Expected:** Identifies potential race condition in ChatService session map. Auto-fix can modify ChatService.java.

---

## Scenario 5: WebClient Timeout on GitHub PR Creation

```
Auto-fix creates the branch but fails when creating the PR. Repo: https://github.com/SenseiBruce/RCAAgent branch feat/externalize-prompts

2026-06-07T16:00:00.000Z INFO  [http-nio-8080-exec-6] com.rca.agent.fix.AutoFixService - Pushed branch fix/rca-123 with 1 file changes
2026-06-07T16:00:00.100Z INFO  [http-nio-8080-exec-6] com.rca.agent.fix.AutoFixService - Creating PR/MR on platform: github
2026-06-07T16:00:30.100Z ERROR [http-nio-8080-exec-6] com.rca.agent.fix.platform.GitHubPlatform - Failed to create GitHub PR
io.netty.channel.ConnectTimeoutException: connection timed out after 30000ms: api.github.com/2606:50c0:8003::6:443
    at io.netty.channel.nio.AbstractNioChannel$AbstractNioUnsafe$1.run(AbstractNioChannel.java:261)
    at com.rca.agent.fix.platform.GitHubPlatform.createPullRequest(GitHubPlatform.java:52)
    at com.rca.agent.fix.AutoFixService.createPrOnPlatform(AutoFixService.java:105)
2026-06-07T16:00:30.200Z ERROR [http-nio-8080-exec-6] com.rca.agent.fix.AutoFixService - Auto-fix failed
```

**Expected:** Identifies missing timeout config and retry in GitHubPlatform. Auto-fix can modify GitHubPlatform.java.

---

## Scenario 6: Prompt Template Missing (PromptService)

```
App fails to start after deployment. Repo: https://github.com/SenseiBruce/RCAAgent branch feat/externalize-prompts

2026-06-07T08:00:00.000Z ERROR [main] com.rca.agent.service.PromptService - Failed to load prompt from classpath: prompts/rca-analysis.txt
java.io.IOException: class path resource [prompts/rca-analysis.txt] cannot be resolved to URL because it does not exist
    at org.springframework.core.io.ClassPathResource.getURL(ClassPathResource.java:195)
    at com.rca.agent.service.PromptService.loadPrompt(PromptService.java:62)
    at com.rca.agent.service.PromptService.<init>(PromptService.java:30)
2026-06-07T08:00:00.100Z ERROR [main] o.s.boot.SpringApplication - Application run failed
java.lang.IllegalStateException: Cannot load prompt: rca-analysis.txt
    at com.rca.agent.service.PromptService.loadPrompt(PromptService.java:64)
```

**Expected:** Identifies missing prompt template file. Auto-fix could add a fallback default prompt in PromptService.java.

---

## Scenario 7: Metrics Registration Failure (RcaService)

```
App crashes on startup with bean creation error. Repo: https://github.com/SenseiBruce/RCAAgent branch feat/externalize-prompts

2026-06-07T07:00:00.000Z ERROR [main] o.s.boot.SpringApplication - Application run failed
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'rcaService': java.lang.NullPointerException
    at org.springframework.beans.factory.support.ConstructorResolver.createArgumentArray(ConstructorResolver.java:800)
Caused by: java.lang.NullPointerException: Cannot invoke "io.micrometer.core.instrument.MeterRegistry.register()" because "meterRegistry" is null
    at com.rca.agent.service.RcaService.<init>(RcaService.java:60)
    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
2026-06-07T07:00:00.100Z INFO  [main] o.s.boot.SpringApplication - Application shutdown
```

**Expected:** Identifies null MeterRegistry injection in RcaService constructor. Auto-fix can add null check in RcaService.java.

---

## Scenario 8: Time Window Parse Failure

```
Analysis works but time-window filtering returns no commits even though there are recent commits. Repo: https://github.com/SenseiBruce/RCAAgent branch feat/externalize-prompts

2026-06-07T13:00:00.000Z INFO  [http-nio-8080-exec-1] com.rca.agent.service.RcaService - Starting RCA analysis for issue: deployment failure
2026-06-07T13:00:01.000Z INFO  [http-nio-8080-exec-1] com.rca.agent.analyzer.git.GitAnalyzerService - Retrieved 0 commits for branch main with time window
2026-06-07T13:00:01.000Z WARN  [http-nio-8080-exec-1] com.rca.agent.analyzer.git.TimeWindowParser - Failed to parse time window: "last 2 hours"
2026-06-07T13:00:01.100Z INFO  [http-nio-8080-exec-1] com.rca.agent.service.RcaService - RCA analysis complete using provider: openrouter
```

**Expected:** Identifies that TimeWindowParser doesn't support "hours" (only "h"). Auto-fix can modify TimeWindowParser.java to accept longer unit names.

---

## Tips for Testing Auto-Fix

- These scenarios all reference **real files** in this repo
- The LLM will generate search/replace patches targeting actual code
- Use Scenario 1 or 4 for the best chance of a working auto-fix PR
- You need to provide a GitHub PAT with `repo` scope when prompted
- The fix branch is created from whatever branch the repo was cloned at
