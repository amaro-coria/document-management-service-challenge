########################################
# ECR repository for the service image
########################################

resource "aws_ecr_repository" "service" {
  name                 = "${var.project_prefix}-service"
  image_tag_mutability = "MUTABLE"
  force_delete         = true # POC: allow `terraform destroy` to clean up.

  image_scanning_configuration {
    scan_on_push = true
  }
}

# Keep only the 10 most recent images to control storage cost.
resource "aws_ecr_lifecycle_policy" "service" {
  repository = aws_ecr_repository.service.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep only the 10 most recent images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
