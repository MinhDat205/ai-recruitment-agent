package com.recruitment.cvbuilder.dto;

import com.recruitment.ai.cvrephrase.CvRephraseKind;

// B4 - than request (muc 4.5). kind la (khong thuoc enum) -> body khong doc duoc -> 400 INVALID_CV_REPHRASE_REQUEST.
public record CvRephraseRequest(CvRephraseKind kind, String text) {
}
