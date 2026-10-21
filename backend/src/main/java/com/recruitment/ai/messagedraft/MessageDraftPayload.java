package com.recruitment.ai.messagedraft;

// FR-C07 - schema JSON co dinh cua output LLM (BeanOutputConverter qua AiOutputConverters). DUNG MOT field: ban nhap
// tieng Viet (R-D6 kiem o MessageDraftService.isValidDraft).
public record MessageDraftPayload(String draft) {
}
