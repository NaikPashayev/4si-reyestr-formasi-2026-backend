package az.c4ir.innovate.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {
  private static final Path UPLOAD_DIR = Paths.get("/data/uploads");

  private final ApplicationRepository repository;
  private final ObjectMapper objectMapper;

  public ApplicationController(ApplicationRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> submit(@RequestBody Map<String, Object> payload) {
    return saveApplication(payload, null);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> submitMultipart(
      @RequestPart("payload") String payloadJson,
      @RequestPart(value = "file", required = false) MultipartFile file
  ) {
    try {
      Map<String, Object> payload = objectMapper.readValue(payloadJson, new TypeReference<>() {});
      return saveApplication(payload, file);
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid application payload");
    }
  }

  private Map<String, Object> saveApplication(Map<String, Object> payload, MultipartFile file) {
    if (payload == null || payload.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty application");
    try {
      ApplicationEntity entity = new ApplicationEntity();
      entity.setPayloadJson(objectMapper.writeValueAsString(payload));
      entity.setApplicantName(firstString(payload, "fullName", "name", "applicantName", "founderName"));
      entity.setEmail(firstString(payload, "email", "mail"));
      entity.setPhone(firstString(payload, "phone", "mobile", "phoneNumber"));

      if (file != null && !file.isEmpty()) {
        storePdf(file, entity);
      }

      ApplicationEntity saved = repository.save(entity);
      return Map.of("id", saved.getId(), "submittedAt", saved.getSubmittedAt(), "status", "ok");
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not save application");
    }
  }

  private void storePdf(MultipartFile file, ApplicationEntity entity) throws IOException {
    String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "application.pdf" : file.getOriginalFilename());
    String contentType = file.getContentType();

    boolean pdfContentType = contentType != null && contentType.equalsIgnoreCase(MediaType.APPLICATION_PDF_VALUE);
    boolean pdfExtension = originalName.toLowerCase().endsWith(".pdf");
    if (!pdfContentType && !pdfExtension) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files are allowed");
    }

    Files.createDirectories(UPLOAD_DIR);
    String savedName = UUID.randomUUID() + ".pdf";
    Path savedPath = UPLOAD_DIR.resolve(savedName).normalize();
    Files.copy(file.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);

    entity.setFileName(originalName);
    entity.setFilePath(savedPath.toString());
    entity.setFileContentType(contentType == null || contentType.isBlank() ? MediaType.APPLICATION_PDF_VALUE : contentType);
  }

  private String firstString(Map<String, Object> payload, String... keys) {
    for (String key : keys) {
      Object value = payload.get(key);
      if (value instanceof String s && !s.isBlank()) return s.trim();
    }
    return null;
  }
}
