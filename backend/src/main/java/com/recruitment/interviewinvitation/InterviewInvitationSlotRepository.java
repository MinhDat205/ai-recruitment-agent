package com.recruitment.interviewinvitation;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewInvitationSlotRepository extends JpaRepository<InterviewInvitationSlot, UUID> {

    List<InterviewInvitationSlot> findByInvitationIdOrderByDisplayOrderAsc(UUID invitationId);

    // FR-H12 I1 - khung cua nhieu giay moi trong mot truy van; nhom theo invitation_id o service.
    List<InterviewInvitationSlot> findByInvitationIdInOrderByDisplayOrderAsc(Collection<UUID> invitationIds);
}
