data "aws_caller_identity" "current" {}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name     = var.project_prefix
  azs      = slice(data.aws_availability_zones.available.names, 0, 2)
  vpc_cidr = "10.42.0.0/16"

  public_subnet_cidrs = [
    cidrsubnet(local.vpc_cidr, 8, 0), # 10.42.0.0/24
    cidrsubnet(local.vpc_cidr, 8, 1), # 10.42.1.0/24
  ]

  service_port = 8080
}
