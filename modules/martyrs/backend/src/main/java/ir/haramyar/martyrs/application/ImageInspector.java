package ir.haramyar.martyrs.application;

import java.util.Optional;

/**
 * Determines the image type from the file's own bytes (magic numbers), never from the client-declared content type
 * or file name. Only raster formats browsers render safely are accepted; SVG (scriptable) is refused.
 */
public final class ImageInspector {
  public static final long MAX_BYTES = 5L * 1024 * 1024;

  public enum ImageType {
    JPEG("image/jpeg", "jpg"), PNG("image/png", "png"), WEBP("image/webp", "webp");
    public final String contentType;
    public final String extension;
    ImageType(String contentType, String extension) { this.contentType = contentType; this.extension = extension; }
  }

  private ImageInspector() {}

  public static Optional<ImageType> detect(byte[] bytes) {
    if (bytes == null || bytes.length < 12) return Optional.empty();
    if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) return Optional.of(ImageType.JPEG);
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    boolean isPng = true;
    for (int i = 0; i < png.length; i++) isPng &= bytes[i] == png[i];
    if (isPng) return Optional.of(ImageType.PNG);
    if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P')
      return Optional.of(ImageType.WEBP);
    return Optional.empty();
  }
}
