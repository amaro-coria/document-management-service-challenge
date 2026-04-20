variable "region" {
  description = "AWS region for all resources."
  type        = string
  default     = "us-east-2"
}

variable "project_prefix" {
  description = "Lowercase prefix applied to every resource name."
  type        = string
  default     = "clara-amaro"
}

variable "image_tag" {
  description = "ECR image tag to deploy (usually the git SHA from CI)."
  type        = string
  default     = "latest"
}

variable "ecr_repository_url" {
  description = "Full ECR repo URI (e.g. 039438368062.dkr.ecr.us-east-2.amazonaws.com/clara-amaro-service)."
  type        = string
}

variable "service_cpu" {
  description = "Fargate task CPU units."
  type        = number
  default     = 512 # 0.5 vCPU
}

variable "service_memory" {
  description = "Fargate task memory (MiB)."
  type        = number
  default     = 1024
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "RDS allocated storage in GiB."
  type        = number
  default     = 20
}
