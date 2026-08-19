output "alb_url" {
  description = "Application Load Balancer URL"
  value       = "http://${module.compute.alb_dns_name}"
}

output "ecr_repository_url" {
  description = "ECR repository URL for pushing images"
  value       = module.compute.ecr_repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = module.compute.ecs_cluster_name
}

output "ecs_service_name" {
  description = "ECS service name"
  value       = module.compute.ecs_service_name
}
