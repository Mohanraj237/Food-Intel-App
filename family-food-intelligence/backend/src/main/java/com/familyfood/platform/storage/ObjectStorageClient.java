package com.familyfood.platform.storage;

import com.familyfood.platform.config.AppProperties;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

/**
 * The one place this application talks to S3-compatible object storage (works against
 * MinIO in local/dev, since {@link AppProperties.Storage#pathStyleAccess()} is exactly what
 * MinIO needs). Bound to {@code familyfood.storage} — the same endpoint/region/credentials
 * {@code label_image} storage is scoped to conceptually, but this client is bucket-agnostic:
 * every call names its own bucket, so a private bucket (future label-photo use) and the
 * public catalogue-image bucket never share code that assumes one or the other's visibility.
 *
 * <p>Credentials never leave this class — no method here returns them, and nothing outside
 * {@code platform.storage} touches {@link AppProperties.Storage#accessKey()} or
 * {@link AppProperties.Storage#secretKey()}.
 *
 * <p>Uses the SDK's synchronous {@code S3Client} (Apache HTTP client) rather than the async
 * client — every write path in this codebase is synchronous/transactional, and an async
 * upload would need its own error-handling and cancellation story for no present benefit.
 */
@Component
public class ObjectStorageClient {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageClient.class);

    private final S3Client client;
    private final String endpoint;

    public ObjectStorageClient(AppProperties properties) {
        AppProperties.Storage config = properties.storage();
        this.endpoint = config.endpoint();

        AwsCredentialsProvider credentials =
                (config.accessKey() != null && !config.accessKey().isBlank()
                        && config.secretKey() != null && !config.secretKey().isBlank())
                        ? StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(config.accessKey(), config.secretKey()))
                        : DefaultCredentialsProvider.create();

        this.client = S3Client.builder()
                .endpointOverride(URI.create(config.endpoint()))
                .region(Region.of(config.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.pathStyleAccess())
                        .build())
                .build();
    }

    /**
     * Uploads {@code content} to {@code bucket}/{@code key}, overwriting any existing object
     * at that key (callers that want upload-once idempotency — e.g.
     * {@code catalog.application.ProductImageService} — check for an existing row by content
     * hash before calling this, so a duplicate call is never expected in practice).
     *
     * @throws AppException {@link ErrorCode#STORAGE_UNAVAILABLE} if the backing store cannot
     *                       be reached or rejects the write — this is a degraded-dependency
     *                       failure, never conflated with "the object does not exist."
     */
    public void putObject(String bucket, String key, byte[] content, String contentType) {
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) content.length)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException e) {
            log.warn("Object storage upload failed for bucket={} key={}: {}", bucket, key, e.getMessage());
            throw new AppException(ErrorCode.STORAGE_UNAVAILABLE, java.util.Map.of(), e);
        }
    }

    /**
     * A stable, path-style URL for an object in a public-read bucket (e.g. the catalogue
     * image bucket). Deliberately not signed — {@link AppProperties.Storage#signedUrlTtl()}
     * is for a future private-image use case; a catalogue photo sourced from Open Food
     * Facts/brand-published material is not personal data and does not need per-request
     * signing (see the decision recorded for this step).
     */
    public String publicUrl(String bucket, String key) {
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return base + "/" + bucket + "/" + key;
    }
}
