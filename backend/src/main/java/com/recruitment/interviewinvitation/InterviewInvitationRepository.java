package com.recruitment.interviewinvitation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewInvitationRepository extends JpaRepository<InterviewInvitation, UUID> {

    List<InterviewInvitation> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
