package com.recruitment.interviewinvitation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewInvitationSlotRepository extends JpaRepository<InterviewInvitationSlot, UUID> {

    List<InterviewInvitationSlot> findByInvitationIdOrderByDisplayOrderAsc(UUID invitationId);
}
