#!/usr/bin/env bash
set -euo pipefail

# Usage: ./deploy.sh [region] [account-id]
# Requires: aws cli, docker, terraform outputs

REGION="${1:-us-east-1}"
ACCOUNT_ID="${2:-$(aws sts get-caller-identity --query Account --output text)}"
APP_NAME="rca-agent"
ECR_REPO="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${APP_NAME}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

echo "=== RCA Agent Deployment ==="
echo "Region: ${REGION}"
echo "Account: ${ACCOUNT_ID}"
echo "Image: ${ECR_REPO}:${IMAGE_TAG}"
echo ""

# Step 1: Build
echo ">> Building Docker image..."
docker build -t "${APP_NAME}:${IMAGE_TAG}" .

# Step 2: Tag & Push to ECR
echo ">> Authenticating with ECR..."
aws ecr get-login-password --region "${REGION}" | \
    docker login --username AWS --password-stdin "${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

echo ">> Pushing image to ECR..."
docker tag "${APP_NAME}:${IMAGE_TAG}" "${ECR_REPO}:${IMAGE_TAG}"
docker push "${ECR_REPO}:${IMAGE_TAG}"

# Step 3: Force new deployment
echo ">> Updating ECS service..."
aws ecs update-service \
    --cluster "${APP_NAME}-cluster" \
    --service "${APP_NAME}" \
    --force-new-deployment \
    --region "${REGION}" \
    --no-cli-pager

echo ""
echo "=== Deployment initiated ==="
echo "Monitor: https://${REGION}.console.aws.amazon.com/ecs/v2/clusters/${APP_NAME}-cluster/services/${APP_NAME}"
