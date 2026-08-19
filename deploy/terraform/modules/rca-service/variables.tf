variable "aws_region" {
  type = string
}

variable "app_name" {
  type = string
}

variable "container_port" {
  type = number
}

variable "cpu" {
  type = number
}

variable "memory" {
  type = number
}

variable "desired_count" {
  type = number
}

variable "llm_provider" {
  type = string
}

variable "availability_zones" {
  type = list(string)
}

variable "secret_arn" {
  type = string
}

variable "alarm_sns_arn" {
  type    = string
  default = ""
}
