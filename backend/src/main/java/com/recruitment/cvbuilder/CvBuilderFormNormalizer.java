package com.recruitment.cvbuilder;

import java.text.Normalizer;
import java.util.List;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

// FR-U12 R-F3 - chuan hoa moi chuoi cua form TRUOC khi kiem, dung PDF va luu form_data. Khong doi hoa/thuong, khong sua
// chinh ta, khong gop khoang trang ben trong (K2 tu gop khi kiem). Chuoi rong sau strip -> null; o bat buoc do validator
// bao loi. Phan tu null cua danh sach giu nguyen (validator bao loi - R-F5), danh sach giu nguyen thu tu.
@Component
public class CvBuilderFormNormalizer {

    // null giu null (validator R-F10 bao CV_BUILDER_EMPTY).
    public CvBuilderForm normalize(CvBuilderForm form) {
        if (form == null) {
            return null;
        }
        CvBuilderForm.Contact contact = form.contact();
        CvBuilderForm.Contact normalizedContact = contact == null
                ? null
                : new CvBuilderForm.Contact(
                        singleLine(contact.fullName()),
                        singleLine(contact.email()),
                        singleLine(contact.phone()),
                        singleLine(contact.address()),
                        singleLine(contact.linkedin()));
        return new CvBuilderForm(
                normalizedContact,
                singleLine(form.currentTitle()),
                singleLine(form.locationText()),
                singleLine(form.industryCode()),
                mapKeepingNulls(form.education(), this::normalizeEducation),
                mapKeepingNulls(form.experience(), this::normalizeExperience),
                mapKeepingNulls(form.skills(), this::singleLine),
                mapKeepingNulls(form.certifications(), this::normalizeCertification),
                mapKeepingNulls(form.projects(), this::normalizeProject));
    }

    // O mot dong: moi o tru hai o description (gom versionLabel - R-B1).
    public String singleLine(String value) {
        return normalize(value, false);
    }

    // O nhieu dong: experience[].description, projects[].description.
    public String multiLine(String value) {
        return normalize(value, true);
    }

    private CvBuilderForm.Education normalizeEducation(CvBuilderForm.Education e) {
        return new CvBuilderForm.Education(
                singleLine(e.school()),
                singleLine(e.degree()),
                singleLine(e.major()),
                singleLine(e.startDate()),
                singleLine(e.endDate()),
                e.gpa());
    }

    private CvBuilderForm.Experience normalizeExperience(CvBuilderForm.Experience e) {
        return new CvBuilderForm.Experience(
                singleLine(e.company()),
                singleLine(e.title()),
                singleLine(e.startDate()),
                singleLine(e.endDate()),
                e.current(),
                multiLine(e.description()));
    }

    private CvBuilderForm.Certification normalizeCertification(CvBuilderForm.Certification c) {
        return new CvBuilderForm.Certification(singleLine(c.name()), singleLine(c.issuer()), singleLine(c.issueDate()));
    }

    private CvBuilderForm.Project normalizeProject(CvBuilderForm.Project p) {
        return new CvBuilderForm.Project(
                singleLine(p.name()), multiLine(p.description()), mapKeepingNulls(p.technologies(), this::singleLine));
    }

    // Stream.toList() chap nhan phan tu null (List.of()/Collectors khong dung duoc o day).
    private static <T> List<T> mapKeepingNulls(List<T> items, UnaryOperator<T> mapper) {
        return items.stream().map(item -> item == null ? null : mapper.apply(item)).toList();
    }

    private static String normalize(String value, boolean multiLine) {
        if (value == null) {
            return null;
        }
        // 1. NFC - tieng Viet dung san, de glyph va K2 khop (K2 khong NFC).
        String s = Normalizer.normalize(value, Normalizer.Form.NFC);
        // 2. CRLF, CR -> LF.
        s = s.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\t' || Character.getType(ch) == Character.SPACE_SEPARATOR) {
                // 3. tab va moi khoang trang Unicode loai Zs (gom U+00A0, U+202F) -> dau cach thuong.
                sb.append(' ');
            } else if (ch == '\n' && !multiLine) {
                // 4. o mot dong: xuong dong -> dau cach.
                sb.append(' ');
            } else {
                sb.append(ch);
            }
        }
        // 5. strip hai dau; rong -> null.
        String stripped = sb.toString().strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
