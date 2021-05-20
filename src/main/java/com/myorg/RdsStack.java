package com.myorg;

import java.util.Collections;

import com.myorg.util.ConstantUtil;

import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.CfnParameter;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.SecretValue;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.rds.MySqlInstanceEngineProps;
import software.amazon.awscdk.services.rds.MysqlEngineVersion;

public class RdsStack extends Stack {
	public RdsStack(final Construct scope, final String id, Vpc vpc) {
		this(scope, id, null, vpc);
	}

	public RdsStack(final Construct scope, final String id, final StackProps props, Vpc vpc) {
		super(scope, id, props);

		CfnParameter databasePassword = CfnParameter.Builder.create(this, "databasePassword").type("String")
				.description("The RDS instance password").build();

		ISecurityGroup iSecurityGroup = SecurityGroup.fromSecurityGroupId(this, id, vpc.getVpcDefaultSecurityGroup());
		iSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(3306));

		DatabaseInstance databaseInstance = DatabaseInstance.Builder.create(this, "Rds01")
				.instanceIdentifier("aws-project01-db")
				.engine(DatabaseInstanceEngine
						.mysql(MySqlInstanceEngineProps.builder().version(MysqlEngineVersion.VER_5_6).build()))
				.vpc(vpc)
				.credentials(Credentials.fromPassword(ConstantUtil.RDS_USERNAME,
						SecretValue.plainText(databasePassword.getValueAsString())))
				// .credentials(Credentials.fromUsername("admin",
				// CredentialsFromUsernameOptions.builder()
				// .password(SecretValue.plainText(databasePassword.getValueAsString())).build()))
				.instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO)).multiAz(Boolean.FALSE)
				.allocatedStorage(10).securityGroups(Collections.singletonList(iSecurityGroup))
				.vpcSubnets(SubnetSelection.builder().subnets(vpc.getPrivateSubnets()).build()).build();

		CfnOutput.Builder.create(this, ConstantUtil.RDS_ENDPOINT).exportName(ConstantUtil.RDS_ENDPOINT)
				.value(databaseInstance.getDbInstanceEndpointAddress()).build();

		CfnOutput.Builder.create(this, ConstantUtil.RDS_PASSWORD).exportName(ConstantUtil.RDS_PASSWORD)
				.value(databasePassword.getValueAsString()).build();
	}
}
