# Main Terraform stack

Provisions the runtime AWS resources for the Document Management Service:

- VPC with 2 public subnets across 2 AZs (no NAT → no $32/mo idle charge).
- Application Load Balancer + target group (HTTP on :80 → container on :8080).
- ECS Fargate cluster, task definition, service (1 task, 0.5 vCPU / 1 GB).
- RDS PostgreSQL 15 (`db.t4g.micro`, single-AZ, no backups — POC).
- S3 bucket for document objects (CORS enabled for direct presigned PUT/GET).
- IAM: task execution role, task role, dedicated IAM user for the MinIO SDK.
- Secrets Manager for the RDS password and S3 user keys.
- CloudWatch log group (`/ecs/<prefix>-service`, 7-day retention).

## Don't run this locally — use CI

The workflows under `.github/workflows/` handle it:

|     Workflow     |               Trigger                |                        What it does                        |
|------------------|--------------------------------------|------------------------------------------------------------|
| `infra-plan.yml` | PR touching `infra/terraform/**`     | `terraform plan`, posts diff as PR comment                 |
| `deploy.yml`     | `workflow_dispatch` (type "deploy")  | Build + push image → `terraform apply` → print service URL |
| `destroy.yml`    | `workflow_dispatch` (type "destroy") | `terraform destroy` (leaves bootstrap resources intact)    |

CI authenticates via GitHub OIDC using the role created by `infra/bootstrap/`. No AWS secrets are stored in GitHub.

## Required GitHub repo variables

|       Variable        |                              Source                               |
|-----------------------|-------------------------------------------------------------------|
| `AWS_ACCOUNT_ID`      | bootstrap output                                                  |
| `AWS_REGION`          | bootstrap output                                                  |
| `AWS_ROLE_ARN`        | bootstrap output (`github_actions_role_arn`)                      |
| `ECR_REPOSITORY`      | bootstrap output (`ecr_repository_url`)                           |
| `TF_STATE_BUCKET`     | bootstrap output (`tf_state_bucket`)                              |
| `TF_STATE_LOCK_TABLE` | bootstrap output (`tf_state_lock_table`)                          |
| `PROJECT_PREFIX`      | matches `var.project_prefix` in bootstrap (default `clara-amaro`) |

## Running locally (optional, admin creds required)

```bash
cd infra/terraform
terraform init \
  -backend-config="bucket=clara-amaro-tf-state-<account_id>" \
  -backend-config="key=document-management-service/terraform.tfstate" \
  -backend-config="region=us-east-2" \
  -backend-config="dynamodb_table=clara-amaro-tf-state-lock" \
  -backend-config="encrypt=true"

terraform apply \
  -var="ecr_repository_url=<account_id>.dkr.ecr.us-east-2.amazonaws.com/clara-amaro-service" \
  -var="image_tag=latest"
```

Useful when debugging — otherwise prefer the workflow.

## Typical deploy cycle

1. Merge a PR to `develop`.
2. Go to **Actions → Deploy → Run workflow**, type `deploy`, click Run.
3. Wait ~8–10 minutes: image build & push → Terraform apply → ECS pulls the new task → ALB registers healthy targets.
4. Copy the service URL from the workflow summary, smoke-test with `curl`.
5. When done, **Actions → Destroy → Run workflow**, type `destroy` → teardown in ~5 minutes.

## Gotchas

- **First deploy is slow**: ~7 min of that is RDS provisioning. Subsequent deploys that don't change RDS are ~2 min.
- **Tests get the tables created**: first boot runs with `SPRING_PROFILES_ACTIVE=aws`, which flips `hibernate.ddl-auto` to `update`. Hibernate creates the `documents` + `document_tags` tables from the JPA entities. No init SQL is run on RDS.
- **ALB health check** points at `/actuator/health`. If you remove the actuator dependency, update `alb.tf`.
- **Forgot to destroy?** The ALB ($0.03/hr), RDS ($0.02/hr), and Fargate task ($0.025/hr) together cost ~$1.80/day. Set a Budget alarm.

