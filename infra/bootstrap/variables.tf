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

variable "allowed_subjects" {
  description = <<-EOT
    GitHub OIDC subject patterns allowed to assume the CI role.
    Common patterns:
      repo:OWNER/REPO:ref:refs/heads/*     -> any branch push / workflow_dispatch
      repo:OWNER/REPO:pull_request         -> any pull_request event
      repo:OWNER/REPO:ref:refs/tags/*      -> any tag push
    Leave OWNER/REPO as literal placeholders; they're substituted at plan time.
  EOT
  type        = list(string)
  default = [
    "ref:refs/heads/*",
    "pull_request"
  ]
}
