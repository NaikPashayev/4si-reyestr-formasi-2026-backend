package az.c4ir.innovate.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {
  private final ApplicationRepository repository;
  private final ObjectMapper objectMapper;

  public ApplicationController(ApplicationRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> submit(@RequestBody Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty application");
    try {
      ApplicationEntity entity = new ApplicationEntity();
      entity.setPayloadJson(objectMapper.writeValueAsString(payload));
      entity.setApplicantName(firstString(payload, "fullName", "name", "applicantName", "founderName"));
      entity.setEmail(firstString(payload, "email", "mail"));
      entity.setPhone(firstString(payload, "phone", "mobile", "phoneNumber"));
      ApplicationEntity saved = repository.save(entity);
      return Map.of("id", saved.getId(), "submittedAt", saved.getSubmittedAt(), "status", "ok");
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
