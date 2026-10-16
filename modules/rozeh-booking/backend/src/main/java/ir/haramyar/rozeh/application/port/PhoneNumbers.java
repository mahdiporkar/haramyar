package ir.haramyar.rozeh.application.port;

import ir.haramyar.rozeh.domain.model.PhoneNumber;
import java.util.Optional;

/** Parses user-entered numbers (national or international format, Persian/Arabic digits) into canonical E.164. */
public interface PhoneNumbers {
  Optional<PhoneNumber> normalize(String raw);
}
