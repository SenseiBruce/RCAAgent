# Optional AWS deploy (not the product)

This Terraform is **optional deployment tooling** for the Java/Spring Boot RCA Agent. Local development, tests, and CI `make test` do not use AWS. The primary codebase is `src/main/java`.

## Layout

| Path | Purpose |
|------|---------|
| `main.tf` | Provider and module wiring |
| `backend.tf.example` | Encrypted S3 remote state (copy to `backend.tf` before apply) |
| `modules/network` | VPC, subnets, routing, security groups |
| `modules/compute` | ECR, ALB, ECS Fargate, IAM, logs |
| `monitoring.tf` | CloudWatch dashboard and alarms |
| `variables.tf` / `outputs.tf` | Region, sizing, image, ALB DNS |
| `.terraform.lock.hcl` | Pinned AWS provider version |

## Remote state

Production apply uses an **encrypted S3 backend** (`encrypt = true`). Copy `backend.tf.example` to `backend.tf` and replace the placeholder bucket `rca-agent-tfstate` before the first apply.

GitHub Actions never applies and never writes remote state: CI does **not** copy `backend.tf.example`, then runs `terraform init -backend=false`, fmt, validate, plan, Checkov, and tfsec. Failures block the job (`continue-on-error` is not set; Checkov/tfsec use `soft_fail: false`).

## Commands

From `deploy/terraform`:

```bash
terraform fmt -check
terraform init -backend=false
terraform validate
terraform plan -input=false -lock=false -refresh=false
```
