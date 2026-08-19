# Terraform (optional AWS deploy)

This directory is an **optional** ECS Fargate stack for RCA Agent. Local development uses Docker Compose in the repo root; you do not need AWS to run tests.

## Layout

| File | Purpose |
|------|---------|
| `main.tf` | VPC, ALB, ECS Fargate service |
| `variables.tf` | Region, sizing, image |
| `outputs.tf` | ALB DNS and related outputs |
| `monitoring.tf` | CloudWatch dashboard and alarms |
| `.terraform.lock.hcl` | Pinned provider versions (created by `terraform init`; commit it when you run Terraform locally) |

## Commands

From `infrastructure/terraform`:

```bash
terraform fmt -check
terraform init -backend=false
terraform validate
```

CI job `terraform-validate` runs fmt, validate, and Checkov (high-severity) on every push and pull request.

Remote state and apply are intentionally not wired in CI (no cloud credentials in GitHub Actions).
