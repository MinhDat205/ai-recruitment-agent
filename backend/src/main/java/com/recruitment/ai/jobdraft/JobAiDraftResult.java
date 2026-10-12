package com.recruitment.ai.jobdraft;

// FR-H11 R-K4-2 - ket qua mot phan: payload da parse + validate, model (tu SyncAiResult.model() - metadata response)
// va promptVersion (hang PROMPT_VERSION cua phan do). Noi goi dung model + promptVersion de ky token K4; khong tra
// hai gia tri nay ra response J1 (R-K4-3).
public record JobAiDraftResult<T>(T payload, String model, String promptVersion) {
}
