# Remote encrypted state. Bucket, key, region, and lock table are not hardcoded:
# pass them with `terraform init -backend-config=backend.hcl` (see backend.hcl.example).
# CI moves this file aside so plan can run without AWS.
terraform {
  backend "s3" {
    encrypt = true
  }
}
