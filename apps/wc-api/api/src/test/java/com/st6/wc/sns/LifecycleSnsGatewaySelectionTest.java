package com.st6.wc.sns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.awspring.cloud.sns.core.SnsOperations;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Bean-selection proof (Wave-2 s7): EXACTLY ONE {@link LifecycleSnsGateway} is active — the real
 * {@link AwsSnsLifecycleGateway} when the topic ARN is configured (deployed {@code aws}), the
 * {@link LoggingLifecycleSnsGateway} stub otherwise (local/demo/test). The selection is
 * property-driven (the {@code app.sns.topic-arn} presence), so no test or env accidentally
 * publishes to a live topic, and it does not depend on component-scan ordering.
 */
class LifecycleSnsGatewaySelectionTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
          .withBean(SnsOperations.class, () -> mock(SnsOperations.class))
          .withUserConfiguration(AwsSnsLifecycleGateway.class, LoggingLifecycleSnsGateway.class);

  @Test
  void realGatewayActiveWhenArnPresent() {
    runner
        .withPropertyValues("app.sns.topic-arn=arn:aws:sns:us-east-1:123456789012:wc-lifecycle")
        .run(
            ctx -> {
              assertThat(ctx.getBeansOfType(LifecycleSnsGateway.class)).hasSize(1);
              assertThat(ctx.getBean(LifecycleSnsGateway.class))
                  .isInstanceOf(AwsSnsLifecycleGateway.class);
            });
  }

  @Test
  void stubActiveWhenArnAbsent() {
    runner.run(
        ctx -> {
          assertThat(ctx.getBeansOfType(LifecycleSnsGateway.class)).hasSize(1);
          assertThat(ctx.getBean(LifecycleSnsGateway.class))
              .isInstanceOf(LoggingLifecycleSnsGateway.class);
        });
  }
}
