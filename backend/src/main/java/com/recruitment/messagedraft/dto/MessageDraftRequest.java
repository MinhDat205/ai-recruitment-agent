package com.recruitment.messagedraft.dto;

import com.recruitment.ai.messagedraft.DraftScenario;
import com.recruitment.ai.messagedraft.DraftTone;

// FR-C07 A2 (muc 4.4). scenario/tone GIU kieu enum (muc 8): gia tri la/JSON hong bi Spring tu choi o buoc doc body ->
// 400 INVALID_DRAFT_REQUEST qua MessageDraftExceptionAdvice (ngoai le R-Q3b). Thieu field -> null, kiem theo thu tu
// R-Q3 o MessageDraftFacade. customPurpose chi dung khi scenario = CUSTOM (R-S4).
public record MessageDraftRequest(DraftScenario scenario, DraftTone tone, String customPurpose) {
}
