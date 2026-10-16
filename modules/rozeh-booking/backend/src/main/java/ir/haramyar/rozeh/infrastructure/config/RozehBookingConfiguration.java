package ir.haramyar.rozeh.infrastructure.config;

import ir.haramyar.kit.messaging.OutboxWriter;
import ir.haramyar.rozeh.application.BookingAdmissionPolicy;
import ir.haramyar.rozeh.application.BookingCommandHandlers;
import ir.haramyar.rozeh.application.BookingQueryHandlers;
import ir.haramyar.rozeh.application.BookingViews;
import ir.haramyar.rozeh.application.ProviderHandlers;
import ir.haramyar.rozeh.application.port.BookingReadModel;
import ir.haramyar.rozeh.application.port.BookingRepository;
import ir.haramyar.rozeh.application.port.ProviderRepository;
import ir.haramyar.rozeh.domain.policy.BookingActorPolicy;
import ir.haramyar.rozeh.domain.policy.BookingAvailabilityPolicy;
import ir.haramyar.rozeh.domain.policy.BookingCancellationPolicy;
import ir.haramyar.rozeh.domain.policy.BookingConfirmationPolicy;
import ir.haramyar.rozeh.domain.policy.BookingPolicies;
import ir.haramyar.rozeh.domain.policy.CeremonyCatalog;
import ir.haramyar.rozeh.domain.policy.ProviderCalendar;
import ir.haramyar.rozeh.infrastructure.adapter.LibPhoneNumbers;
import ir.haramyar.rozeh.infrastructure.adapter.OutboxIntegrationEvents;
import ir.haramyar.rozeh.infrastructure.adapter.SecureRandomTrackingCodes;
import ir.haramyar.rozeh.application.query.ServiceCatalog;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableConfigurationProperties(RozehBookingProperties.class)
class RozehBookingConfiguration {
  @Bean @ConditionalOnMissingBean Clock clock() { return Clock.systemUTC(); }

  @Bean BookingPolicies bookingPolicies(RozehBookingProperties p) {
    return new BookingPolicies(BookingActorPolicy.provisional(p.confirmationBy()), new BookingConfirmationPolicy(p.providerRequiredForConfirmation()),
        new BookingCancellationPolicy(Optional.ofNullable(p.hostCancellationNotice())), new CeremonyCatalog(p.categories(), p.audiences(), p.deliveryModes()));
  }

  @Bean ServiceCatalog serviceCatalog(RozehBookingProperties p) {
    return new ServiceCatalog(List.copyOf(new TreeSet<>(p.categories())), List.copyOf(new TreeSet<>(p.audiences())), List.copyOf(new TreeSet<>(p.deliveryModes())),
        p.confirmationBy().stream().map(Enum::name).sorted().toList(), p.providerRequiredForConfirmation(),
        p.hostCancellationNotice() == null ? -1 : p.hostCancellationNotice().toMinutes(), p.requireVerifiedMobile(), true);
  }

  @Bean BookingAdmissionPolicy bookingAdmissionPolicy(RozehBookingProperties p) { return new BookingAdmissionPolicy(p.requireVerifiedMobile()); }

  @Bean BookingAvailabilityPolicy bookingAvailabilityPolicy(ProviderCalendar calendar) { return new BookingAvailabilityPolicy(calendar); }

  @Bean BookingViews bookingViews(ProviderRepository providers, BookingPolicies policies, Clock clock) { return new BookingViews(providers, policies, clock); }

  @Bean BookingCommandHandlers bookingCommandHandlers(BookingRepository bookings, ProviderRepository providers, OutboxWriter outbox, RozehBookingProperties p,
      BookingAvailabilityPolicy availability, BookingPolicies policies, BookingAdmissionPolicy admission, BookingViews views, PlatformTransactionManager tx, Clock clock) {
    return new BookingCommandHandlers(bookings, providers, new OutboxIntegrationEvents(outbox), new LibPhoneNumbers(p.phoneDefaultRegion(), p.phoneRegions()),
        new SecureRandomTrackingCodes(), availability, policies, admission, views, new TransactionTemplate(tx), clock);
  }

  @Bean BookingQueryHandlers bookingQueryHandlers(BookingRepository bookings, BookingReadModel readModel, BookingViews views, PlatformTransactionManager tx) {
    TransactionTemplate readOnly = new TransactionTemplate(tx);
    readOnly.setReadOnly(true);
    return new BookingQueryHandlers(bookings, readModel, views, readOnly);
  }

  @Bean ProviderHandlers providerHandlers(ProviderRepository providers, PlatformTransactionManager tx, Clock clock) {
    return new ProviderHandlers(providers, new TransactionTemplate(tx), clock);
  }
}
