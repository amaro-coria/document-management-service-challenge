resource "aws_cloudwatch_log_group" "service" {
  name              = "/ecs/${local.name}-service"
  retention_in_days = 7
}
