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

// FR-C07 A1/A2 phia HR. /api/hr/** da bi SecurityConfig chan hasRole("HR") o filter chain; quyen so huu don kiem o
// MessageDraftFacade qua HrApplicationAccess (R-Q1). Vai tro nguoi soan = HR suy tu duong dan nay (R-Q2).
// Endpoint nam trong package rieng, KHONG thuoc messaging/ (messaging/ khong import ai/ - muc 4.2).
@RestController
@RequestMapping("/api/hr/applications/{applicationId}/messages/ai-draft")
public class MessageDraftHrController {

    private final MessageDraftFacade messageDraftFacade;

    public MessageDraftHrController(MessageDraftFacade messageDraftFacade) {
        this.messageDraftFacade = messageDraftFacade;
    }

    @GetMapping("/scenarios")
    public DraftScenariosResponse scenarios(Authentication authentication, @PathVariable UUID applicationId) {
        return messageDraftFacade.scenariosAsHr(UUID.fromString(authentication.getName()), applicationId);
    }

    // 200 (khong 201) - khong tao gi.
    @PostMapping
    public MessageDraftResponse draft(
            Authentication authentication,
            @PathVariable UUID applicationId,
            @RequestBody MessageDraftRequest request) {
        return messageDraftFacade.draftAsHr(UUID.fromString(authentication.getName()), applicationId, request);
    }
}
