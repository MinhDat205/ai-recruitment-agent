package com.recruitment.resume;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record ResumeDownload(Resource resource, String fileName, MediaType contentType) {
}
