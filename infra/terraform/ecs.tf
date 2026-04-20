########################################
# ECS Fargate cluster, task def, service
########################################

resource "aws_ecs_cluster" "main" {
  name = "${local.name}-cluster"
}

resource "aws_ecs_task_definition" "service" {
  family                   = "${local.name}-service"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.service_cpu
  memory                   = var.service_memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([{
    name      = "service"
    image     = "${var.ecr_repository_url}:${var.image_tag}"
    essential = true
    portMappings = [{
      containerPort = local.service_port
      protocol      = "tcp"
    }]
    environment = [
      { name = "APP_PORT", value = tostring(local.service_port) },
      { name = "DB_HOST", value = aws_db_instance.main.address },
      { name = "DB_PORT", value = tostring(aws_db_instance.main.port) },
      { name = "DB_NAME", value = aws_db_instance.main.db_name },
      { name = "DB_SCHEMA", value = "document_schema" },
      { name = "MINIO_ENDPOINT", value = "https://s3.${var.region}.amazonaws.com" },
      { name = "MINIO_EXTERNAL_ENDPOINT", value = "https://s3.${var.region}.amazonaws.com" },
      { name = "MINIO_REGION", value = var.region },
      { name = "MINIO_BUCKET", value = aws_s3_bucket.documents.bucket },
      { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
      { name = "JAVA_OPTS", value = "-Xmx512m -Xms256m -XX:+UseSerialGC -Dspring.jmx.enabled=false" }
    ]
    secrets = [
      { name = "DB_USERNAME", valueFrom = "${aws_secretsmanager_secret.db.arn}:username::" },
      { name = "DB_PASSWORD", valueFrom = "${aws_secretsmanager_secret.db.arn}:password::" },
      { name = "MINIO_ACCESS_KEY", valueFrom = "${aws_secretsmanager_secret.s3_user.arn}:access_key::" },
      { name = "MINIO_SECRET_KEY", valueFrom = "${aws_secretsmanager_secret.s3_user.arn}:secret_key::" }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.service.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "service"
      }
    }
  }])
}

resource "aws_ecs_service" "service" {
  name                   = "${local.name}-service"
  cluster                = aws_ecs_cluster.main.id
  task_definition        = aws_ecs_task_definition.service.arn
  desired_count          = 1
  launch_type            = "FARGATE"
  enable_execute_command = true

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.service.id]
    assign_public_ip = true # no NAT; task pulls ECR + talks to S3 over the IGW
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.service.arn
    container_name   = "service"
    container_port   = local.service_port
  }

  depends_on = [aws_lb_listener.http, aws_db_instance.main]

  lifecycle {
    ignore_changes = [desired_count]
  }
}
