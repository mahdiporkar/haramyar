package ir.haramyar.rozeh.domain.model;

/** Where the home ceremony takes place. The street address is personal data. */
public record CeremonyLocation(String city, String area, String address) {
  public CeremonyLocation {
    city = Text.required(city, "City", 2, 80);
    area = Text.optional(area, "Area", 80);
    address = Text.required(address, "Address", 5, 300);
  }
}
