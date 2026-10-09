package com.recruitment.messaging;

import com.recruitment.common.dto.PageResponse;
import com.recruitment.messaging.dto.ConversationCandidateResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// FR-C06 M5 phia ung vien - hop thu "Tin nhan". /api/candidates/** chan hasRole("CANDIDATE") o filter chain;
// chi tra don co candidate_id = nguoi goi (R-Q6), ten ben kia la TEN CONG TY (R-I2). Chi doc (R-I4).
@RestController
@RequestMapping("/api/candidates/messages/conversations")
public class ConversationCandidateController {

    private final MessageService messageService;

    public ConversationCandidateController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public PageResponse<ConversationCandidateResponse> list(
            Authentication authentication,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return messageService.listConversationsAsCandidate(UUID.fromString(authentication.getName()), page, size);
    }
}
