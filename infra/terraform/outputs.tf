output "service_url" {
  description = "Public URL of the deployed service (HTTP)."
  value       = "http://${aws_lb.service.dns_name}"
}

output "alb_dns_name" {
  value = aws_lb.service.dns_name
}

output "documents_bucket" {
  value = aws_s3_bucket.documents.bucket
}

output "db_endpoint" {
  value     = aws_db_instance.main.endpoint
  sensitive = true
}

output "ecs_cluster" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service" {
  value = aws_ecs_service.service.name
}

output "log_group" {
  value = aws_cloudwatch_log_group.service.name
}
