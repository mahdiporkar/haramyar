package ir.haramyar.rozeh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Rozeh Booking service: an independently deployable HaramYar business module behind the Hive BFF. */
@SpringBootApplication
public class RozehBookingApplication {
  public static void main(String[] args) {
    SpringApplication.run(RozehBookingApplication.class, args);
  }
}
