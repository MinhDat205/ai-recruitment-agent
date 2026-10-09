package com.recruitment.messaging;

import com.recruitment.common.dto.PageResponse;
import com.recruitment.messaging.dto.ConversationHrResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// FR-C06 M5 phia HR - hop thu "Tin nhan". /api/hr/** chan hasRole("HR") o filter chain; chi tra don vao tin
// cua cong ty nguoi goi (R-Q6). Chi doc, khong danh dau da doc (R-I4).
@RestController
@RequestMapping("/api/hr/messages/conversations")
public class ConversationHrController {

    private final MessageService messageService;

    public ConversationHrController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public PageResponse<ConversationHrResponse> list(
            Authentication authentication,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return messageService.listConversationsAsHr(UUID.fromString(authentication.getName()), page, size);
    }
}
