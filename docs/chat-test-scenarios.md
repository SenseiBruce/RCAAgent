# RCA Agent Chat — Test Scenarios

## Scenario 1: Simple NPE (Logs Only)

**Paste this as your first message:**

```
Our payment service is failing with 500 errors since this morning. Here are the logs:

2026-06-07T10:15:22.100Z ERROR [http-nio-8080-exec-1] com.acme.payment.PaymentService - Failed to process payment
java.lang.NullPointerException: Cannot invoke "com.acme.payment.model.User.getWalletId()" because "user" is null
    at com.acme.payment.PaymentService.processPayment(PaymentService.java:87)
    at com.acme.payment.PaymentController.pay(PaymentController.java:34)
2026-06-07T10:15:22.200Z WARN  [http-nio-8080-exec-1] com.acme.payment.UserRepository - User lookup returned null for userId=usr_9281
2026-06-07T10:15:23.500Z ERROR [http-nio-8080-exec-3] com.acme.payment.PaymentService - Failed to process payment
java.lang.NullPointerException: Cannot invoke "com.acme.payment.model.User.getWalletId()" because "user" is null
    at com.acme.payment.PaymentService.processPayment(PaymentService.java:87)
2026-06-07T10:15:25.000Z INFO  [scheduling-1] com.acme.payment.HealthCheck - DB connection pool: 48/50 active
```

**Expected:** Bot triggers RCA analysis, identifies NPE due to null user from repository lookup.

---

## Scenario 2: CloudWatch JSON Logs (Lambda Timeout)

**Paste this:**

```
We're getting timeouts on our order processing Lambda. Here are the CloudWatch logs:

{"events": [{"timestamp": 1749278400000, "message": "START RequestId: abc-123 Version: $LATEST", "logStreamName": "2026/06/07/[$LATEST]stream1"}, {"timestamp": 1749278401000, "message": "ERROR: DynamoDB query timed out after 5000ms for table=Orders, key=ord_5523", "logStreamName": "2026/06/07/[$LATEST]stream1"}, {"timestamp": 1749278401500, "message": "WARN: Retry attempt 1/3 for DynamoDB query", "logStreamName": "2026/06/07/[$LATEST]stream1"}, {"timestamp": 1749278406000, "message": "ERROR: All retries exhausted. DynamoDB consistently timing out.", "logStreamName": "2026/06/07/[$LATEST]stream1"}, {"timestamp": 1749278406100, "message": "Task timed out after 15.00 seconds", "logStreamName": "2026/06/07/[$LATEST]stream1"}, {"timestamp": 1749278406200, "message": "END RequestId: abc-123", "logStreamName": "2026/06/07/[$LATEST]stream1"}]}
```

**Expected:** Bot detects CloudWatch format, identifies DynamoDB timeout as root cause.

---

## Scenario 3: With Repo URL (Git Correlation)

**Paste this:**

```
Our API started returning 503 after the last deploy. Repo is https://github.com/SenseiBruce/RCAAgent branch feat/externalize-prompts. Logs:

2026-06-07T14:00:01.000Z ERROR [main] o.s.boot.SpringApplication - Application run failed
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'rcaService': Unsatisfied dependency
    at org.springframework.beans.factory.support.ConstructorResolver.createArgumentArray(ConstructorResolver.java:800)
Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException: No qualifying bean of type 'com.rca.agent.analyzer.git.GitAnalyzerService'
    at org.springframework.beans.factory.support.DefaultListableBeanFactory.raiseNoMatchingBeanFound(DefaultListableBeanFactory.java:1880)
2026-06-07T14:00:01.100Z INFO  [main] o.s.boot.SpringApplication - Application shutdown initiated
```

**Expected:** Bot triggers RCA with git analysis, correlates commits with the bean creation failure.

---

## Scenario 4: Multi-Turn Conversation (Bot Asks Questions)

**Message 1:**
```
Login page is broken
```

**Expected:** Bot asks for more details (logs, repo, time window). Use quick reply buttons.

**Message 2 (after bot asks):**
```
Users see a blank white page. Started happening 2 hours ago after deploy. No error in browser but backend returns 500. Repo: https://github.com/SenseiBruce/RCAAgent
```

**Expected:** Bot now has enough context, triggers analysis.

---

## Scenario 5: Auto-Fix Flow

**After any RCA completes, click "🔧 Auto-fix this issue" button, then when asked for token:**

```
ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

(Replace with a real GitHub PAT with `repo` scope for actual PR creation)

**Expected:** Bot stores token, executes fix, creates PR.

---

## Scenario 6: CloudWatch Insights Format

**Paste this:**

```
Our auth service is rejecting valid tokens. CloudWatch Insights results:

{"results": [[{"field": "@timestamp", "value": "2026-06-07T12:00:01Z"}, {"field": "@message", "value": "ERROR: JWT signature verification failed for kid=key-2026-06"}, {"field": "@logStream", "value": "auth-service/prod/i-0abc123"}], [{"field": "@timestamp", "value": "2026-06-07T12:00:02Z"}, {"field": "@message", "value": "WARN: Falling back to expired key rotation cache"}, {"field": "@logStream", "value": "auth-service/prod/i-0abc123"}], [{"field": "@timestamp", "value": "2026-06-07T12:00:03Z"}, {"field": "@message", "value": "ERROR: Token validation failed - InvalidSignatureException"}, {"field": "@logStream", "value": "auth-service/prod/i-0abc123"}]], "statistics": {"recordsMatched": 847}}
```

**Expected:** Bot detects CloudWatch Insights format, identifies JWT key rotation issue.

---

## Scenario 7: Minimal Input (Quick Test)

**Paste this:**

```
Getting "Cannot read property 'email' of undefined" in browser console after deploying PR #42 yesterday
```

**Expected:** Bot may ask for logs/repo or analyze directly with just the description.

---

## Scenario 8: OOM / Resource Exhaustion

**Paste this:**

```
Production pod keeps getting OOMKilled. Logs from the last crash:

2026-06-07T08:30:00.000Z INFO  [main] c.a.app.CacheService - Loading product catalog into memory...
2026-06-07T08:30:15.000Z INFO  [main] c.a.app.CacheService - Loaded 2,847,291 products (estimated 3.2GB heap usage)
2026-06-07T08:30:15.500Z WARN  [main] c.a.app.CacheService - Heap usage at 87% after catalog load
2026-06-07T08:31:00.000Z ERROR [http-nio-8080-exec-1] c.a.app.SearchService - GC overhead limit exceeded during search query
java.lang.OutOfMemoryError: GC overhead limit exceeded
    at java.util.Arrays.copyOf(Arrays.java:3210)
    at com.acme.app.SearchService.filterProducts(SearchService.java:156)
2026-06-07T08:31:00.100Z ERROR [main] o.s.boot.SpringApplication - Application crashed
java.lang.OutOfMemoryError: Java heap space
```
```
Getting NullPointerException on the /analyze endpoint when no repo path is provided. Repo: https://github.com/SenseiBruce/RCAAgent

2026-06-07T18:00:00.000Z INFO  [http-nio-8080-exec-1] com.rca.agent.controller.RcaController - Received RCA request for issue: app crash
2026-06-07T18:00:00.050Z INFO  [http-nio-8080-exec-1] com.rca.agent.service.RcaService - Starting RCA analysis for issue: app crash
2026-06-07T18:00:00.100Z ERROR [http-nio-8080-exec-1] com.rca.agent.service.RcaService - NPE in doAnalyze
java.lang.NullPointerException: Cannot invoke "String.isBlank()" because the return value of "com.rca.agent.model.RcaRequest.repoPath()" is null
at com.rca.agent.service.RcaService.resolveRepo(RcaService.java:113)
at com.rca.agent.service.RcaService.doAnalyze(RcaService.java:87)
at com.rca.agent.service.RcaService.analyze(RcaService.java:79)
at com.rca.agent.controller.RcaController.analyze(RcaController.java:45)
2026-06-07T18:00:00.101Z ERROR [http-nio-8080-exec-1] c.r.a.controller.GlobalExceptionHandler - Unhandled exception
```
**Expected:** Bot identifies in-memory catalog loading causing OOM, recommends pagination or external cache.

---

## Tips

- You can type freely at any point — the bot responds to natural language
- Click quick reply buttons instead of typing for common actions
- Paste logs directly in the chat — no need for a separate field
- Say "fix it" or click the fix button after RCA to trigger auto-fix
- Provide your GitHub token when asked (starts with `ghp_`)
