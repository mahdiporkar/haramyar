package ir.haramyar.rozeh.application;

import ir.haramyar.rozeh.application.command.BookingCommands.RegisterProvider;
import ir.haramyar.rozeh.application.command.BookingCommands.UpdateProvider;
import ir.haramyar.rozeh.application.port.ConcurrentModification;
import ir.haramyar.rozeh.application.port.ProviderRepository;
import ir.haramyar.rozeh.application.query.ProviderView;
import ir.haramyar.rozeh.domain.model.BookingRuleViolation;
import ir.haramyar.rozeh.domain.model.Provider;
import ir.haramyar.rozeh.domain.model.ProviderId;
import java.time.Clock;
import java.util.List;
import org.springframework.transaction.support.TransactionTemplate;

/** Coordinator-maintained provider registry used for assignment. */
public class ProviderHandlers {
  private final ProviderRepository providers;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public ProviderHandlers(ProviderRepository providers, TransactionTemplate transactions, Clock clock) {
    this.providers = providers;
    this.transactions = transactions;
    this.clock = clock;
  }

  public ProviderView handle(RegisterProvider command) {
    return transactions.execute(status -> {
      Provider provider = Provider.register(ProviderId.newId(), command.displayName(), clock.instant());
      providers.add(provider);
      return ProviderView.of(provider);
    });
  }

  public ProviderView handle(UpdateProvider command) {
    return transactions.execute(status -> {
      Provider provider = providers.findById(new ProviderId(command.providerId()))
          .orElseThrow(() -> new BookingRuleViolation(BookingRuleViolation.Code.INVALID_VALUE, "Unknown provider"));
      if (provider.version() != command.expectedVersion()) throw new ConcurrentModification();
      if (command.displayName() != null) provider.rename(command.displayName());
      if (Boolean.TRUE.equals(command.active())) provider.activate();
      if (Boolean.FALSE.equals(command.active())) provider.deactivate();
      providers.update(provider);
      return ProviderView.of(providers.findById(provider.id()).orElseThrow());
    });
  }

  public List<ProviderView> list(boolean includeInactive) {
    return transactions.execute(status -> providers.list(includeInactive).stream().map(ProviderView::of).toList());
  }
}
