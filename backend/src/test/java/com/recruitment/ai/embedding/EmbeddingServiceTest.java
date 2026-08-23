package com.recruitment.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.resume.LlmTestConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// LlmTestConfiguration khong duoc nhan ban vao package nay - class da la public, import thang tu
// package resume la du. Mau y het CvImprovementServiceIntegrationTest/ScoreExplanationServiceIntegrationTest,
// chi khac o cho stub EmbeddingModel thay vi ChatModel (EmbeddingService khong qua ChatClient).
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class EmbeddingServiceTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void resetEmbeddingModelMock() {
        Mockito.reset(embeddingModel);
    }

    // vector[0] = 0.1f (KHONG de vector toan so 0) - tu khi EmbeddingService.toResult them guard
    // chan vector suy bien (xem yeu cau review sau Dot 5), mot response vector 0 se bi tu choi du
    // dung so chieu. Cac test dimension (1535/1537) van dung binh thuong vi kiem tra so chieu chay
    // TRUOC kiem tra suy bien, khong bao gio toi duoc nhanh nay.
    private EmbeddingResponse fakeResponse(int dimensions, String model) {
        float[] vector = new float[dimensions];
        if (dimensions > 0) {
            vector[0] = 0.1f;
        }
        Embedding embedding = new Embedding(vector, 0);
        return new EmbeddingResponse(List.of(embedding), new EmbeddingResponseMetadata(model, new DefaultUsage(10, 0)));
    }

    private EmbeddingResponse fakeZeroVectorResponse(String model) {
        Embedding embedding = new Embedding(new float[1536], 0);
        return new EmbeddingResponse(List.of(embedding), new EmbeddingResponseMetadata(model, new DefaultUsage(10, 0)));
    }

    @Test
    void embed_validDimension1536_returnsVectorAndModelFromMetadata() {
        doReturn(fakeResponse(1536, "text-embedding-3-small")).when(embeddingModel).embedForResponse(anyList());

        EmbeddingResult result = embeddingService.embed("noi dung mau");

        assertThat(result.vector()).hasSize(1536);
        assertThat(result.model()).isEqualTo("text-embedding-3-small");
    }

    // Bien duoi nguong: 1535 - thieu 1 chieu so voi schema vector(1536).
    @Test
    void embed_dimension1535_throwsInvalidDimension() {
        doReturn(fakeResponse(1535, "text-embedding-3-small")).when(embeddingModel).embedForResponse(anyList());

        EmbeddingFailedException exception =
                assertThrows(EmbeddingFailedException.class, () -> embeddingService.embed("noi dung mau"));

        assertThat(exception.errorCode()).isEqualTo(EmbeddingErrorCode.INVALID_DIMENSION);
    }

    // Bien tren nguong: 1537 - thua 1 chieu so voi schema vector(1536).
    @Test
    void embed_dimension1537_throwsInvalidDimension() {
        doReturn(fakeResponse(1537, "text-embedding-3-small")).when(embeddingModel).embedForResponse(anyList());

        EmbeddingFailedException exception =
                assertThrows(EmbeddingFailedException.class, () -> embeddingService.embed("noi dung mau"));

        assertThat(exception.errorCode()).isEqualTo(EmbeddingErrorCode.INVALID_DIMENSION);
    }

    @Test
    void embed_modelThrowsRuntimeException_mapsToApiErrorWithoutLeakingOriginalMessage() {
        String secretOriginalMessage = "loi ket noi toi OpenAI tai dia chi bi mat XYZ";
        doThrow(new RuntimeException(secretOriginalMessage)).when(embeddingModel).embedForResponse(anyList());

        EmbeddingFailedException exception =
                assertThrows(EmbeddingFailedException.class, () -> embeddingService.embed("noi dung mau"));

        assertThat(exception.errorCode()).isEqualTo(EmbeddingErrorCode.API_ERROR);
        assertThat(exception.getMessage()).doesNotContain(secretOriginalMessage);
    }

    @Test
    void embed_metadataModelBlank_fallsBackToConfiguredModel() {
        doReturn(fakeResponse(1536, "")).when(embeddingModel).embedForResponse(anyList());

        EmbeddingResult result = embeddingService.embed("noi dung mau");

        assertThat(result.model()).isEqualTo("text-embedding-3-small");
    }

    // Vector suy bien (toan so 0, dung 1536 chieu) - kiem tra DOC LAP voi kiem tra so chieu (dimension
    // dung nhung vector van bi tu choi). Bang chung thuc nghiem dan toi guard nay: da xac nhan tren
    // Postgres 17 that 'NaN'::float8 >= 0.4 tra ve TRUE - job co vector 0 se vuot moi nguong
    // similarity va xuat hien dau danh sach goi y cho MOI ung vien neu khong chan tai day (xem
    // comment EmbeddingService.toResult).
    @Test
    void embed_zeroVector_throwsZeroVector() {
        doReturn(fakeZeroVectorResponse("text-embedding-3-small")).when(embeddingModel).embedForResponse(anyList());

        EmbeddingFailedException exception =
                assertThrows(EmbeddingFailedException.class, () -> embeddingService.embed("noi dung mau"));

        assertThat(exception.errorCode()).isEqualTo(EmbeddingErrorCode.ZERO_VECTOR);
    }

    // --- EmbeddingTextFormat (Dot 2, sau review) - gop vao day thay vi file rieng, cung mot khai
    // niem "embedding" duoc kiem chung trong cung mot class test. ---

    // Mang 1536 phan tu THAT (khong phai 3 phan tu) - toVectorText luon validate du 1536 chieu
    // truoc khi serialize, mot mang ngan hon se nem INVALID_DIMENSION chu khong bao gio toi duoc
    // buoc dinh dang. Van kiem duoc dinh dang chinh xac o cac vi tri dau/cuoi biet truoc.
    @Test
    void toVectorText_knownValuesIn1536Array_producesBracketedCommaSeparatedText() {
        float[] vector = new float[1536];
        vector[0] = 0.1f;
        vector[1] = -0.2f;
        vector[2] = 0.3f;

        String text = EmbeddingTextFormat.toVectorText(vector);

        assertThat(text).startsWith("[0.1,-0.2,0.3,0.0,");
        assertThat(text).endsWith(",0.0]");
        assertThat(text.chars().filter(c -> c == ',').count()).isEqualTo(1535);
    }

    // Bien duoi nguong: 1535.
    @Test
    void toVectorText_dimension1535_throwsInvalidDimension() {
        EmbeddingFailedException exception =
                assertThrows(EmbeddingFailedException.class, () -> EmbeddingTextFormat.toVectorText(new float[1535]));

        assertThat(exception.errorCode()).isEqualTo(EmbeddingErrorCode.INVALID_DIMENSION);
    }

    // Bien tren nguong: 1537.
    @Test
    void toVectorText_dimension1537_throwsInvalidDimension() {
        EmbeddingFailedException exception =
                assertThrows(EmbeddingFailedException.class, () -> EmbeddingTextFormat.toVectorText(new float[1537]));

        assertThat(exception.errorCode()).isEqualTo(EmbeddingErrorCode.INVALID_DIMENSION);
    }
}
