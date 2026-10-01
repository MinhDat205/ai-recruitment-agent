package com.recruitment.resume;

import com.recruitment.catalog.CatalogRegistry;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

// MOT cho duy nhat ghi ket qua trich xuat vao ResumeParsedData (FR-C05) - dung chung cho trich xuat lan
// dau (ResumeParsingStateService.markDone) va trich xuat lai (ResumeReparseStateService.markDone), de
// hai duong khong the lech nhau:
// - R-C3 "backend khong tin AI": industryCode la hoac OTHER -> null o CA JSON lan cot industry_code
//   (bat bien R-C4: cot = data.industryCode); locationText -> region_code qua bo khop R-M3, truot thi
//   ma null va GIU NGUYEN chuoi goc.
// - R-E9a: tinh so thang kinh nghiem trong CUNG transaction voi lan ghi (noi goi la method
//   @Transactional; bean nay khong mo transaction rieng).
// KHONG goi AI, KHONG kiem nguyen van currentTitle/locationText (R-C3, khong dung K2).
@Component
public class ResumeParsedDataEnricher {

    private final CatalogRegistry catalogRegistry;
    private final Clock clock;

    public ResumeParsedDataEnricher(CatalogRegistry catalogRegistry, Clock clock) {
        this.catalogRegistry = catalogRegistry;
        this.clock = clock;
    }

    // Ghi payload da qua kiem + cot truy van + cot kinh nghiem vao entity (chua save - noi goi save).
    public void applyExtraction(ResumeParsedData target, ResumeParsedPayload aiPayload) {
        ResumeParsedPayload payload = sanitize(aiPayload);
        target.setData(payload);
        target.setIndustryCode(payload.industryCode());
        target.setRegionCode(catalogRegistry.matchProvince(payload.locationText()));
        applyExperience(target, payload, clock.instant());
    }

    // R-C3 - chi industryCode bi thay; locationText giu nguyen van ke ca khi khong khop danh muc.
    ResumeParsedPayload sanitize(ResumeParsedPayload aiPayload) {
        String code = aiPayload.industryCode();
        boolean accepted = catalogRegistry.isIndustry(code)
                && !ResumeParsingService.EXCLUDED_INDUSTRY_CODE.equals(code);
        return accepted ? aiPayload : aiPayload.withIndustryCode(null);
    }

    // R-E2, R-E7: luu kem thoi diem tinh (moc tham chieu = thang cua thoi diem nay, gio VN).
    static void applyExperience(ResumeParsedData target, ResumeParsedPayload payload, Instant computedAt) {
        ExperienceCalculator.Result result =
                ExperienceCalculator.compute(payload.experience(), ExperienceCalculator.referenceMonth(computedAt));
        target.setExperienceMonths(result.months());
        target.setExperienceEntriesCounted(result.countedEntries());
        target.setExperienceEntriesSkipped(result.skippedEntries());
        target.setExperienceComputedAt(computedAt);
    }
}
