package com.ledgerlens.config;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${storage.max-download-bytes:20971520}")
    private long maxDownloadBytes;

    public String generateUploadUrl(String objectKey, int expiryMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate upload URL", e);
        }
    }

    public byte[] downloadBytes(String objectKey) {
        try {
            long size = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(objectKey).build()
            ).size();
            if (size > maxDownloadBytes) {
                throw new RuntimeException(
                        "File exceeds maximum allowed size of " + maxDownloadBytes + " bytes: " + objectKey);
            }
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
                return stream.readAllBytes();
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to download object: " + objectKey, e);
        }
    }

    public void assertObjectExists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new IllegalStateException("File not found in storage — upload it before triggering processing: " + objectKey);
        }
    }

    public void deleteObjectIfExists(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete object: " + objectKey, e);
        }
    }

    public String generateDownloadUrl(String objectKey, int expiryMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }

    public String computeContentHashFromBytes(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute content hash", e);
        }
    }
}
