package az.c4ir.innovate.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class TokenService {
  private final String secret;
  private final long ttlHours;
  public TokenService(@Value("${app.admin.token-secret}") String secret, @Value("${app.admin.token-ttl-hours}") long ttlHours) {
    this.secret = secret; this.ttlHours = ttlHours;
  }
  public String create() {
  try {
    long exp = Instant.now().plusSeconds(ttlHours * 3600).getEpochSecond();
    String body = "admin:" + exp;
    return b64(body) + "." + sign(body);
  } catch (Exception e) {
    throw new RuntimeException("Could not create admin token", e);
  }
}
  public boolean valid(String token) {
  try {
    if (token == null || token.isBlank()) return false;

    String[] p = token.split("\\.", 2);
    if (p.length != 2) return false;

    String body = new String(Base64.getUrlDecoder().decode(p[0]), StandardCharsets.UTF_8);
    if (!sign(body).equals(p[1])) return false;

    long exp = Long.parseLong(body.split(":", 2)[1]);
    return Instant.now().getEpochSecond() < exp;
  } catch (Exception e) {
    return false;
  }
}
  private String sign(String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }
  private String b64(String v) { return Base64.getUrlEncoder().withoutPadding().encodeToString(v.getBytes(StandardCharsets.UTF_8)); }
}
