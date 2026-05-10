// GitHub
export const GITHUB_META_URL = 'https://api.github.com/meta';

// Security group
export const FALLBACK_CIDR = '0.0.0.0/0';

// EC2 / AMI
export const UBUNTU_2204_AMI_SSM_PATH =
  '/aws/service/canonical/ubuntu/server/22.04/stable/current/amd64/hvm/ebs-gp2/ami-id';

// User data script
export const USER_DATA_SCRIPT_PATH = '../scripts/ec2-user-data.sh';
export const PLACEHOLDER_REGION = '__REGION__';
export const PLACEHOLDER_DOCKERHUB_USERNAME = '__DOCKERHUB_USERNAME__';

// IAM
export const MANAGED_POLICY_SSM_READ_ONLY = 'AmazonSSMReadOnlyAccess';
export const KMS_ACTION_DECRYPT = 'kms:Decrypt';
export const KMS_SSM_KEY_ALIAS = 'alias/aws/ssm';

// CloudWatch Logs
export const CW_LOGS_RESOURCE = 'arn:aws:logs:*:*:log-group:/ai-code-reviewer/*';
