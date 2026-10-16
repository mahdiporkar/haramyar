package ir.haramyar.rozeh.infrastructure.config;

import ir.haramyar.rozeh.domain.model.BookingActor;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Business policies awaiting customer approval, made explicit as configuration (see docs/BUSINESS_DECISIONS.md).
 * Defaults are reversible and documented as provisional.
 *
 * @param categories allowed ceremony category codes (provisional: {@code rozeh})
 * @param audiences allowed audience codes (provisional, from the approved design: family, women, men)
 * @param deliveryModes allowed delivery modes (first release targets home hosts: {@code in-person})
 * @param confirmationBy who performs final confirmation (provisional: HOST, as shown in the prototype)
 * @param providerRequiredForConfirmation whether a provider must be assigned before confirmation (provisional: false)
 * @param hostCancellationNotice minimum notice for host cancellations; empty means no deadline (not yet decided)
 * @param requireVerifiedMobile whether a verified mobile number is required before booking (provisional: false)
 * @param phoneDefaultRegion region for national-format numbers
 * @param phoneRegions regions whose mobile numbers are accepted
 */
@ConfigurationProperties("haramyar.rozeh")
public record RozehBookingProperties(Set<String> categories, Set<String> audiences, Set<String> deliveryModes, Set<BookingActor.Role> confirmationBy,
    Boolean providerRequiredForConfirmation, Duration hostCancellationNotice, Boolean requireVerifiedMobile, String phoneDefaultRegion, List<String> phoneRegions) {
  public RozehBookingProperties {
    categories = categories == null || categories.isEmpty() ? Set.of("rozeh") : Set.copyOf(categories);
    audiences = audiences == null ? Set.of("family", "women", "men") : Set.copyOf(audiences);
    deliveryModes = deliveryModes == null || deliveryModes.isEmpty() ? Set.of("in-person") : Set.copyOf(deliveryModes);
    confirmationBy = confirmationBy == null || confirmationBy.isEmpty() ? Set.of(BookingActor.Role.HOST) : Set.copyOf(confirmationBy);
    providerRequiredForConfirmation = Boolean.TRUE.equals(providerRequiredForConfirmation);
    requireVerifiedMobile = Boolean.TRUE.equals(requireVerifiedMobile);
    phoneDefaultRegion = phoneDefaultRegion == null || phoneDefaultRegion.isBlank() ? "IR" : phoneDefaultRegion;
    phoneRegions = phoneRegions == null || phoneRegions.isEmpty() ? List.of("IR", "IQ") : List.copyOf(phoneRegions);
  }
}
