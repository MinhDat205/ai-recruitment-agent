package com.recruitment.jobdraft.dto;

import com.recruitment.jobdraft.JobDraftPart;
import java.util.List;

// FR-H11 J1 (muc 4.4). parts KHONG chuan hoa null -> List.of(): null va rong deu la 400 o facade (R-N3), phan tu null
// phai den duoc facade (khong List.copyOf). Gia tri enum la -> body khong doc duoc -> 400 qua JobAiDraftExceptionAdvice.
public record JobAiDraftRequest(String description, List<JobDraftPart> parts) {
}
