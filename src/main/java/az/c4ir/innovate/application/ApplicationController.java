package az.c4ir.innovate.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import az.c4ir.innovate.storage.FileStorageService;

import java.util.Map;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {
  private final ApplicationRepository repository;
  private final ObjectMapper objectMapper;
  private final FileStorageService fileStorageService;

  public ApplicationController(ApplicationRepository repository, ObjectMapper objectMapper, FileStorageService fileStorageService) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.fileStorageService = fileStorageService;
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
        FileStorageService.StoredFile storedFile = fileStorageService.storePdf(file);
        entity.setFileName(storedFile.fileName());
        entity.setFilePath(storedFile.storagePath());
        entity.setFileContentType(storedFile.contentType());
      }

      ApplicationEntity saved = repository.save(entity);
      return Map.of("id", saved.getId(), "submittedAt", saved.getSubmittedAt(), "status", "ok");
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not save application");
    }
  }

  private String firstString(Map<String, Object> payload, String... keys) {
    for (String key : keys) {
      Object value = payload.get(key);
      if (value instanceof String s && !s.isBlank()) return s.trim();
    }
    return null;
  }
}
