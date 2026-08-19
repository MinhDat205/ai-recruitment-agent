package com.recruitment.resume;

public record ResumeParsingResult(ResumeParsedPayload payload, String model, Integer tokenUsage, String promptVersion) {
}
