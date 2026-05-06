import * as cdk from 'aws-cdk-lib';
import { Match, Template } from 'aws-cdk-lib/assertions';
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

  test('restricts port 8080 to GitHub webhook IPs and leaves port 22 open', () => {
    const resources = template.findResources('AWS::EC2::SecurityGroup');
    const sg = Object.values(resources)[0] as { Properties: { SecurityGroupIngress: { FromPort: number; CidrIp: string }[] } };
    const ingress = sg.Properties.SecurityGroupIngress;

    expect(ingress.some(r => r.FromPort === 22 && r.CidrIp === '0.0.0.0/0')).toBe(true);

    const port8080Rules = ingress.filter(r => r.FromPort === 8080);
    expect(port8080Rules.length).toBeGreaterThan(0);
    expect(port8080Rules.every(r => r.CidrIp !== '0.0.0.0/0')).toBe(true);
  });

  test('creates an IAM role with SSM read access and CloudWatch Logs write access', () => {
    template.hasResourceProperties('AWS::IAM::Role', {
      ManagedPolicyArns: [{ 'Fn::Join': ['', ['arn:', { Ref: 'AWS::Partition' }, ':iam::aws:policy/AmazonSSMReadOnlyAccess']] }],
    });
    template.hasResourceProperties('AWS::IAM::Role', {
      Policies: Match.arrayWith([
        Match.objectLike({
          PolicyName: 'CloudWatchLogs',
          PolicyDocument: Match.objectLike({
            Statement: Match.arrayWith([
              Match.objectLike({
                Action: Match.arrayWith(['logs:PutLogEvents']),
                Resource: 'arn:aws:logs:*:*:log-group:/ai-code-reviewer/*',
              }),
            ]),
          }),
        }),
      ]),
    });
  });

  test('outputs PublicIp and WebhookUrl', () => {
    template.hasOutput('PublicIp', {});
    template.hasOutput('WebhookUrl', {});
  });
});
