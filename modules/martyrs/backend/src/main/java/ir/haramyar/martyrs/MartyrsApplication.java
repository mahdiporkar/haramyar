package ir.haramyar.martyrs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Martyrs service: an independently deployable HaramYar business module behind the Hive BFF. */
@SpringBootApplication
public class MartyrsApplication {
  public static void main(String[] args) {
    SpringApplication.run(MartyrsApplication.class, args);
  }
}
