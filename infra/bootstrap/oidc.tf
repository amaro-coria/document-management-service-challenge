########################################
# GitHub OIDC federation + CI role
########################################

data "aws_caller_identity" "current" {}

# Creates (or re-uses) the GitHub Actions OIDC provider in this account.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]

  # Amazon publishes the thumbprints; GitHub rotates certs occasionally.
  # See https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd"
  ]
}

locals {
  github_subjects = [
    for pattern in var.allowed_subjects :
    "repo:${var.github_owner}/${var.github_repo}:${pattern}"
  ]
}

data "aws_iam_policy_document" "ci_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = local.github_subjects
    }
  }
}

resource "aws_iam_role" "ci" {
  name               = "${var.project_prefix}-gha-ci"
  assume_role_policy = data.aws_iam_policy_document.ci_trust.json
  description        = "Assumed by GitHub Actions via OIDC to run terraform + docker push."
}

# Scope: broad enough to manage our stack, tight enough to stay out of other accounts.
# For a POC this is pragmatic; a prod setup would further split plan vs. apply roles.
resource "aws_iam_role_policy_attachment" "ci_power_user" {
  role       = aws_iam_role.ci.name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

# PowerUser cannot manage IAM; allow just what our stack needs.
resource "aws_iam_role_policy" "ci_iam" {
  name = "terraform-iam-management"
  role = aws_iam_role.ci.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "iam:CreateRole",
          "iam:DeleteRole",
          "iam:GetRole",
          "iam:UpdateRole",
          "iam:UpdateAssumeRolePolicy",
          "iam:ListRoles",
          "iam:PassRole",
          "iam:TagRole",
          "iam:UntagRole",
          "iam:ListRoleTags",
          "iam:AttachRolePolicy",
          "iam:DetachRolePolicy",
          "iam:ListAttachedRolePolicies",
          "iam:PutRolePolicy",
          "iam:DeleteRolePolicy",
          "iam:GetRolePolicy",
          "iam:ListRolePolicies",
          "iam:CreateInstanceProfile",
          "iam:DeleteInstanceProfile",
          "iam:GetInstanceProfile",
          "iam:AddRoleToInstanceProfile",
          "iam:RemoveRoleFromInstanceProfile",
          "iam:CreatePolicy",
          "iam:DeletePolicy",
          "iam:GetPolicy",
          "iam:ListPolicies",
          "iam:CreatePolicyVersion",
          "iam:DeletePolicyVersion",
          "iam:GetPolicyVersion",
          "iam:ListPolicyVersions",
          "iam:ListEntitiesForPolicy",
          "iam:ListPolicyTags"
        ]
        Resource = "*"
      }
    ]
  })
}
