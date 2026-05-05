import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import { buildUserDataCommands } from './user-data';

export class AppStack extends cdk.Stack {
  constructor(scope: cdk.App, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const dockerhubUsername = this.node.tryGetContext('dockerhubUsername') as string;

    const vpc = ec2.Vpc.fromLookup(this, 'DefaultVpc', { isDefault: true });

    const sg = new ec2.SecurityGroup(this, 'AppSG', { vpc, allowAllOutbound: true });
    sg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(8080), 'GitHub webhooks');
    sg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(22), 'SSH');

    const role = new iam.Role(this, 'AppRole', {
      assumedBy: new iam.ServicePrincipal('ec2.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonSSMReadOnlyAccess'),
      ],
      inlinePolicies: {
        SsmKmsDecrypt: new iam.PolicyDocument({
          statements: [
            new iam.PolicyStatement({
              actions: ['kms:Decrypt'],
              resources: [`arn:aws:kms:${this.region}:${this.account}:alias/aws/ssm`],
            }),
          ],
        }),
      },
    });

    const userData = ec2.UserData.forLinux();
    userData.addCommands(...buildUserDataCommands(this.region, dockerhubUsername));

    const instance = new ec2.Instance(this, 'AppInstance', {
      vpc,
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.MICRO),
      machineImage: ec2.MachineImage.fromSsmParameter(
        '/aws/service/canonical/ubuntu/server/22.04/stable/current/amd64/hvm/ebs-gp2/ami-id',
      ),
      securityGroup: sg,
      role,
      userData,
    });

    new cdk.CfnOutput(this, 'PublicIp', { value: instance.instancePublicIp });
    new cdk.CfnOutput(this, 'WebhookUrl', {
      value: `${instance.instancePublicIp}:8080/webhook/github`,
    });
  }
}
