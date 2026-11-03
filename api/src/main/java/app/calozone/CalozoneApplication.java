package app.calozone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Calozone API. Component scanning starts here, so every feature package must
 * live under {@code app.calozone}.
 */
@SpringBootApplication
public class CalozoneApplication {

  /**
   * Starts the Spring application context and the embedded web server.
   *
   * @param args command-line arguments, passed through to Spring
   */
  public static void main(String[] args) {
    SpringApplication.run(CalozoneApplication.class, args);
  }
}
