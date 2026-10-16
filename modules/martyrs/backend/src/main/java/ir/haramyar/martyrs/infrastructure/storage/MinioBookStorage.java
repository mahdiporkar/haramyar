package ir.haramyar.martyrs.infrastructure.storage;

import ir.haramyar.martyrs.application.BookObjectStore;
import java.io.InputStream;
import java.net.URI;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
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

/** Private, separate MinIO bucket. Existing SeaweedFS portrait objects are not migrated or touched. */
@Component
public final class MinioBookStorage implements BookObjectStore {
  private final S3Client s3;
  private final String bucket;

  public MinioBookStorage(@Value("${haramyar.martyrs.books.endpoint}") String endpoint,
      @Value("${haramyar.martyrs.books.region:us-east-1}") String region,
      @Value("${haramyar.martyrs.books.access-key}") String access,
      @Value("${haramyar.martyrs.books.secret-key}") String secret,
      @Value("${haramyar.martyrs.books.bucket:haramyar-books}") String bucket) {
    this.bucket = bucket;
    this.s3 = S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access, secret)))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .httpClient(UrlConnectionHttpClient.create()).build();
  }

  @PostConstruct
  public void ensureBucket() {
    try { s3.headBucket(b -> b.bucket(bucket)); }
    catch (NoSuchBucketException missing) { s3.createBucket(b -> b.bucket(bucket)); }
  }

  @Override
  public void put(String key, InputStream stream, long size) {
    s3.putObject(b -> b.bucket(bucket).key(key).contentType("application/pdf").contentLength(size),
        RequestBody.fromInputStream(stream, size));
  }

  @Override
  public InputStream open(String key) {
    return s3.getObject(b -> b.bucket(bucket).key(key));
  }
}
