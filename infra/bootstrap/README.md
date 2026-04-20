# Bootstrap — one-time setup

Creates the AWS primitives the rest of the pipeline depends on:

- **S3 bucket + DynamoDB table** for Terraform remote state (used by `infra/terraform/`).
- **GitHub Actions OIDC provider + IAM role** so workflows can assume AWS permissions without stored secrets.
- **ECR repository** for the service Docker image.

Run this **once** from a workstation that has admin-level AWS credentials. After it finishes, CI takes over — you will not need local AWS credentials again.

## Prerequisites

- Terraform ≥ 1.6
- AWS CLI configured (`aws sts get-caller-identity` should return your account)
- IAM permissions: `AdministratorAccess` recommended for the bootstrap user. You can downgrade after.

## Run

```bash
cd infra/bootstrap
terraform init
terraform apply
```

Review the plan carefully and confirm with `yes`.

## After apply

Terraform prints a `next_steps` output with the exact values to paste into the GitHub repo as **Repository variables** (Settings → Secrets and variables → Actions → *Variables* tab, **not** Secrets):

| Variable              | Source                                    |
|-----------------------|-------------------------------------------|
| `AWS_ACCOUNT_ID`      | output `aws_account_id`                   |
| `AWS_REGION`          | output `region`                           |
| `AWS_ROLE_ARN`        | output `github_actions_role_arn`          |
| `ECR_REPOSITORY`      | output `ecr_repository_url`               |
| `TF_STATE_BUCKET`     | output `tf_state_bucket`                  |
| `TF_STATE_LOCK_TABLE` | output `tf_state_lock_table`              |
| `PROJECT_PREFIX`      | matches `var.project_prefix` (`clara-amaro` by default) |

## Destroying the bootstrap

You rarely want to do this — the state bucket holds the main stack's state. If you truly want to start over:

```bash
# First destroy the main stack in infra/terraform/ (see its README)
cd infra/bootstrap
terraform destroy
```

`force_destroy = true` on the ECR repo lets it clean up even with images present. The S3 state bucket will refuse to destroy if it still has state files inside; empty it first or pass `-var 'force_destroy_state=true'` after adding that option if you want that escape hatch.
