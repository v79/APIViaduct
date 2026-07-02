# Minimal OpenTofu deployment of the sample-native Lambda function.
#
# Prerequisites:
#   1. Build the deployment package:
#        GRAALVM_HOME=~/.sdkman/candidates/java/21.0.2-graalce ../../gradlew -p .. buildNativeLambdaZip
#      (produces ../build/distributions/function.zip containing the `bootstrap` binary)
#   2. tofu init && tofu apply
#
# The function is exposed through an API Gateway HTTP API with payload format
# 1.0 — this matters: format 1.0 delivers the APIGatewayProxyRequestEvent (v1)
# shape that the router's event bridge expects. A Lambda Function URL or the
# default 2.0 format would send a different event shape.

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

variable "region" {
  description = "AWS region to deploy to"
  type        = string
  default     = "eu-west-2"
}

variable "function_name" {
  description = "Name of the Lambda function"
  type        = string
  default     = "apiviaduct-sample-native"
}

provider "aws" {
  region = var.region
}

locals {
  zip_path = "${path.module}/../build/distributions/function.zip"
}

resource "aws_iam_role" "lambda" {
  name = "${var.function_name}-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "basic_execution" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_lambda_function" "sample" {
  function_name    = var.function_name
  role             = aws_iam_role.lambda.arn
  filename         = local.zip_path
  source_code_hash = filebase64sha256(local.zip_path)

  # Native binary in a custom runtime: the zip contains a single executable
  # named `bootstrap`. The handler value is required but unused.
  runtime       = "provided.al2023"
  handler       = "bootstrap"
  architectures = ["x86_64"] # must match the architecture the binary was compiled on

  memory_size = 256
  timeout     = 10
}

resource "aws_apigatewayv2_api" "http_api" {
  name          = "${var.function_name}-api"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "lambda" {
  api_id                 = aws_apigatewayv2_api.http_api.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.sample.invoke_arn
  payload_format_version = "1.0"
}

resource "aws_apigatewayv2_route" "default" {
  api_id    = aws_apigatewayv2_api.http_api.id
  route_key = "$default"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.http_api.id
  name        = "$default"
  auto_deploy = true
}

resource "aws_lambda_permission" "apigw" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.sample.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.http_api.execution_arn}/*/*"
}

output "api_endpoint" {
  description = "Base URL of the deployed API"
  value       = aws_apigatewayv2_api.http_api.api_endpoint
}

output "example_requests" {
  description = "Try these against the deployed function"
  value = [
    "curl -H 'Accept: text/plain' ${aws_apigatewayv2_api.http_api.api_endpoint}/hello",
    "curl ${aws_apigatewayv2_api.http_api.api_endpoint}/person/Liam",
    "curl -X POST -H 'Content-Type: application/json' -d '{\"name\": \"Christopher\", \"age\": 42}' ${aws_apigatewayv2_api.http_api.api_endpoint}/person",
  ]
}
