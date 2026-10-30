package com.recruitment.reconsideration;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.ApplicationStatusService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// FR-C09 T13 + Plan Mode L6 (khuon AiOutputConvertersSourceGuardTest) - quet nguon, KHONG Spring context:
// - canh rieng REJECTED -> INTERVIEW_INVITED (R-S2) chi duoc goi tu InterviewInvitationService;
// - bang chuyen tiep chung khong co khoa REJECTED (kiem ca van ban lan gia tri that bang reflection);
// - ApplicationStatus dung 5 gia tri;
// - han dung '168 hours', khong '7 days' (R-T1).
// Duong dan nguon tinh tu thu muc lam viec cua Surefire (backend/).
class ReconsiderationSourceGuardTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final String REINVITE_METHOD = "reinviteAfterReconsideration";
    private static final Set<Path> REINVITE_ALLOWED = Set.of(
            MAIN_SOURCES.resolve("com/recruitment/jobapplication/ApplicationStatusService.java"),
            MAIN_SOURCES.resolve("com/recruitment/interviewinvitation/InterviewInvitationService.java"));
    private static final Path STATUS_SERVICE =
            MAIN_SOURCES.resolve("com/recruitment/jobapplication/ApplicationStatusService.java");

    private static List<Path> mainSources() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    // T13 - ten method R-S2 chi xuat hien o noi khai bao va noi goi duy nhat.
    @Test
    void reinviteMethod_onlyInStatusServiceAndInvitationService() throws IOException {
        List<Path> sources = mainSources();
        assertThat(sources).hasSizeGreaterThan(100).containsAll(REINVITE_ALLOWED);

        List<Path> mentioning = sources.stream()
                .filter(source -> read(source).contains(REINVITE_METHOD))
                .toList();

        assertThat(mentioning).containsExactlyInAnyOrderElementsOf(REINVITE_ALLOWED);
        assertThat(read(MAIN_SOURCES.resolve("com/recruitment/interviewinvitation/InterviewInvitationService.java")))
                .contains("applicationStatusService." + REINVITE_METHOD + "(");
    }

    // T13 - khoi khai bao ALLOWED_TRANSITIONS (tu "ALLOWED_TRANSITIONS = Map.of(" toi ");" dau tien) khong co khoa REJECTED.
    @Test
    void allowedTransitionsDeclaration_hasNoRejectedKey() {
        String source = read(STATUS_SERVICE);
        int start = source.indexOf("ALLOWED_TRANSITIONS = Map.of(");
        assertThat(start).as("khong tim thay khai bao ALLOWED_TRANSITIONS").isNotNegative();
        String declaration = source.substring(start, source.indexOf(");", start) + 2);

        assertThat(declaration).contains("ApplicationStatus.PENDING, Set.of").doesNotContain("REJECTED, Set.of");
    }

    // L6 - gia tri that cua bang (chong truong hop khai bao bi viet lai theo kieu khac ma kiem van ban bo lot).
    @Test
    @SuppressWarnings("unchecked")
    void allowedTransitionsValue_keysArePendingAndInterviewInvitedOnly() {
        Map<ApplicationStatus, Set<ApplicationStatus>> transitions = (Map<ApplicationStatus, Set<ApplicationStatus>>)
                ReflectionTestUtils.getField(ApplicationStatusService.class, "ALLOWED_TRANSITIONS");

        assertThat(transitions).isNotNull();
        assertThat(transitions.keySet())
                .containsExactlyInAnyOrder(ApplicationStatus.PENDING, ApplicationStatus.INTERVIEW_INVITED);
        assertThat(transitions.get(ApplicationStatus.PENDING))
                .containsExactlyInAnyOrder(ApplicationStatus.INTERVIEW_INVITED, ApplicationStatus.REJECTED);
        assertThat(transitions.get(ApplicationStatus.INTERVIEW_INVITED))
                .containsExactlyInAnyOrder(ApplicationStatus.HIRED, ApplicationStatus.REJECTED);
    }

    // T13 - khong trang thai thu 6.
    @Test
    void applicationStatus_hasExactlyFiveValues() {
        assertThat(ApplicationStatus.values())
                .containsExactly(
                        ApplicationStatus.PENDING,
                        ApplicationStatus.INTERVIEW_INVITED,
                        ApplicationStatus.HIRED,
                        ApplicationStatus.REJECTED,
                        ApplicationStatus.WITHDRAWN);
    }

    // L6 - R-T1: dinh nghia han duy nhat dung '168 hours'; khong dong code nao (bo qua comment) trong goi reconsideration/
    // dung '7 days'.
    @Test
    void deadline_uses168Hours_never7Days() throws IOException {
        assertThat(ReconsiderationRequestRepository.PENDING_REJECTED_LATERAL).contains("INTERVAL '168 hours'");

        List<Path> packageSources = mainSources().stream()
                .filter(p -> p.startsWith(MAIN_SOURCES.resolve("com/recruitment/reconsideration")))
                .toList();
        assertThat(packageSources).isNotEmpty();
        for (Path source : packageSources) {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (!isComment(trimmed)) {
                    assertThat(trimmed.toLowerCase()).as(source + ":" + (i + 1)).doesNotContain("'7 days'");
                }
            }
        }
    }

    private static boolean isComment(String trimmed) {
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }

    private static String read(Path source) {
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
