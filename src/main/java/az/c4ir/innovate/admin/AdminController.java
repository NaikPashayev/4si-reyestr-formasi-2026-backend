package az.c4ir.innovate.admin;

import az.c4ir.innovate.application.ApplicationEntity;
import az.c4ir.innovate.application.ApplicationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private final ApplicationRepository repository;
  private final ObjectMapper objectMapper;
  private final String adminPassword;
  private final TokenService tokenService;

  public AdminController(ApplicationRepository repository, ObjectMapper objectMapper, @Value("${app.admin.password}") String adminPassword, TokenService tokenService) {
    this.repository = repository; this.objectMapper = objectMapper; this.adminPassword = adminPassword; this.tokenService = tokenService;
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
      rows.add(Map.of(
        "id", e.getId(), "submittedAt", e.getSubmittedAt(), "status", e.getStatus(),
        "applicantName", Optional.ofNullable(e.getApplicantName()).orElse(""),
        "email", Optional.ofNullable(e.getEmail()).orElse(""),
        "phone", Optional.ofNullable(e.getPhone()).orElse(""),
        "payload", payload
      ));
    }
    return rows;
  }

  private void requireAdmin(String auth) {
    if (auth == null || !auth.startsWith("Bearer ") || !tokenService.valid(auth.substring(7))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin login required");
    }
  }
}
