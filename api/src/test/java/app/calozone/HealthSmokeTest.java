package app.calozone;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Proves the foundation: the context loads, Flyway migrates, and the database answers. */
@AutoConfigureMockMvc
class HealthSmokeTest extends IntegrationTest {

  private final MockMvcTester mvc;

  @Autowired
  HealthSmokeTest(MockMvcTester mvc) {
    this.mvc = mvc;
  }

  // Every endpoint requires authentication until security is configured in 1.08.
  @Test
  @WithMockUser
  void health_withDatabaseRunning_reportsUp() {
    assertThat(mvc.get().uri("/actuator/health"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("UP");
  }
}
