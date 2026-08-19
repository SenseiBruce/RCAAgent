# Optional AWS deploy (not the product)

This Terraform is **optional deployment tooling** for the Java/Spring Boot RCA Agent. Local development, tests, and CI `make test` do not use AWS. The primary codebase is `src/main/java`.

## Layout

| File | Purpose |
|------|---------|
| `main.tf` | VPC, ALB, ECS Fargate service |
| `variables.tf` | Region, sizing, image |
| `outputs.tf` | ALB DNS and related outputs |
| `monitoring.tf` | CloudWatch dashboard and alarms |
| `.terraform.lock.hcl` | Pinned AWS provider version |

## Commands

From `deploy/terraform`:

```bash
terraform fmt -check
terraform init -backend=false
terraform validate
terraform plan -input=false -lock=false -refresh=false
```

CI runs fmt, validate, `terraform plan` (dummy AWS env, no apply), Checkov, and tfsec.

Remote state and `terraform apply` are not run in GitHub Actions (no cloud credentials).
