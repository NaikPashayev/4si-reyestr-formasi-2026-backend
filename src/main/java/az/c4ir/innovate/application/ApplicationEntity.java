package az.c4ir.innovate.application;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "applications")
public class ApplicationEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false, updatable = false)
  private Instant submittedAt = Instant.now();
  @Column(columnDefinition = "CLOB", nullable = false)
  private String payloadJson;
  private String applicantName;
  private String email;
  private String phone;
  private String status = "NEW";

  public Long getId() { return id; }
  public Instant getSubmittedAt() { return submittedAt; }
  public String getPayloadJson() { return payloadJson; }
  public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
  public String getApplicantName() { return applicantName; }
  public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
