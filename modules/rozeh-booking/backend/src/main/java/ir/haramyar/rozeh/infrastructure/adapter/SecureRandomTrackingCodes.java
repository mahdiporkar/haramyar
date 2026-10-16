package ir.haramyar.rozeh.infrastructure.adapter;

import ir.haramyar.rozeh.application.port.TrackingCodes;
import ir.haramyar.rozeh.domain.model.TrackingCode;
import java.security.SecureRandom;

/** 40 random bits rendered as {@code RZ-XXXX-XXXX}; uniqueness is checked by the handler and enforced by the database. */
public class SecureRandomTrackingCodes implements TrackingCodes {
  private final SecureRandom random = new SecureRandom();

  @Override
  public TrackingCode next() {
    var code = new StringBuilder("RZ-");
    for (int i = 0; i < 8; i++) {
      if (i == 4) code.append('-');
      code.append(TrackingCode.ALPHABET.charAt(random.nextInt(TrackingCode.ALPHABET.length())));
    }
    return new TrackingCode(code.toString());
  }
}
