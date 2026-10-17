package com.recruitment.cvbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.recruitment.catalog.CatalogEntry;
import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.common.exception.CvBuilderTooLargeException;
import com.recruitment.cvbuilder.dto.CvBuilderRequest;
import com.recruitment.resume.PdfBoxPoiTextExtractor;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.resume.ResumeService;
import com.recruitment.storage.StorageService;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// FR-U12 T16b (R-P9, don vi) - CvBuilderService dung bang constructor package-private voi nguong nho; chuan hoa, kiem,
// dung PDF, trich van ban la that (font that), luu file va ghi DB la mock. PDF cua form co dinh co n byte: nguong n - 1 ->
// 400 CV_BUILDER_TOO_LARGE, 0 lan store / 0 lan CvBuilderStateService; nguong n, n + 1 -> qua. Phan "bean Spring dung
// ResumeService.MAX_RESUME_SIZE_BYTES" kiem tren bean that o CvBuilderCreateIntegrationTest.
class CvBuilderServiceSizeTest {

    private static final CatalogRegistry REGISTRY = new CatalogRegistry(
            List.of(new CatalogEntry("IT_SOFTWARE", "Công nghệ thông tin - Phần mềm", List.of())),
            List.of(new CatalogEntry("HA_NOI", "Hà Nội", List.of())));
    private static final CvPdfFonts FONTS = new CvPdfFonts();
    private static final CvTemplate TEMPLATE = CvTemplate.CLASSIC;

    private static long pdfSize;

    private final StorageService storageService = mock(StorageService.class);
    private final CvBuilderStateService stateService = mock(CvBuilderStateService.class);
    private final ResumeService resumeService = mock(ResumeService.class);

    @BeforeAll
    static void measurePdf() throws IOException {
        CvPdfRenderer renderer = new CvPdfRenderer(FONTS);
        CvBuilderForm form = CvBuilderIntegrationTestSupport.sampleForm();
        pdfSize = renderer.render(TEMPLATE, form).length;
        // Kich thuoc phai on dinh giua hai lan dung thi bien n moi co nghia.
        assertThat(renderer.render(TEMPLATE, form).length).isEqualTo(pdfSize);
    }

    private CvBuilderService service(long maxPdfBytes) {
        return new CvBuilderService(
                new CvBuilderFormNormalizer(),
                new CvBuilderFormValidator(REGISTRY, FONTS),
                new CvPdfRenderer(FONTS),
                new PdfBoxPoiTextExtractor(),
                storageService,
                stateService,
                mock(CvBuilderDocumentRepository.class),
                mock(ResumeRepository.class),
                resumeService,
                maxPdfBytes);
    }

    private static CvBuilderRequest request() {
        return new CvBuilderRequest(TEMPLATE, "Bản 1", CvBuilderIntegrationTestSupport.sampleForm());
    }

    private void stubWritePath() {
        UUID resumeId = UUID.randomUUID();
        when(storageService.store(anyString(), anyString(), any())).thenReturn("/uploads/resumes/x.pdf");
        when(stateService.create(any(), any(), any(), any(), anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(resumeId);
    }

    @Test
    void thresholdBelowSize_tooLarge_noStoreNoStateCall() {
        assertThatThrownBy(() -> service(pdfSize - 1).create(UUID.randomUUID(), request()))
                .isInstanceOf(CvBuilderTooLargeException.class)
                .hasMessage("File CV tạo ra vượt quá 10MB. Vui lòng rút gọn nội dung.");
        verifyNoInteractions(storageService, stateService);
    }

    @Test
    void thresholdBelowSize_previewAlsoTooLarge() {
        assertThatThrownBy(() -> service(pdfSize - 1).preview(request())).isInstanceOf(CvBuilderTooLargeException.class);
    }

    @Test
    void thresholdEqualToSize_passes() {
        stubWritePath();

        service(pdfSize).create(UUID.randomUUID(), request());

        verify(storageService, times(1)).store(anyString(), anyString(), any());
        verify(stateService, times(1))
                .create(any(), any(), any(), any(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void thresholdAboveSize_passes() {
        stubWritePath();

        service(pdfSize + 1).create(UUID.randomUUID(), request());

        verify(storageService, times(1)).store(anyString(), anyString(), any());
        verify(stateService, times(1))
                .create(any(), any(), any(), any(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void previewAtExactSize_returnsPdfOfThatSize() {
        assertThat(service(pdfSize).preview(request())).hasSize((int) pdfSize);
        verifyNoInteractions(storageService, stateService);
    }

    // Constructor @Autowired truyen dung ResumeService.MAX_RESUME_SIZE_BYTES (doc lai field bang reflection).
    @Test
    void autowiredConstructor_usesResumeMaxSize() throws Exception {
        Constructor<?> autowired = null;
        for (Constructor<?> constructor : CvBuilderService.class.getDeclaredConstructors()) {
            if (constructor.isAnnotationPresent(Autowired.class)) {
                autowired = constructor;
            }
        }
        assertThat(autowired).isNotNull();
        Object[] args = new Object[autowired.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            args[i] = mock(autowired.getParameterTypes()[i]);
        }
        Object instance = autowired.newInstance(args);
        Field field = CvBuilderService.class.getDeclaredField("maxPdfBytes");
        field.setAccessible(true);

        assertThat(field.getLong(instance)).isEqualTo(ResumeService.MAX_RESUME_SIZE_BYTES);
    }
}
