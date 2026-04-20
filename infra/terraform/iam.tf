########################################
# IAM: task execution role + task role + service user
########################################

# ---- Task execution role (ECR pull + logs + secrets read) ----
data "aws_iam_policy_document" "ecs_task_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "task_execution" {
  name               = "${local.name}-task-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume.json
}

resource "aws_iam_role_policy_attachment" "task_execution_managed" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "task_execution_secrets" {
  name = "read-service-secrets"
  role = aws_iam_role.task_execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = [aws_secretsmanager_secret.db.arn, aws_secretsmanager_secret.s3_user.arn]
    }]
  })
}

# ---- Task role (the service's runtime AWS identity — can touch S3) ----
resource "aws_iam_role" "task" {
  name               = "${local.name}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume.json
}

resource "aws_iam_role_policy" "task_s3" {
  name = "documents-bucket-rw"
  role = aws_iam_role.task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:ListBucket",
        "s3:GetBucketLocation"
      ]
      Resource = [
        aws_s3_bucket.documents.arn,
        "${aws_s3_bucket.documents.arn}/*"
      ]
    }]
  })
}

# ---- IAM user for the MinIO-SDK client (access key / secret key) ----
# The MinIO Java SDK expects static access/secret credentials; the native
# ECS task-role path would need an AWS SDK rewrite. For the POC we mint a
# dedicated IAM user scoped to the documents bucket and stash the keys in
# Secrets Manager.
resource "aws_iam_user" "service" {
  name          = "${local.name}-service"
  force_destroy = true
}

resource "aws_iam_user_policy" "service_s3" {
  name = "documents-bucket-rw"
  user = aws_iam_user.service.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:ListBucket",
        "s3:GetBucketLocation"
      ]
      Resource = [
        aws_s3_bucket.documents.arn,
        "${aws_s3_bucket.documents.arn}/*"
      ]
    }]
  })
}

resource "aws_iam_access_key" "service" {
  user = aws_iam_user.service.name
}

# ---- Secrets Manager: DB password + service S3 keys ----
resource "aws_secretsmanager_secret" "db" {
  name                    = "${local.name}/db"
  recovery_window_in_days = 0 # POC: destroy-and-recreate friendly.
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    username = aws_db_instance.main.username
    password = random_password.db.result
  })
}

resource "aws_secretsmanager_secret" "s3_user" {
  name                    = "${local.name}/s3-user"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "s3_user" {
  secret_id = aws_secretsmanager_secret.s3_user.id
  secret_string = jsonencode({
    access_key = aws_iam_access_key.service.id
    secret_key = aws_iam_access_key.service.secret
  })
}
