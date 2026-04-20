########################################
# S3 bucket for document objects
########################################

resource "aws_s3_bucket" "documents" {
  bucket        = "${local.name}-documents-${data.aws_caller_identity.current.account_id}"
  force_destroy = true # POC: let `terraform destroy` wipe it even with objects inside.
}

resource "aws_s3_bucket_server_side_encryption_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "documents" {
  bucket                  = aws_s3_bucket.documents.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_cors_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id

  # Permissive for the demo: the client PUTs pre-signed URLs from any origin.
  # Tighten this before exposing in prod.
  cors_rule {
    allowed_methods = ["GET", "PUT", "HEAD"]
    allowed_origins = ["*"]
    allowed_headers = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}
