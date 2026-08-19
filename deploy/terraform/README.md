# Optional AWS deploy (not the product)

This Terraform is **optional deployment tooling** for the Java/Spring Boot RCA Agent. Local development, tests, and CI `make test` do not use AWS. The primary codebase is `src/main/java`.

## Layout

| Path | Purpose |
|------|---------|
| `main.tf` | Provider and `rca-service` module wiring |
| `backend.tf` | Encrypted S3 remote state with DynamoDB locking |
| `modules/rca-service` | Reusable stack (network + compute + monitoring) |
| `modules/network` | VPC, subnets, routing, security groups |
| `modules/compute` | ECR, ALB, ECS Fargate, IAM, logs |
| `variables.tf` / `outputs.tf` | Region, sizing, image, ALB DNS |
| `.terraform.lock.hcl` | Pinned AWS provider version |

## Remote state

`backend.tf` uses an **encrypted S3 backend** (`encrypt = true`) and a DynamoDB lock table.

Required backend values (edit `backend.tf` before the first apply):

| Argument | Purpose |
|----------|---------|
| `bucket` | S3 bucket name (`rca-agent-tfstate` is a placeholder) |
| `key` | State object key |
| `region` | Bucket region |
| `dynamodb_table` | Lock table (`rca-agent-tflock` is a placeholder) |
| `encrypt` | Must remain `true` |

GitHub Actions never applies. CI moves `backend.tf` aside, then runs fmt, `terraform init`, validate, plan, Checkov, and tfsec against a local backend. Failures block the job (`continue-on-error` is not set; Checkov/tfsec use `soft_fail: false`).

To consume the reusable module from another stack and pin a version:

```hcl
module "rca_service" {
  source = "git::https://github.com/SenseiBruce/RCAAgent.git//deploy/terraform/modules/rca-service?ref=v1.0.0"
  # ... required variables from modules/rca-service/variables.tf
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
