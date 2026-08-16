package com.recruitment.resume;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ResumeParsingService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParsingService.class);

    // Nguong do dai rawText truoc khi dua vao prompt goi LLM. Khong gioi han o TextExtractor
    // (Luot 2) vi do la tang trich xuat, phai day du de luu DB/audit - cat chi ap dung cho BAN SAO
    // gui LLM, raw_text luu DB van la ban day du.
    static final int MAX_PROMPT_CHARS = 45_000;

    private static final String TRUNCATION_MARKER = "\n\n[...phan giua CV da duoc luoc bot do do dai...]\n\n";

    // Giu dau + cuoi, cat phan giua - KHONG cat cut duoi: muc "Du an" va "Chung chi" (2/6 khoi
    // trong schema) gan nhu luon nam o cuoi CV, cat cut duoi se mat dung hai khoi do. 60% dau
    // (lien he, hoc van, kinh nghiem - thuong nam truoc) + 40% cuoi (du an, chung chi - thuong nam
    // sau). Job nen tu chay voi input nguoi dung tai len - mot file duoc dat ten "CV" nhung thuc
    // chat la tai lieu rat dai (vd luan van) khong duoc phep dot het quota API cho mot lan goi.
    String truncateForPrompt(String rawText, UUID resumeId) {
        if (rawText.length() <= MAX_PROMPT_CHARS) {
            return rawText;
        }
        // Chi ghi resumeId va do dai goc, KHONG ghi noi dung CV - du lieu ca nhan khong duoc vao
        // log o muc thuong.
        log.warn("Cat bot rawText truoc khi goi LLM do vuot nguong: resumeId={}, doDaiGoc={}", resumeId, rawText
                .length());

        int keepLength = MAX_PROMPT_CHARS - TRUNCATION_MARKER.length();
        int headLength = (int) (keepLength * 0.6);
        int tailLength = keepLength - headLength;

        String head = rawText.substring(0, headLength);
        String tail = rawText.substring(rawText.length() - tailLength);
        return head + TRUNCATION_MARKER + tail;
    }
}
