package com.recruitment.blindreview;

import java.util.UUID;

// FR-H16 R-M1 - interface projection: cac gia tri contact trong resume_parsed_data.data cua CV ma don da nop.
public interface ParsedContactRow {

    UUID getApplicationId();

    String getFullName();

    String getEmail();

    String getPhone();

    String getAddress();

    String getLinkedin();
}
