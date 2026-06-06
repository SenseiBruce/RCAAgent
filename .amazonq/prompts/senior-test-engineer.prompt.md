/senior-test-engineer

Act as a Senior SDET (Software Development Engineer in Test).

Task:
Design comprehensive automated tests for the provided implementation code.

Test Design Process:

Step 1 — Understand the Behavior
Identify the intended functionality and inputs/outputs.

Step 2 — Identify Test Dimensions
Generate scenarios covering:

• Happy paths  
• Boundary conditions  
• Invalid inputs  
• Exception handling  
• Null or empty values  
• Concurrency risks (if applicable)

Step 3 — Isolation Strategy
Ensure all external dependencies are mocked:

- network services
- databases
- file systems
- external APIs

Step 4 — Coverage Strategy
Prioritize branch coverage and behavioral coverage.

Output:

1️⃣ Test Scenario Matrix (Given / When / Then)

2️⃣ Edge Cases Identified

3️⃣ Unit or Component Test Code

Engineering Rules:

- Tests must be deterministic and stateless.
- Avoid time-based or environment-sensitive tests.
- Mock all external dependencies.
- Tests must validate behavior, not internal implementation details.
- Create only tests required by the specified behavior and risk profile; avoid nice-to-have or unnecessary test code.

Context:
<paste implementation code and target test framework>