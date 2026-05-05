import * as cdk from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { AppStack } from '../lib/app-stack';

describe('AppStack', () => {
  const app = new cdk.App({ context: { dockerhubUsername: 'testuser' } });
  const stack = new AppStack(app, 'TestStack', {
    env: { account: '123456789012', region: 'us-east-1' },
  });
  const template = Template.fromStack(stack);

  test('creates an EC2 instance', () => {
    template.resourceCountIs('AWS::EC2::Instance', 1);
  });

  test('creates a security group with port 8080 and 22 open', () => {
    template.hasResourceProperties('AWS::EC2::SecurityGroup', {
      SecurityGroupIngress: [
        { FromPort: 8080, ToPort: 8080, IpProtocol: 'tcp', CidrIp: '0.0.0.0/0' },
        { FromPort: 22,   ToPort: 22,   IpProtocol: 'tcp', CidrIp: '0.0.0.0/0' },
      ],
    });
  });

  test('creates an IAM role with SSM read access', () => {
    template.hasResourceProperties('AWS::IAM::Role', {
      ManagedPolicyArns: [{ 'Fn::Join': ['', ['arn:', { Ref: 'AWS::Partition' }, ':iam::aws:policy/AmazonSSMReadOnlyAccess']] }],
    });
  });

  test('outputs PublicIp and WebhookUrl', () => {
    template.hasOutput('PublicIp', {});
    template.hasOutput('WebhookUrl', {});
  });
});
