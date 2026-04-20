variable "region" {
  description = "AWS region hosting the bootstrap resources."
  type        = string
  default     = "us-east-2"
}

variable "project_prefix" {
  description = "Lowercase prefix used to name resources (must be globally unique for S3)."
  type        = string
  default     = "clara-amaro"
}

variable "github_owner" {
  description = "GitHub org/user that owns the repo allowed to assume the CI role."
  type        = string
  default     = "amaro-coria"
}

variable "github_repo" {
  description = "GitHub repo name that can assume the CI role."
  type        = string
  default     = "document-management-service-challenge"
}

variable "allowed_branches" {
  description = "Git refs permitted to assume the CI role (e.g. refs/heads/develop)."
  type        = list(string)
  default = [
    "refs/heads/develop",
    "refs/heads/main",
    "refs/heads/feature/iac",
    "refs/pull/*"
  ]
}
