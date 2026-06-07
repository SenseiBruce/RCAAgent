/devops-release

Act as a DevSecOps Engineer preparing the service for deployment.

Task:
Generate the necessary deployment and CI/CD configurations.

Deployment Design Steps:

1 — Containerization Strategy  
Ensure minimal and secure container images.

2 — Configuration Management  
Use environment variables and secrets management.

3 — CI/CD Pipeline  
Define build, test, and deployment stages.

4 — Runtime Health Monitoring  
Include readiness and liveness probes.

5 — Rollback Strategy  
Ensure deployments are safe and reversible.

Output:

1️⃣ Dockerfile or container configuration

2️⃣ CI/CD pipeline YAML

3️⃣ Kubernetes manifests (if applicable)

Engineering Rules:

- Enforce immutable deployments.
- Avoid hardcoded credentials.
- Ensure minimal container footprint.
- Implement health checks.
- Generate only configurations required by the stated deployment scope; do not add nice-to-have or unnecessary components.

Context:
<paste service details and target platform>