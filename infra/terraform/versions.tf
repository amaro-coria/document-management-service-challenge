terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Backend is configured via -backend-config flags in CI so values stay in
  # repo variables, not committed to source. See infra/terraform/README.md.
  backend "s3" {}
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project       = "document-management-service"
      ManagedBy     = "terraform"
      TerraformRoot = "main"
    }
  }
}
