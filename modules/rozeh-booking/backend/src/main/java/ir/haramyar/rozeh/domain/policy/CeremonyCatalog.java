package ir.haramyar.rozeh.domain.policy;

import ir.haramyar.rozeh.domain.model.CeremonyDetails;
import java.util.Set;

/**
 * Allowed codes per ceremony dimension. The exhaustive lists are an open business decision (see
 * docs/BUSINESS_DECISIONS.md); they are therefore supplied by configuration instead of being hardcoded here.
 */
public record CeremonyCatalog(Set<String> categories, Set<String> audiences, Set<String> deliveryModes) {
  public CeremonyCatalog {
    categories = Set.copyOf(categories);
    audiences = Set.copyOf(audiences);
    deliveryModes = Set.copyOf(deliveryModes);
    if (categories.isEmpty() || deliveryModes.isEmpty()) throw new IllegalArgumentException("At least one category and one delivery mode must be configured");
  }

  public boolean allows(CeremonyDetails details) {
    return categories.contains(details.category())
        && (details.audience() == null || audiences.contains(details.audience()))
        && deliveryModes.contains(details.deliveryMode());
  }
}
