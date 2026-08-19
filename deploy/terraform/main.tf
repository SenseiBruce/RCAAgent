terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
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

module "network" {
  source = "./modules/network"

  app_name           = var.app_name
  availability_zones = var.availability_zones
  container_port     = var.container_port
}

module "compute" {
  source = "./modules/compute"

  app_name              = var.app_name
  aws_region            = var.aws_region
  container_port        = var.container_port
  cpu                   = var.cpu
  memory                = var.memory
  desired_count         = var.desired_count
  llm_provider          = var.llm_provider
  secret_arn            = var.secret_arn
  vpc_id                = module.network.vpc_id
  public_subnet_ids     = module.network.public_subnet_ids
  alb_security_group_id = module.network.alb_security_group_id
  ecs_security_group_id = module.network.ecs_security_group_id
}
