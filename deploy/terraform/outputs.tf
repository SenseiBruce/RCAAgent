output "alb_url" {
  description = "Application Load Balancer URL"
  value       = "http://${module.rca_service.alb_dns_name}"
}

output "ecr_repository_url" {
  description = "ECR repository URL for pushing images"
  value       = module.rca_service.ecr_repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = module.rca_service.ecs_cluster_name
}

output "ecs_service_name" {
  description = "ECS service name"
  value       = module.rca_service.ecs_service_name
}
