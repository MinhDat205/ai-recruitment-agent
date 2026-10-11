package com.recruitment.messagedraft;

import com.recruitment.messagedraft.dto.DraftScenariosResponse;
import com.recruitment.messagedraft.dto.MessageDraftRequest;
import com.recruitment.messagedraft.dto.MessageDraftResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FR-C07 A1/A2 phia ung vien. /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o filter chain; quyen
// so huu don kiem o MessageDraftFacade qua findByIdAndCandidateId - don nguoi khac va don khong ton tai CUNG 404 (R-Q1).
// Vai tro nguoi soan = CANDIDATE suy tu duong dan nay (R-Q2).
@RestController
@RequestMapping("/api/candidates/applications/{applicationId}/messages/ai-draft")
public class MessageDraftCandidateController {

    private final MessageDraftFacade messageDraftFacade;

    public MessageDraftCandidateController(MessageDraftFacade messageDraftFacade) {
        this.messageDraftFacade = messageDraftFacade;
    }

    @GetMapping("/scenarios")
    public DraftScenariosResponse scenarios(Authentication authentication, @PathVariable UUID applicationId) {
        return messageDraftFacade.scenariosAsCandidate(UUID.fromString(authentication.getName()), applicationId);
    }

    // 200 (khong 201) - khong tao gi.
    @PostMapping
    public MessageDraftResponse draft(
            Authentication authentication,
            @PathVariable UUID applicationId,
            @RequestBody MessageDraftRequest request) {
        return messageDraftFacade.draftAsCandidate(UUID.fromString(authentication.getName()), applicationId, request);
    }
}
