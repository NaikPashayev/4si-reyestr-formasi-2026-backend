package az.c4ir.innovate.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@Service
public class FileStorageService {
  private final Path localUploadDir;
  private final String r2Endpoint;
  private final String r2Bucket;
  private final String r2AccessKeyId;
  private final String r2SecretAccessKey;
  private final String r2Region;

  public FileStorageService(
      @Value("${app.storage.local-upload-dir:/data/uploads}") String localUploadDir,
      @Value("${app.storage.r2.endpoint:}") String r2Endpoint,
      @Value("${app.storage.r2.bucket:}") String r2Bucket,
      @Value("${app.storage.r2.access-key-id:}") String r2AccessKeyId,
      @Value("${app.storage.r2.secret-access-key:}") String r2SecretAccessKey,
      @Value("${app.storage.r2.region:auto}") String r2Region
  ) {
    this.localUploadDir = Paths.get(localUploadDir);
    this.r2Endpoint = trim(r2Endpoint);
    this.r2Bucket = trim(r2Bucket);
    this.r2AccessKeyId = trim(r2AccessKeyId);
    this.r2SecretAccessKey = trim(r2SecretAccessKey);
    this.r2Region = trim(r2Region).isBlank() ? "auto" : trim(r2Region);
  }

  public StoredFile storePdf(MultipartFile file) throws IOException {
    String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "application.pdf" : file.getOriginalFilename());
    String contentType = Optional.ofNullable(file.getContentType()).filter(s -> !s.isBlank()).orElse(MediaType.APPLICATION_PDF_VALUE);

    boolean pdfContentType = MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(contentType);
    boolean pdfExtension = originalName.toLowerCase().endsWith(".pdf");
    if (!pdfContentType && !pdfExtension) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files are allowed");
    }

    String objectName = UUID.randomUUID() + ".pdf";
    if (isR2Configured()) {
      String key = "applications/" + objectName;
      s3Client().putObject(
          PutObjectRequest.builder()
              .bucket(r2Bucket)
              .key(key)
              .contentType(contentType)
              .contentDisposition("attachment; filename=\"" + safeHeaderFileName(originalName) + "\"")
              .build(),
          RequestBody.fromInputStream(file.getInputStream(), file.getSize())
      );
      return new StoredFile(originalName, "r2://" + key, contentType);
    }

    Files.createDirectories(localUploadDir);
    Path savedPath = localUploadDir.resolve(objectName).normalize();
    Files.copy(file.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);
    return new StoredFile(originalName, savedPath.toString(), contentType);
  }

  public DownloadedFile load(String storedPath, String originalName, String contentType) throws IOException {
    if (storedPath == null || storedPath.isBlank()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No uploaded file found");
    }

    String finalContentType = Optional.ofNullable(contentType).filter(s -> !s.isBlank()).orElse(MediaType.APPLICATION_PDF_VALUE);
    String finalName = Optional.ofNullable(originalName).filter(s -> !s.isBlank()).orElse("application.pdf");

    if (storedPath.startsWith("r2://")) {
      if (!isR2Configured()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File storage is not configured");
      }
      String key = storedPath.substring("r2://".length());
      try {
        ResponseInputStream<GetObjectResponse> stream = s3Client().getObject(
            GetObjectRequest.builder().bucket(r2Bucket).key(key).build()
        );
        String r2Type = Optional.ofNullable(stream.response().contentType()).filter(s -> !s.isBlank()).orElse(finalContentType);
        return new DownloadedFile(new InputStreamResource(stream), finalName, r2Type);
      } catch (NoSuchKeyException e) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Uploaded file not found");
      }
    }

    Path path = Paths.get(storedPath).normalize();
    if (!Files.exists(path) || !Files.isRegularFile(path)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Uploaded file not found");
    }
    return new DownloadedFile(new org.springframework.core.io.UrlResource(path.toUri()), finalName, finalContentType);
  }

  private boolean isR2Configured() {
    return !r2Endpoint.isBlank() && !r2Bucket.isBlank() && !r2AccessKeyId.isBlank() && !r2SecretAccessKey.isBlank();
  }

  private S3Client s3Client() {
    return S3Client.builder()
        .endpointOverride(URI.create(r2Endpoint))
        .region(Region.of(r2Region))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(r2AccessKeyId, r2SecretAccessKey)))
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .forcePathStyle(true)
        .build();
  }

  private static String trim(String value) {
    return value == null ? "" : value.trim();
  }

  private static String safeHeaderFileName(String fileName) {
    return fileName.replace("\\", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_");
  }

  public record StoredFile(String fileName, String storagePath, String contentType) {}
  public record DownloadedFile(Resource resource, String fileName, String contentType) {}
}
