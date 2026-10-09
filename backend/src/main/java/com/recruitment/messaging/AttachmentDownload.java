package com.recruitment.messaging;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

// M4 - khuon ResumeDownload: controller tu dat Content-Disposition: attachment (R-F7).
public record AttachmentDownload(Resource resource, String fileName, MediaType contentType) {}
