package at.jku.dke.bigkgolap.storage;

import at.jku.dke.bigkgolap.storage.internal.ObjectKeys;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.InputStream;

public class MinioStorageService implements StorageService {

  private final MinioClient client;
  private final String bucket;

  public MinioStorageService(MinioClient client, String bucket) {
    this.client = client;
    this.bucket = bucket;
    ensureBucket();
  }

  @Override
  public void store(String schemaId, String storedName, InputStream input, long sizeBytes) {
    var key = ObjectKeys.objectKey(schemaId, storedName);
    try {
      client.putObject(
          PutObjectArgs.builder().bucket(bucket).object(key).stream(input, sizeBytes, -1L)
              .contentType("application/octet-stream")
              .build());
    } catch (Exception e) {
      throw new StorageWriteException(
          "Failed to store '%s' to bucket '%s'".formatted(key, bucket), e);
    }
  }

  @Override
  public InputStream load(String schemaId, String storedName) {
    var key = ObjectKeys.objectKey(schemaId, storedName);
    try {
      return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
    } catch (ErrorResponseException e) {
      if ("NoSuchKey".equals(e.errorResponse().code())) {
        throw new StorageNotFoundException(schemaId, storedName);
      }
      throw new StorageReadException("Failed to load '%s'".formatted(key), e);
    } catch (Exception e) {
      throw new StorageReadException("Failed to load '%s'".formatted(key), e);
    }
  }

  @Override
  public boolean exists(String schemaId, String storedName) {
    var key = ObjectKeys.objectKey(schemaId, storedName);
    try {
      client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
      return true;
    } catch (ErrorResponseException e) {
      if ("NoSuchKey".equals(e.errorResponse().code())) {
        return false;
      }
      throw new StorageReadException("statObject failed for '%s'".formatted(key), e);
    } catch (Exception e) {
      throw new StorageReadException("statObject failed for '%s'".formatted(key), e);
    }
  }

  @Override
  public void delete(String schemaId, String storedName) {
    var key = ObjectKeys.objectKey(schemaId, storedName);
    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
    } catch (Exception e) {
      throw new StorageWriteException("Failed to delete '%s'".formatted(key), e);
    }
  }

  @Override
  public void deleteAllForSchema(String schemaId) {
    var prefix = ObjectKeys.schemaPrefix(schemaId);
    deleteByPrefix(prefix);
  }

  @Override
  public void clearAll() {
    deleteByPrefix(null);
  }

  private void deleteByPrefix(String prefix) {
    try {
      var argsBuilder = ListObjectsArgs.builder().bucket(bucket).recursive(true);
      if (prefix != null) {
        argsBuilder.prefix(prefix);
      }
      var iter = client.listObjects(argsBuilder.build());
      for (var result : iter) {
        var item = result.get();
        client.removeObject(
            RemoveObjectArgs.builder().bucket(bucket).object(item.objectName()).build());
      }
    } catch (Exception e) {
      throw new StorageWriteException(
          "Failed to delete-by-prefix '%s'".formatted(prefix != null ? prefix : "<all>"), e);
    }
  }

  private void ensureBucket() {
    try {
      boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
      if (!exists) {
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      }
    } catch (ErrorResponseException e) {
      // Concurrent startup: another service created the bucket between our exists-check and
      // makeBucket call. The bucket exists and is ours — treat as success.
      if ("BucketAlreadyOwnedByYou".equals(e.errorResponse().code())) {
        return;
      }
      throw new StorageWriteException("Failed to ensure bucket '%s'".formatted(bucket), e);
    } catch (Exception e) {
      throw new StorageWriteException("Failed to ensure bucket '%s'".formatted(bucket), e);
    }
  }
}
