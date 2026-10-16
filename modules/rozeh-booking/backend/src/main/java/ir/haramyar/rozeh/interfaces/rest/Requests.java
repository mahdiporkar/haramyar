package ir.haramyar.rozeh.interfaces.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

/** HTTP request bodies. They never carry the acting user's identity; that comes from the secured request. */
final class Requests {
  private Requests() {}

  record SubmitBooking(@NotNull UUID clientRequestId, @NotBlank @Size(max = 120) String hostName, @NotBlank @Size(max = 40) String hostPhone,
      @NotBlank @Size(max = 80) String city, @Size(max = 80) String area, @NotBlank @Size(max = 300) String address,
      @NotBlank @Size(max = 40) String category, @Size(max = 40) String audience, @NotBlank @Size(max = 40) String deliveryMode,
      Integer approximateCapacity, @Size(max = 1000) String notes, @NotNull OffsetDateTime startsAt,
      @Min(1) @Max(1440) int durationMinutes, @NotBlank @Size(max = 64) String timeZone) {}

  record Transition(Long expectedVersion, @Size(max = 500) String reason) {}

  record Assignment(Long expectedVersion, @NotNull UUID providerId) {}

  record NewProvider(@NotBlank @Size(max = 120) String displayName) {}

  record ProviderChange(@NotNull Long expectedVersion, @Size(max = 120) String displayName, Boolean active) {}
}
