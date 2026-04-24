package az.c4ir.innovate.application;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, Long> {
  List<ApplicationEntity> findAllByOrderBySubmittedAtDesc();
}
