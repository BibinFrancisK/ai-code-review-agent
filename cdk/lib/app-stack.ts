import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import { execSync } from 'child_process';
import { readFileSync } from 'fs';
import { join } from 'path';
import {
  CW_LOGS_RESOURCE,
  FALLBACK_CIDR,
  GITHUB_META_URL,
  KMS_ACTION_DECRYPT,
  KMS_SSM_KEY_ALIAS,
  MANAGED_POLICY_SSM_READ_ONLY,
  PLACEHOLDER_DOCKERHUB_USERNAME,
  PLACEHOLDER_REGION,
  UBUNTU_2204_AMI_SSM_PATH,
  USER_DATA_SCRIPT_PATH,
} from '../types/constants';

export class AppStack extends cdk.Stack {
  constructor(scope: cdk.App, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const dockerhubUsername = this.node.tryGetContext('dockerhubUsername') as string;

    const vpc = ec2.Vpc.fromLookup(this, 'DefaultVpc', { isDefault: true });

    const securityGroup = new ec2.SecurityGroup(this, 'AppSG', { vpc, allowAllOutbound: true });

    const githubHookCidrs = fetchGitHubHookCidrs();
    for (const cidr of githubHookCidrs) {
      securityGroup.addIngressRule(ec2.Peer.ipv4(cidr), ec2.Port.tcp(8080), 'GitHub webhooks');
    }

    securityGroup.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(22), 'SSH');

    const role = new iam.Role(this, 'AppRole', {
      assumedBy: new iam.ServicePrincipal('ec2.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName(MANAGED_POLICY_SSM_READ_ONLY),
      ],
      inlinePolicies: {
        SsmKmsDecrypt: new iam.PolicyDocument({
          statements: [
            new iam.PolicyStatement({
              actions: [KMS_ACTION_DECRYPT],
              resources: [`arn:aws:kms:${this.region}:${this.account}:${KMS_SSM_KEY_ALIAS}`],
            }),
          ],
        }),
        CloudWatchLogs: new iam.PolicyDocument({
          statements: [
            new iam.PolicyStatement({
              actions: [
                'logs:CreateLogGroup',
                'logs:CreateLogStream',
                'logs:PutLogEvents',
                'logs:DescribeLogStreams',
              ],
              resources: [CW_LOGS_RESOURCE],
            }),
          ],
        }),
      },
    });

    const ec2UserData = ec2.UserData.custom(buildUserData(this.region, dockerhubUsername));

    const instance = new ec2.Instance(this, 'AppInstance', {
      vpc,
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.MICRO),
      machineImage: ec2.MachineImage.fromSsmParameter(UBUNTU_2204_AMI_SSM_PATH),
      securityGroup: securityGroup,
      role,
      userData: ec2UserData,
    });

    new cdk.CfnOutput(this, 'PublicIp', { value: instance.instancePublicIp });
    new cdk.CfnOutput(this, 'WebhookUrl', {
      value: `${instance.instancePublicIp}:8080/webhook/github`,
    });
  }
}

function fetchGitHubHookCidrs(): string[] {
  try {
    const result = execSync(`curl -sf --max-time 10 ${GITHUB_META_URL}`, { timeout: 12000 });
    const meta = JSON.parse(result.toString()) as { hooks: string[] };
    return meta.hooks.filter(cidr => !cidr.includes(':'));
  } catch {
    console.warn('Could not fetch GitHub meta IPs — falling back to 0.0.0.0/0');
    return [FALLBACK_CIDR];
  }
}

function buildUserData(region: string, dockerhubUsername: string): string {
  const script = readFileSync(join(__dirname, USER_DATA_SCRIPT_PATH), 'utf-8');
  return script
    .replace(PLACEHOLDER_REGION, region)
    .replace(PLACEHOLDER_DOCKERHUB_USERNAME, dockerhubUsername);
}
