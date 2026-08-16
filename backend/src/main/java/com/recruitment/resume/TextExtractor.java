package com.recruitment.resume;

import java.io.InputStream;

public interface TextExtractor {

    String extract(InputStream content, ResumeFileType fileType);
}
