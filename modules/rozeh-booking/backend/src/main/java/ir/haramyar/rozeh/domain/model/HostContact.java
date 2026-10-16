package ir.haramyar.rozeh.domain.model;

import java.util.Objects;

/** Who coordinators contact about this ceremony. Personal data: visible only to the host and authorized coordinators. */
public record HostContact(String name, PhoneNumber phone) {
  public HostContact {
    name = Text.required(name, "Host name", 2, 120);
    Objects.requireNonNull(phone, "phone");
  }
}
