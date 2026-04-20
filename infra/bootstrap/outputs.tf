output "aws_account_id" {
  value = data.aws_caller_identity.current.account_id
}

output "region" {
  value = var.region
}

output "tf_state_bucket" {
  description = "S3 bucket that stores the main stack's Terraform state."
  value       = aws_s3_bucket.tf_state.bucket
}

output "tf_state_lock_table" {
  description = "DynamoDB table used for state locking."
  value       = aws_dynamodb_table.tf_state_lock.name
}

output "github_actions_role_arn" {
  description = "Paste this as the AWS_ROLE_ARN repo variable in GitHub."
  value       = aws_iam_role.ci.arn
}

output "ecr_repository_url" {
  description = "Paste this as the ECR_REPOSITORY repo variable in GitHub."
  value       = aws_ecr_repository.service.repository_url
}

output "next_steps" {
  value = <<-EOT

    Bootstrap complete.

    Add the following as GitHub Actions "Repository variables"
    (Settings → Secrets and variables → Actions → Variables tab):

      AWS_ACCOUNT_ID      = ${data.aws_caller_identity.current.account_id}
      AWS_REGION          = ${var.region}
      AWS_ROLE_ARN        = ${aws_iam_role.ci.arn}
      ECR_REPOSITORY      = ${aws_ecr_repository.service.repository_url}
      TF_STATE_BUCKET     = ${aws_s3_bucket.tf_state.id}
      TF_STATE_LOCK_TABLE = ${aws_dynamodb_table.tf_state_lock.name}
      PROJECT_PREFIX      = ${var.project_prefix}

    After that, merge the upcoming `infra/terraform/` PR and run the
    `deploy` workflow from the Actions tab to spin up the live stack.
  EOT
}
