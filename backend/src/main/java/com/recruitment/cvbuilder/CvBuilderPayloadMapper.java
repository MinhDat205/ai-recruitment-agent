package com.recruitment.cvbuilder;

import com.recruitment.resume.ResumeParsedPayload;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;

// FR-U12 R-M1, R-M2 - form DA chuan hoa va DA qua validator -> "CV da trich xuat". Xac dinh, khong goi AI, moi chuoi chep
// NGUYEN VAN. Ket qua di qua ResumeParsedDataEnricher.applyExtraction o ResumeParsingStateService.insertBuiltResume
// (R-M3) nhu CV tai len - mapper khong tu kiem nganh/khop khu vuc/tinh kinh nghiem.
@Component
public class CvBuilderPayloadMapper {

    // R-M2 - cung chuoi in ra PDF (R-P5); ExperienceCalculator nhan vi bo dau ra "hien tai" (a17).
    public static final String CURRENT_END_DATE = "Hiện tại";

    public ResumeParsedPayload toPayload(CvBuilderForm form) {
        return new ResumeParsedPayload(
                toContact(form.contact()),
                map(form.education(), CvBuilderPayloadMapper::toEducation),
                map(form.experience(), CvBuilderPayloadMapper::toExperience),
                List.copyOf(form.skills()),
                map(form.certifications(), CvBuilderPayloadMapper::toCertification),
                map(form.projects(), CvBuilderPayloadMapper::toProject),
                form.currentTitle(),
                form.industryCode(),
                form.locationText());
    }

    // Ca 5 null -> contact = null (quy uoc "khong co thong tin lien he" cua payload - a9).
    private static ResumeParsedPayload.Contact toContact(CvBuilderForm.Contact c) {
        if (c == null
                || (c.fullName() == null
                        && c.email() == null
                        && c.phone() == null
                        && c.address() == null
                        && c.linkedin() == null)) {
            return null;
        }
        return new ResumeParsedPayload.Contact(c.fullName(), c.email(), c.phone(), c.address(), c.linkedin());
    }

    private static ResumeParsedPayload.Education toEducation(CvBuilderForm.Education e) {
        return new ResumeParsedPayload.Education(
                e.school(), e.degree(), e.major(), e.startDate(), e.endDate(), e.gpa());
    }

    private static ResumeParsedPayload.Experience toExperience(CvBuilderForm.Experience e) {
        String endDate = Boolean.TRUE.equals(e.current()) ? CURRENT_END_DATE : e.endDate();
        return new ResumeParsedPayload.Experience(e.company(), e.title(), e.startDate(), endDate, e.description());
    }

    private static ResumeParsedPayload.Certification toCertification(CvBuilderForm.Certification c) {
        return new ResumeParsedPayload.Certification(c.name(), c.issuer(), c.issueDate());
    }

    private static ResumeParsedPayload.Project toProject(CvBuilderForm.Project p) {
        return new ResumeParsedPayload.Project(p.name(), p.description(), List.copyOf(p.technologies()));
    }

    private static <S, T> List<T> map(List<S> items, Function<S, T> mapper) {
        return items.stream().map(mapper).toList();
    }
}
