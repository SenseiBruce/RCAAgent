# Remote encrypted state. Replace bucket/table names before the first apply.
# Required backend values:
#   bucket         — S3 bucket for state (SSE enabled via encrypt = true)
#   key            — object key for this stack
#   region         — AWS region of the bucket
#   dynamodb_table — DynamoDB table for state locking
#   encrypt        — must stay true
#
# CI temporarily moves this file aside so `terraform plan` can run without AWS.
terraform {
  backend "s3" {
    bucket         = "rca-agent-tfstate"
    key            = "deploy/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "rca-agent-tflock"
  }
}
