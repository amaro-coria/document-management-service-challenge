resource "aws_security_group" "alb" {
  name        = "${local.name}-alb-sg"
  description = "Ingress :80 from the internet; egress to the service SG."
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name}-alb-sg" }
}

resource "aws_security_group" "service" {
  name        = "${local.name}-service-sg"
  description = "Ingress only from the ALB; egress everywhere (S3, RDS, ECR, logs)."
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "From ALB to container port"
    from_port       = local.service_port
    to_port         = local.service_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name}-service-sg" }
}

resource "aws_security_group" "rds" {
  name        = "${local.name}-rds-sg"
  description = "Postgres: only from the service SG."
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Postgres"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.service.id]
  }

  tags = { Name = "${local.name}-rds-sg" }
}
