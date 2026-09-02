terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.62"
    }
  }
}

provider "aws" {
  region = var.aws_region

  # CI runs `terraform plan` without AWS credentials.
  skip_credentials_validation = true
  skip_requesting_account_id  = true
  skip_metadata_api_check     = true
  skip_region_validation      = true

  default_tags {
    tags = {
      Project     = var.app_name
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# Reusable stack. External consumers can pin a git ref, for example:
# source = "git::https://github.com/SenseiBruce/RCAAgent.git//deploy/terraform/modules/rca-service?ref=v1.0.0"
module "rca_service" {
  source = "./modules/rca-service"

  app_name           = var.app_name
  aws_region         = var.aws_region
  container_port     = var.container_port
  cpu                = var.cpu
  memory             = var.memory
  desired_count      = var.desired_count
  llm_provider       = var.llm_provider
  availability_zones = var.availability_zones
  secret_arn         = var.secret_arn
  alarm_sns_arn      = var.alarm_sns_arn
}
