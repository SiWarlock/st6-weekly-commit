package com.st6.wc.sns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.awspring.cloud.sns.core.SnsOperations;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Bean-selection proof (Wave-2 s7; hardened deploy-fix #5b): EXACTLY ONE {@link
 * LifecycleSnsGateway} is active — the real {@link AwsSnsLifecycleGateway} when {@code
 * app.sns.topic-arn} is NON-EMPTY (deployed API), the {@link LoggingLifecycleSnsGateway} stub when
 * it is empty/absent (local/demo/test + the aws-profile Jobs that never receive {@code
 * SNS_TOPIC_ARN}). The selection is a length-based {@code @ConditionalOnExpression} over an
 * empty-defaulted placeholder ({@code '${app.sns.topic-arn:}'.length()}) — so an aws-profile
 * context with no {@code SNS_TOPIC_ARN} boots cleanly (the empty default always resolves) instead
 * of crashing on an unresolvable placeholder, and the two beans stay mutually exclusive without
 * component-scan ordering.
 */
class LifecycleSnsGatewaySelectionTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
          .withBean(SnsOperations.class, () -> mock(SnsOperations.class))
          .withUserConfiguration(AwsSnsLifecycleGateway.class, LoggingLifecycleSnsGateway.class);

  @Test
  void setTopicArn_selectsRealGateway() {
    runner
        .withPropertyValues("app.sns.topic-arn=arn:aws:sns:us-east-1:123456789012:wc-lifecycle")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx.getBeansOfType(LifecycleSnsGateway.class)).hasSize(1);
              assertThat(ctx.getBean(LifecycleSnsGateway.class))
                  .isInstanceOf(AwsSnsLifecycleGateway.class);
            });
  }

  // THE fix (#5b): an aws-profile Job with no SNS_TOPIC_ARN → the empty default → boots clean +
  // stub
  // (previously: the present-but-empty value matched @ConditionalOnProperty → real gateway / or the
  // unresolvable placeholder crashed the context).
  @Test
  void emptyTopicArn_bootsClean_selectsStub() {
    runner
        .withPropertyValues("app.sns.topic-arn=")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx.getBeansOfType(LifecycleSnsGateway.class)).hasSize(1);
              assertThat(ctx.getBean(LifecycleSnsGateway.class))
                  .isInstanceOf(LoggingLifecycleSnsGateway.class);
            });
  }

  @Test
  void absentTopicArn_selectsStub() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx.getBeansOfType(LifecycleSnsGateway.class)).hasSize(1);
          assertThat(ctx.getBean(LifecycleSnsGateway.class))
              .isInstanceOf(LoggingLifecycleSnsGateway.class);
        });
  }

  // Marker (§42): pin the empty default in application-aws.yml so a future edit dropping the `:` —
  // which would re-introduce the unresolvable-placeholder boot crash on the Jobs — is caught here.
  @Test
  void awsYaml_hasEmptyDefaultOnTopicArn() throws Exception {
    try (var in = getClass().getResourceAsStream("/application-aws.yml")) {
      assertThat(in).as("application-aws.yml on the classpath").isNotNull();
      String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(yaml)
          .as("SNS_TOPIC_ARN must carry an empty default (${SNS_TOPIC_ARN:}) — deploy-fix #5b")
          .contains("${SNS_TOPIC_ARN:}");
    }
  }
}
