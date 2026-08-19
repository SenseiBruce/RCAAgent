# Optional AWS deploy (not the product)

This Terraform is **optional deployment tooling** for the Java/Spring Boot RCA Agent. Local development, tests, and CI `make test` do not use AWS. The primary codebase is `src/main/java`.

## Layout

| Path | Purpose |
|------|---------|
| `main.tf` | Provider and `rca-service` module wiring |
| `backend.tf` | Encrypted S3 backend (`encrypt = true`); remaining settings via `-backend-config` |
| `backend.hcl.example` | Placeholder bucket, key, region, DynamoDB lock table |
| `modules/rca-service` | Reusable stack (network + compute + monitoring); `versions.tf` pins AWS `~> 5.100.0` |
| `modules/network` | VPC, subnets, routing, security groups |
| `modules/compute` | ECR, ALB, ECS Fargate, IAM, logs |
| `variables.tf` / `outputs.tf` | Region, sizing, image, ALB DNS |
| `.terraform.lock.hcl` | Pinned AWS provider version |

## Remote state

`backend.tf` declares an **encrypted S3 backend** (`encrypt = true`). Bucket, key, region, and DynamoDB lock table are **not hardcoded**; copy `backend.hcl.example` to `backend.hcl` and pass it at init:

```bash
cp backend.hcl.example backend.hcl   # edit placeholders
terraform init -backend-config=backend.hcl
```

| Argument | Purpose |
|----------|---------|
| `bucket` | S3 bucket name |
| `key` | State object key |
| `region` | Bucket region |
| `dynamodb_table` | Lock table |
| `encrypt` | Must remain `true` (also set in `backend.tf`) |

GitHub Actions never applies. CI moves `backend.tf` aside, then runs fmt, `terraform init`, validate, plan, Checkov, and tfsec against a local backend. Failures block the job (`continue-on-error` is not set; Checkov/tfsec use `soft_fail: false`).

To consume the reusable module from another stack and pin a git tag:

```hcl
module "rca_service" {
  source = "git::https://github.com/SenseiBruce/RCAAgent.git//deploy/terraform/modules/rca-service?ref=v1.0.0"
}
```

## Commands

From `deploy/terraform`:

```bash
terraform fmt -check -recursive
# CI equivalent: mv backend.tf backend.tf.remote && terraform init
terraform init -backend=false
terraform validate
terraform plan -input=false -lock=false -refresh=false
```
