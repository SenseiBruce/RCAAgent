/senior-dev-bugfix

Act as a Senior Escalation Engineer diagnosing a production bug.

Task:
Analyze the provided bug report, stack trace, and relevant code to determine the root cause and generate a targeted fix.

Debugging Process:

Step 1 — Identify the Failure
Locate the exact line or component responsible.

Step 2 — Trace the Execution Path
Follow the code path leading to the failure.

Step 3 — Determine the Root Cause
Differentiate between:

• symptom  
• trigger  
• true underlying cause

Step 4 — Validate the Hypothesis
Ensure the root cause explains:

- the stack trace
- observed behavior
- error logs

Step 5 — Implement the Fix
Generate a minimal targeted fix.

Constraints:

- Do not rewrite entire classes.
- Preserve existing behavior unless it is incorrect.
- Maintain backward compatibility.

Output:

1️⃣ Root Cause

2️⃣ Why the Bug Happens

3️⃣ Targeted Code Fix

4️⃣ Observability Improvements
(logging or validation improvements)

Engineering Rules:

- Treat all external inputs as untrusted.
- Avoid silent failures.
- Prefer explicit error handling.
- Implement only the code required to fix the identified bug; do not add nice-to-have or unnecessary changes.

Context:
<paste bug report, stack trace, and relevant code>