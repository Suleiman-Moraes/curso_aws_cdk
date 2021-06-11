package com.myorg;

import java.util.HashMap;
import java.util.Map;

import com.myorg.util.ConstantUtil;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Fn;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.CpuUtilizationScalingProps;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.ScalableTaskCount;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.events.targets.SnsTopic;
import software.amazon.awscdk.services.logs.LogGroup;

public class Service01Stack extends Stack {
	public Service01Stack(final Construct scope, final String id, Cluster cluster, SnsTopic productEventsTopic) {
		this(scope, id, null, cluster, productEventsTopic);
	}

	public Service01Stack(final Construct scope, final String id, final StackProps props, Cluster cluster,
			SnsTopic productEventsTopic) {
		super(scope, id, props);

		Map<String, String> envVariables = new HashMap<>();
		envVariables.put("SPRING_DATASOURCE_URL", "jdbc:mariadb://" + Fn.importValue(ConstantUtil.RDS_ENDPOINT)
				+ ":3306/aws_project01?createDatabaseIfNotExist=true");
		envVariables.put("SPRING_DATASOURCE_USERNAME", ConstantUtil.RDS_USERNAME);
		envVariables.put("SPRING_DATASOURCE_PASSWORD", Fn.importValue(ConstantUtil.RDS_PASSWORD));

		envVariables.put("MORAES_AWS_REGION", "us-east-1");
		envVariables.put("MORAES_AWS_SNS-TOPIC-PRODUCT-EVENTS-ARN", productEventsTopic.getTopic().getTopicArn());
		envVariables.put("MORAES_SWAGGER_SHOW", "true");

		ApplicationLoadBalancedFargateService service01 = ApplicationLoadBalancedFargateService.Builder
				.create(this, "ALB01").serviceName("service-01").cluster(cluster).cpu(512).memoryLimitMiB(1024).desiredCount(2).listenerPort(8080)
				.taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder().containerName("aws_project01")
						.image(ContainerImage.fromRegistry("suleimanmoaraes/curso_aws_project01:1.5.3"))
						.containerPort(8080)
						.logDriver(LogDriver.awsLogs(AwsLogDriverProps.builder()
								.logGroup(LogGroup.Builder.create(this, "Service01LogGroup").logGroupName("Service01")
										.removalPolicy(RemovalPolicy.DESTROY).build())
								.streamPrefix("Service01").build()))
						.environment(envVariables).build())
				.publicLoadBalancer(Boolean.TRUE).build();
		service01.getTargetGroup().configureHealthCheck(
				new HealthCheck.Builder().path("/actuator/health").port("8080").healthyHttpCodes("200").build());

		ScalableTaskCount scalableTaskCount = service01.getService()
				.autoScaleTaskCount(EnableScalingProps.builder().minCapacity(1).maxCapacity(2).build());

		scalableTaskCount.scaleOnCpuUtilization("Service01AutoScaling",
				CpuUtilizationScalingProps.builder().targetUtilizationPercent(50).scaleInCooldown(Duration.seconds(60))
						.scaleOutCooldown(Duration.seconds(60)).build());

		productEventsTopic.getTopic().grantPublish(service01.getTaskDefinition().getTaskRole());
	}
}
