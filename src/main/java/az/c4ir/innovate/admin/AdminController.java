package az.c4ir.innovate.admin;

import az.c4ir.innovate.application.ApplicationEntity;
import az.c4ir.innovate.application.ApplicationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import az.c4ir.innovate.storage.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private final ApplicationRepository repository;
  private final ObjectMapper objectMapper;
  private final String adminPassword;
  private final TokenService tokenService;
  private final FileStorageService fileStorageService;

  public AdminController(ApplicationRepository repository, ObjectMapper objectMapper, @Value("${app.admin.password}") String adminPassword, TokenService tokenService, FileStorageService fileStorageService) {
    this.repository = repository; this.objectMapper = objectMapper; this.adminPassword = adminPassword; this.tokenService = tokenService; this.fileStorageService = fileStorageService;
  }

  @PostMapping("/login")
  public Map<String, String> login(@RequestBody Map<String, String> body) {
    if (!Objects.equals(adminPassword, body.get("password"))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid password");
    return Map.of("token", tokenService.create());
  }

  @GetMapping("/applications")
  public List<Map<String, Object>> all(@RequestHeader(HttpHeaders.AUTHORIZATION) String auth) throws Exception {
    requireAdmin(auth);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (ApplicationEntity e : repository.findAllByOrderBySubmittedAtDesc()) {
      Map<String, Object> payload = objectMapper.readValue(e.getPayloadJson(), Map.class);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", e.getId());
      row.put("submittedAt", e.getSubmittedAt());
      row.put("status", e.getStatus());
      row.put("applicantName", Optional.ofNullable(e.getApplicantName()).orElse(""));
      row.put("email", Optional.ofNullable(e.getEmail()).orElse(""));
      row.put("phone", Optional.ofNullable(e.getPhone()).orElse(""));
      row.put("fileName", Optional.ofNullable(e.getFileName()).orElse(""));
      row.put("hasFile", e.getFilePath() != null && !e.getFilePath().isBlank());
      row.put("payload", payload);
      rows.add(row);
    }
    return rows;
  }

  @GetMapping("/applications/{id}/file")
  public ResponseEntity<Resource> file(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String auth,
      @PathVariable Long id
  ) {
    requireAdmin(auth);
    ApplicationEntity application = repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

    if (application.getFilePath() == null || application.getFilePath().isBlank()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No uploaded file found");
    }

    try {
      FileStorageService.DownloadedFile downloadedFile = fileStorageService.load(
          application.getFilePath(),
          Optional.ofNullable(application.getFileName()).filter(s -> !s.isBlank()).orElse("application-" + id + ".pdf"),
          application.getFileContentType()
      );

      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType(downloadedFile.contentType()))
          .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(downloadedFile.fileName()).build().toString())
          .body(downloadedFile.resource());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Uploaded file not found");
    }
  }

  @DeleteMapping("/applications/{id}")
  public Map<String, Object> delete(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String auth,
      @PathVariable Long id
  ) {
    requireAdmin(auth);

    if (!repository.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
    }

    repository.deleteById(id);

    return Map.of(
        "deleted", true,
        "id", id
    );
  }

  private void requireAdmin(String auth) {
    if (auth == null || !auth.startsWith("Bearer ") || !tokenService.valid(auth.substring(7))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin login required");
    }
  }
}
