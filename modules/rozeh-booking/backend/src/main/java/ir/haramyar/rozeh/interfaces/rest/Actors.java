package ir.haramyar.rozeh.interfaces.rest;

import ir.haramyar.kit.security.CurrentActor;
import ir.haramyar.kit.security.HaramyarSecurityProperties;
import ir.haramyar.rozeh.domain.model.BookingActor;
import org.springframework.stereotype.Component;

/**
 * Resolves the booking actor from the secured request. A coordinator capacity requires both the Hive business
 * permission (checked by the BFF on the route operation before the request reaches this service) and a staff account
 * (checked here, independently).
 */
@Component
class Actors {
  private final HaramyarSecurityProperties security;

  Actors(HaramyarSecurityProperties security) { this.security = security; }

  BookingActor host() { return BookingActor.host(CurrentActor.require().userId()); }

  BookingActor coordinator() { return BookingActor.coordinator(CurrentActor.requireStaff(security.staffRole()).userId()); }

  void requireStaff() { CurrentActor.requireStaff(security.staffRole()); }

  boolean mobileVerified() { return CurrentActor.require().mobileVerified(); }
}
