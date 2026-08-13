package com.recruitment.jobapplication;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationStatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, UUID> {

    List<ApplicationStatusHistory> findByApplicationIdOrderByChangedAtAsc(UUID applicationId);
}
