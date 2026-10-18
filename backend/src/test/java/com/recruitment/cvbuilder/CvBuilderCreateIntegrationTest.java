package com.recruitment.cvbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.recruitment.aicontext.CvQaContextAssembler;
import com.recruitment.common.exception.ResumeReparseNotAllowedException;
import com.recruitment.cvbuilder.dto.CvBuilderRequest;
import com.recruitment.resume.PdfBoxPoiTextExtractor;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeParsedData;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeParsingOrchestrator;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.resume.ResumeService;
import com.recruitment.resume.TextExtractor;
import com.recruitment.storage.StorageService;
import com.recruitment.user.CandidateProfileService;
import com.recruitment.user.dto.ResumeAutofillResponse;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-U12 T9, T10, T11, T12 phan ha nguon (L10), T15, T16 - B1 qua HTTP. Lop KHONG boc @Transactional (CLAUDE.md muc 3c):
// B1 luu file + transaction rieng cua CvBuilderStateService, rollback/race phai quan sat duoc tren DB that.
// Spy (goi that tru khi stub): CvBuilderDocumentRepository (T15 rollback), StorageService (bat gia tri store() tra ve),
// ResumeRepository (T15 race), TextExtractor (T16 - spy thay vi @MockitoBean de cac ca khac trong lop van trich that) ->
// lop nay co Spring context rieng.
class CvBuilderCreateIntegrationTest extends CvBuilderIntegrationTestSupport {

    @MockitoSpyBean
    CvBuilderDocumentRepository documentRepository;

    @MockitoSpyBean
    StorageService storageService;

    @MockitoSpyBean
    ResumeRepository resumeRepository;

    @MockitoSpyBean
    TextExtractor textExtractor;

    @Autowired
    ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    ResumeParsingOrchestrator resumeParsingOrchestrator;

    @Autowired
    CvQaContextAssembler cvQaContextAssembler;

    @Autowired
    CandidateProfileService candidateProfileService;

    @Autowired
    CvBuilderService cvBuilderService;

    @Autowired
    CvPdfRenderer renderer;

    private final PdfBoxPoiTextExtractor realExtractor = new PdfBoxPoiTextExtractor();

    // Gia tri store() tra ve trong test hien tai (L16).
    private final List<String> storedUrls = new ArrayList<>();
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void captureStoreAndLogs() {
        storedUrls.clear();
        doAnswer(invocation -> {
            String url = (String) invocation.callRealMethod();
            storedUrls.add(url);
            return url;
        }).when(storageService).store(anyString(), anyString(), any());
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(CvBuilderService.class)).addAppender(logAppender);
    }

    @AfterEach
    void resetSpies() {
        ((Logger) LoggerFactory.getLogger(CvBuilderService.class)).detachAppender(logAppender);
        reset(documentRepository, storageService, resumeRepository, textExtractor);
    }

    private List<String> warnings() {
        return logAppender.list.stream()
                .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private ResumeParsedData parsedOf(UUID resumeId) {
        return resumeParsedDataRepository.findByResumeId(resumeId).orElseThrow();
    }

    private String fileUrlOf(UUID resumeId) {
        return jdbcTemplate.queryForObject("SELECT file_url FROM resumes WHERE id = ?", String.class, resumeId);
    }

    // "/uploads/resumes/<uuid>.pdf" -> file that.
    private boolean storedUrlExists(String publicUrl) {
        return Files.exists(storageBase().resolve(publicUrl.substring("/uploads/".length())));
    }

    // ---- T9 ----

    @Test
    void t9_createsDoneResumeWithParsedDataAndDocument_noAiCall() throws Exception {
        Candidate c = registerCandidate("u12-t9");
        CvBuilderRequest request = sampleRequest(CvTemplate.CLASSIC);

        MvcResult result = createCv(c.token(), request);

        assertStatus(result, 201);
        JsonNode response = body(result);
        UUID resumeId = UUID.fromString(response.get("id").asString());
        assertThat(response.get("parseStatus").asString()).isEqualTo("DONE");
        assertThat(response.get("schemaVersion").asInt()).isEqualTo(2);
        assertThat(response.get("fromCvBuilder").asBoolean()).isTrue();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT file_type, parse_status, parse_error, claimed_at, next_attempt_at, attempt_count, file_size,"
                        + " file_name, version_label, file_url FROM resumes WHERE id = ?",
                resumeId);
        assertThat(row.get("file_type")).isEqualTo("PDF");
        assertThat(row.get("parse_status")).isEqualTo("DONE");
        assertThat(row.get("parse_error")).isNull();
        assertThat(row.get("claimed_at")).isNull();
        assertThat(row.get("next_attempt_at")).isNull();
        assertThat(((Number) row.get("attempt_count")).intValue()).isZero();
        assertThat(row.get("file_name")).isEqualTo("CV Nguyễn Văn An.pdf");
        assertThat(row.get("version_label")).isEqualTo("Bản tạo bằng công cụ");
        String key = (String) row.get("file_url");
        assertThat(key).matches("resumes/[0-9a-f-]{36}\\.pdf");
        assertThat(storedUrls).containsExactly("/" + "uploads/" + key);
        byte[] stored = Files.readAllBytes(storedFile(key));
        assertThat(((Number) row.get("file_size")).longValue()).isEqualTo(stored.length);

        MvcResult download = mockMvc.perform(get("/api/candidates/resumes/" + resumeId + "/download")
                        .header("Authorization", "Bearer " + c.token()))
                .andReturn();
        assertStatus(download, 200);
        assertThat(new String(download.getResponse().getContentAsByteArray(), 0, 4, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");

        ResumeParsedData parsed = parsedOf(resumeId);
        assertThat(parsed.getRawText())
                .isEqualTo(realExtractor.extract(new ByteArrayInputStream(stored), ResumeFileType.PDF));
        assertThat(parsed.getModel()).isEqualTo("cv-builder");
        assertThat(parsed.getPromptVersion()).isEqualTo("cv-builder-v1");
        assertThat(parsed.getTokenUsage()).isNull();
        assertThat(parsed.getData()).isEqualTo(new CvBuilderPayloadMapper().toPayload(sampleForm()));
        assertThat(parsed.getIndustryCode()).isEqualTo("IT_SOFTWARE");
        assertThat(parsed.getRegionCode()).isEqualTo("HA_NOI");
        assertThat(parsed.getExperienceEntriesCounted()).isEqualTo(1);
        assertThat(parsed.getExperienceEntriesSkipped()).isEqualTo(1);
        assertThat(parsed.getExperienceMonths()).isPositive();

        CvBuilderDocument document = documentRepository.findByResumeId(resumeId).orElseThrow();
        assertThat(document.getTemplate()).isEqualTo(CvTemplate.CLASSIC);
        assertThat(document.getFormData()).isEqualTo(sampleForm());

        assertNoAiCall();

        // Mot vong xu ly cua job trich xuat tren CV nay: claim that bai (khong PENDING) -> khong goi LLM, trang thai giu.
        assertThat(resumeRepository.findReadyForProcessing(Integer.MAX_VALUE))
                .extracting(r -> r.getId())
                .doesNotContain(resumeId);
        resumeParsingOrchestrator.processOne(resumeId);
        assertThat(jdbcTemplate.queryForObject("SELECT parse_status FROM resumes WHERE id = ?", String.class, resumeId))
                .isEqualTo("DONE");
        assertThat(jdbcTemplate.queryForObject("SELECT claimed_at IS NULL FROM resumes WHERE id = ?", Boolean.class,
                resumeId)).isTrue();
        assertNoAiCall();
    }

    // Form gui len chua chuan hoa -> form_data, payload va ten file deu la ban DA chuan hoa (R-F3).
    @Test
    void t9_storesNormalizedForm() throws Exception {
        Candidate c = registerCandidate("u12-t9-norm");
        CvBuilderForm raw = sampleForm();
        CvBuilderForm.Contact contact = raw.contact();
        CvBuilderForm messy = new CvBuilderForm(
                new CvBuilderForm.Contact("  Nguyễn Văn An\t", contact.email(), contact.phone(),
                        contact.address(), "   "),
                raw.currentTitle(), raw.locationText(), raw.industryCode(), raw.education(), raw.experience(),
                raw.skills(), raw.certifications(), raw.projects());

        UUID resumeId = createCvOk(c.token(), new CvBuilderRequest(CvTemplate.MODERN, "  Bản 2\r\n ", messy));

        assertThat(documentRepository.findByResumeId(resumeId).orElseThrow().getFormData()).isEqualTo(sampleForm());
        assertThat(parsedOf(resumeId).getData().contact().fullName()).isEqualTo("Nguyễn Văn An");
        assertThat(jdbcTemplate.queryForMap("SELECT file_name, version_label FROM resumes WHERE id = ?", resumeId))
                .containsEntry("file_name", "CV Nguyễn Văn An.pdf")
                .containsEntry("version_label", "Bản 2");
    }

    // ---- T10 ----

    @Test
    void t10_firstBuiltCvIsPrimary_secondIsNot() throws Exception {
        Candidate c = registerCandidate("u12-t10");

        UUID first = createCvOk(c.token(), sampleRequest(CvTemplate.CLASSIC));
        UUID second = createCvOk(c.token(), sampleRequest(CvTemplate.MODERN));

        assertThat(resumeRepository.findById(first).orElseThrow().isPrimary()).isTrue();
        assertThat(resumeRepository.findById(second).orElseThrow().isPrimary()).isFalse();
    }

    @Test
    void t10_candidateWithUploadedCv_builtCvNotPrimary() throws Exception {
        Candidate c = registerCandidate("u12-t10-up");
        UUID uploaded = uploadResume(c.token());

        MvcResult result = createCv(c.token(), sampleRequest(CvTemplate.CLASSIC));

        assertStatus(result, 201);
        assertThat(body(result).get("isPrimary").asBoolean()).isFalse();
        assertThat(resumeRepository.findById(uploaded).orElseThrow().isPrimary()).isTrue();
    }

    // Ban sua cua CV chinh dung tu form khong ke thua co (Q-U10).
    @Test
    void t10_editOfPrimaryBuiltCv_newIsNotPrimary_oldStaysPrimary() throws Exception {
        Candidate c = registerCandidate("u12-t10-edit");
        UUID original = createCvOk(c.token(), sampleRequest(CvTemplate.CLASSIC));
        JsonNode loaded = body(getDocument(c.token(), original));
        CvBuilderForm form = JSON.treeToValue(loaded.get("form"), CvBuilderForm.class);

        MvcResult edited = createCv(c.token(), new CvBuilderRequest(CvTemplate.MODERN, "Bản sửa", form));

        assertStatus(edited, 201);
        assertThat(body(edited).get("isPrimary").asBoolean()).isFalse();
        assertThat(resumeRepository.findById(original).orElseThrow().isPrimary()).isTrue();
    }

    // ---- T11 ----

    @Test
    void t11_editCreatesNewCv_oldRowsFileAndApplicationUnchanged() throws Exception {
        Candidate c = registerCandidate("u12-t11");
        UUID cvA = createCvOk(c.token(), sampleRequest(CvTemplate.CLASSIC));
        String hrToken = register("hr", uniqueEmail("u12-t11-hr"));
        String jobId = createOpenJob(hrToken);
        UUID applicationId = apply(c.token(), jobId, cvA);

        Map<String, Object> resumeBefore = jdbcTemplate.queryForMap("SELECT * FROM resumes WHERE id = ?", cvA);
        Map<String, Object> parsedBefore =
                jdbcTemplate.queryForMap("SELECT * FROM resume_parsed_data WHERE resume_id = ?", cvA);
        Map<String, Object> documentBefore =
                jdbcTemplate.queryForMap("SELECT * FROM cv_builder_documents WHERE resume_id = ?", cvA);
        byte[] fileBefore = Files.readAllBytes(storedFile(fileUrlOf(cvA)));

        CvBuilderForm loaded = JSON.treeToValue(body(getDocument(c.token(), cvA)).get("form"), CvBuilderForm.class);
        CvBuilderForm edited = new CvBuilderForm(loaded.contact(), "Trưởng nhóm phát triển", loaded.locationText(),
                loaded.industryCode(), loaded.education(), loaded.experience(), loaded.skills(),
                loaded.certifications(), loaded.projects());
        UUID cvB = createCvOk(c.token(), new CvBuilderRequest(CvTemplate.MODERN, "Bản sửa", edited));

        assertThat(cvB).isNotEqualTo(cvA);
        assertThat(documentRepository.findByResumeId(cvB).orElseThrow().getFormData().currentTitle())
                .isEqualTo("Trưởng nhóm phát triển");
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM resumes WHERE id = ?", cvA)).isEqualTo(resumeBefore);
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM resume_parsed_data WHERE resume_id = ?", cvA))
                .isEqualTo(parsedBefore);
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM cv_builder_documents WHERE resume_id = ?", cvA))
                .isEqualTo(documentBefore);
        assertThat(Files.readAllBytes(storedFile(fileUrlOf(cvA)))).isEqualTo(fileBefore);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT resume_id FROM job_applications WHERE id = ?", UUID.class, applicationId))
                .isEqualTo(cvA);
    }

    // ---- T12 phan ha nguon (L10) ----

    @Test
    void t12_downstreamTreatsBuiltCvLikeUploadedCv() throws Exception {
        Candidate c = registerCandidate("u12-t12");
        UUID resumeId = createCvOk(c.token(), sampleRequest(CvTemplate.CLASSIC));
        ResumeParsedData parsed = parsedOf(resumeId);

        // Embedding: CV chinh, embedding NULL -> job nen se nhat.
        assertThat(resumeParsedDataRepository.findIdsNeedingEmbedding(PageRequest.of(0, 100_000)))
                .contains(parsed.getId());
        // C08: ngu canh hoi dap la raw_text cua chinh CV nay.
        assertThat(cvQaContextAssembler.forCandidateResume(resumeId).resumeText()).isEqualTo(parsed.getRawText());
        // C05: trich xuat lai -> 409 "da la ban moi nhat" co san.
        MvcResult reparse = mockMvc.perform(post("/api/candidates/resumes/" + resumeId + "/reparse")
                        .header("Authorization", "Bearer " + c.token()))
                .andReturn();
        assertError(reparse, 409, "RESUME_REPARSE_NOT_ALLOWED",
                ResumeReparseNotAllowedException.alreadyLatest().getMessage());
        // U14: dien tu CV chinh.
        ResumeAutofillResponse autofill = candidateProfileService.autofillFromResume(c.id());
        assertThat(autofill.currentTitle()).isEqualTo("Lập trình viên Java");
        assertThat(autofill.skills()).containsExactly("Java", "Spring Boot", "PostgreSQL");
        assertNoAiCall();
    }

    // ---- T15 ----

    @Test
    void t15_documentSaveFails_rollsBackRowsAndDeletesStoredFile() throws Exception {
        Candidate c = registerCandidate("u12-t15");
        long filesBefore = countFiles("resumes");
        doThrow(new IllegalStateException("gia lap loi ghi cv_builder_documents"))
                .when(documentRepository)
                .saveAndFlush(any());

        // Loi khong co handler -> 500 mac dinh; MockMvc nem lai exception thay vi dung trang loi.
        assertThatThrownBy(() -> createCv(c.token(), sampleRequest(CvTemplate.CLASSIC)))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("gia lap loi ghi cv_builder_documents");

        assertThat(countResumesOf(c.id())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM resume_parsed_data d JOIN resumes r ON r.id = d.resume_id"
                                + " WHERE r.candidate_id = ?",
                        Long.class, c.id()))
                .isZero();
        assertThat(storedUrls).hasSize(1);
        String publicUrl = storedUrls.get(0);
        verify(storageService).delete(publicUrl);
        assertThat(storedUrlExists(publicUrl)).as("file da luu phai bi xoa").isFalse();
        assertThat(countFiles("resumes")).isEqualTo(filesBefore);
    }

    // R-W5 - existsByCandidateId tra false cho CA HAI request -> ca hai ghi is_primary = true; request thu hai bi
    // uq_resume_primary_per_candidate chan (xac dinh, khong phu thuoc thoi diem).
    @Test
    void t15_raceOnFirstCv_onePrimary201_one409_oneRowOneFile() throws Exception {
        Candidate c = registerCandidate("u12-t15-race");
        long filesBefore = countFiles("resumes");
        doReturn(false).when(resumeRepository).existsByCandidateId(eq(c.id()));

        MvcResult first = createCv(c.token(), sampleRequest(CvTemplate.CLASSIC));
        MvcResult second = createCv(c.token(), sampleRequest(CvTemplate.MODERN));

        assertStatus(first, 201);
        assertThat(body(first).get("isPrimary").asBoolean()).isTrue();
        assertError(second, 409, "CV_BUILDER_CONCURRENT_CREATE",
                "Đang có một CV khác được tạo cùng lúc. Vui lòng tải lại trang để xem danh sách CV.");
        assertThat(countResumesOf(c.id())).isEqualTo(1);
        assertThat(storedUrls).hasSize(2);
        assertThat(storedUrlExists(storedUrls.get(0))).isTrue();
        assertThat(storedUrlExists(storedUrls.get(1))).isFalse();
        verify(storageService).delete(storedUrls.get(1));
        assertThat(countFiles("resumes")).isEqualTo(filesBefore + 1);
    }

    // ---- T16 ----

    @Test
    void t16_extractedTextMissingOneField_500_noRowsNoFile_logsPathOnly() throws Exception {
        Candidate c = registerCandidate("u12-t16");
        long filesBefore = countFiles("resumes");
        String realText = realExtractor.extract(
                new ByteArrayInputStream(renderer.render(CvTemplate.CLASSIC, sampleForm())), ResumeFileType.PDF);
        String missingValue = "Bảo trì hệ thống nội bộ.";
        assertThat(realText).contains(missingValue);
        doReturn(realText.replace(missingValue, "")).when(textExtractor).extract(any(), any());

        MvcResult result = createCv(c.token(), sampleRequest(CvTemplate.CLASSIC));

        assertError(result, 500, "CV_BUILDER_RENDER_FAILED", "Không tạo được file CV, vui lòng thử lại.");
        assertThat(countResumesOf(c.id())).isZero();
        verify(storageService, never()).store(anyString(), anyString(), any());
        assertThat(countFiles("resumes")).isEqualTo(filesBefore);
        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0)).contains("experience[1].description").doesNotContain(missingValue);
    }

    @Test
    void t16_extractedTextEmpty_500_noRowsNoFile() throws Exception {
        Candidate c = registerCandidate("u12-t16-empty");
        long filesBefore = countFiles("resumes");
        doReturn("").when(textExtractor).extract(any(), any());

        MvcResult result = createCv(c.token(), sampleRequest(CvTemplate.CLASSIC));

        assertError(result, 500, "CV_BUILDER_RENDER_FAILED", "Không tạo được file CV, vui lòng thử lại.");
        assertThat(countResumesOf(c.id())).isZero();
        verify(storageService, never()).store(anyString(), anyString(), any());
        assertThat(countFiles("resumes")).isEqualTo(filesBefore);
        assertThat(warnings()).hasSize(1);
    }

    // Doi chung cua hai ca tren: trich van ban that -> khong canh bao nao.
    @Test
    void t16_realExtraction_noWarning() throws Exception {
        Candidate c = registerCandidate("u12-t16-ok");

        createCvOk(c.token(), sampleRequest(CvTemplate.MODERN));

        assertThat(warnings()).isEmpty();
    }

    // T16b phan bean: constructor Spring truyen dung nguong upload (doc lai qua reflection tren bean).
    @Test
    void t16b_springBeanUsesResumeMaxSize() throws Exception {
        CvBuilderService target = AopTestUtils.getTargetObject(cvBuilderService);
        Field field = CvBuilderService.class.getDeclaredField("maxPdfBytes");
        field.setAccessible(true);

        assertThat(field.getLong(target)).isEqualTo(ResumeService.MAX_RESUME_SIZE_BYTES);
    }
}
