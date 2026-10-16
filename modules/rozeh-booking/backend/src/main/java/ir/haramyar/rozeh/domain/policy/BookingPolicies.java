package ir.haramyar.rozeh.domain.policy;

import java.util.Objects;

/** The configurable policies a booking operation is evaluated against. */
public record BookingPolicies(BookingActorPolicy actors, BookingConfirmationPolicy confirmation, BookingCancellationPolicy cancellation, CeremonyCatalog catalog) {
  public BookingPolicies {
    Objects.requireNonNull(actors, "actors");
    Objects.requireNonNull(confirmation, "confirmation");
    Objects.requireNonNull(cancellation, "cancellation");
    Objects.requireNonNull(catalog, "catalog");
  }
}
