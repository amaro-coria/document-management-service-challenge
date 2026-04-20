########################################
# RDS PostgreSQL (POC: single AZ, no backups)
########################################

resource "random_password" "db" {
  length  = 20
  special = false # RDS allows special, but keeping simple for URL-safe injection
}

resource "aws_db_subnet_group" "main" {
  name       = "${local.name}-db-subnets"
  subnet_ids = aws_subnet.public[*].id
}

resource "aws_db_instance" "main" {
  identifier              = "${local.name}-db"
  engine                  = "postgres"
  engine_version          = "15"
  instance_class          = var.db_instance_class
  allocated_storage       = var.db_allocated_storage
  storage_type            = "gp3"
  db_name                 = "challenge"
  username                = "challenge_user"
  password                = random_password.db.result
  db_subnet_group_name    = aws_db_subnet_group.main.name
  vpc_security_group_ids  = [aws_security_group.rds.id]
  publicly_accessible     = false
  skip_final_snapshot     = true
  backup_retention_period = 0
  apply_immediately       = true
  deletion_protection     = false
}
