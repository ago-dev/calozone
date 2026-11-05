package app.calozone;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for tests that need the full application and a real PostgreSQL.
 *
 * <p>One container serves the whole test run: it is started once in a static initializer and never
 * stopped explicitly (Testcontainers removes it when the JVM exits). Because every subclass shares
 * the same configuration, Spring also reuses one application context across them.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTest {

  /** Same image as infra/docker-compose.yml, so tests and local development run one engine. */
  private static final String POSTGRES_IMAGE = "postgres:16.15";

  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

  static {
    POSTGRES.start();
  }
}
