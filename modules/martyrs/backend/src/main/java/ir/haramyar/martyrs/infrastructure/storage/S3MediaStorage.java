package ir.haramyar.martyrs.infrastructure.storage;

import ir.haramyar.martyrs.application.Ports.MediaStorage;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * S3-compatible object storage (MinIO in the reference deployment). The bucket is private: objects are delivered only
 * through the application's authorization checks, never by pre-signed or public bucket URLs.
 */
public class S3MediaStorage implements MediaStorage {
  private final S3Client s3;
  private final String bucket;

  public S3MediaStorage(String endpoint, String region, String accessKey, String secretKey, String bucket) {
    this.bucket = bucket;
    this.s3 = S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        // S3-compatible stores (SeaweedFS, Garage, ...) do not all decode aws-chunked streaming bodies or trailing checksums.
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .httpClient(UrlConnectionHttpClient.create())
        .build();
  }

  /** Creates the private bucket on first start. */
  public void ensureBucket() {
    try {
      s3.headBucket(b -> b.bucket(bucket));
    } catch (NoSuchBucketException missing) {
      s3.createBucket(b -> b.bucket(bucket));
    }
  }

  @Override
  public void put(String key, byte[] bytes, String contentType) {
    s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType).contentLength((long) bytes.length), RequestBody.fromBytes(bytes));
  }

  @Override
  public byte[] get(String key) {
    return s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray();
  }
}
