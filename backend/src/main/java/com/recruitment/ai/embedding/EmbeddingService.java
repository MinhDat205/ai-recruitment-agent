package com.recruitment.ai.embedding;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Doc lap hoan toan voi tang persistence - khong repository, khong entity, khong @Transactional -
// cung nguyen tac voi CriterionScoringService/CvImprovementService/ScoreExplanationService. Dung
// embedForResponse (KHONG dung embed(String) don thuan) de lay duoc ca EmbeddingResponseMetadata
// (ten model that) - cung tinh than .responseEntity() vs .entity() da ghi o CLAUDE.md 3b, khong
// duoc "vut mat" metadata.
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    // Khop vector(1536) cua job_embeddings/resume_parsed_data (V1__init_schema.sql) va
    // spring.ai.vectorstore.pgvector.dimensions=1536 trong application.yml. AI hay lam sai (PHASES.md
    // F1): "Dung so chieu khac 1536 ma khong sua schema -> loi runtime" - kiem tra tuong minh o day
    // de that bai som, ro rang, thay vi de INSERT vao Postgres nem loi kieu du lieu kho hieu hon.
    // Package-private (khong private): EmbeddingTextFormat.toVectorText cung dung chung hang so nay
    // de validate TRUOC khi serialize, tranh hai noi co hai gia tri 1536 hardcode rieng co the lech.
    static final int EXPECTED_DIMENSIONS = 1536;

    private final EmbeddingModel embeddingModel;
    private final String configuredModel;

    public EmbeddingService(
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String configuredModel) {
        this.embeddingModel = embeddingModel;
        this.configuredModel = configuredModel;
    }

    // Goi API DUNG MOT lan, KHONG retry (khac CvImprovementService/ScoreExplanationService - hai
    // service do retry vi loi thuong la JSON hong, co the tu sua o lan goi lai; o day dau ra la vector
    // so thuan tuy tu API, khong co khai niem "JSON khong hop le" can retry - loi API la loi API, de
    // lan poll ke tiep tu nhien thu lai, xem Plan Mode F1 muc A).
    public EmbeddingResult embed(String text) {
        EmbeddingResponse response;
        try {
            response = embeddingModel.embedForResponse(List.of(text));
        } catch (RuntimeException e) {
            log.warn("Goi API embedding that bai: loai={}", e.getClass().getSimpleName());
            throw new EmbeddingFailedException(EmbeddingErrorCode.API_ERROR, e);
        }
        return toResult(response);
    }

    private EmbeddingResult toResult(EmbeddingResponse response) {
        float[] vector = response.getResult().getOutput();
        if (vector.length != EXPECTED_DIMENSIONS) {
            log.warn("Embedding tra ve sai so chieu: ky vong={}, thuc te={}", EXPECTED_DIMENSIONS, vector.length);
            throw new EmbeddingFailedException(EmbeddingErrorCode.INVALID_DIMENSION);
        }

        // Chan vector suy bien (toan so 0) NGAY TAI DAY - day la noi DUY NHAT moi vector di qua
        // truoc khi vao DB, chan o day thi khong con noi nao khac phai tu lo lieu nua. He qua neu
        // KHONG chan: vector 0 co do dai (norm) bang 0, cosine distance voi bat ky vector nao khac
        // la 0/0 = NaN. Postgres xu ly NaN cho float8 KHAC chuan IEEE754- NaN duoc coi la LON HON
        // moi so khac (da kiem chung thuc nghiem tren Postgres 17 that: 'NaN'::float8 >= 0.4 tra ve
        // true). Ket qua: mot job co vector suy bien se VUOT MOI NGUONG similarity va xuat hien dau
        // danh sach goi y cho MOI ung vien - sai hoan toan, va rat kho phat hien vi khong co loi ro
        // rang o tang goi API (van la mot cau tra loi 200 OK hop le, chi la du lieu suy bien).
        // Dung tong binh phuong (khong phai kiem tung phan tu == 0) - tuong duong ve mat toan hoc
        // voi "moi phan tu bang 0" nhung mot phep kiem duy nhat, khong can vong lap rieng.
        double sumOfSquares = 0.0;
        for (float value : vector) {
            sumOfSquares += (double) value * value;
        }
        if (sumOfSquares == 0.0) {
            log.warn("Embedding tra ve la vector suy bien (toan so 0)");
            throw new EmbeddingFailedException(EmbeddingErrorCode.ZERO_VECTOR);
        }

        // model la fallback "gia tri thay the khi metadata thieu" - cung mau CvImprovementService.toResult.
        String model = response.getMetadata() != null ? response.getMetadata().getModel() : null;
        if (model == null || model.isBlank()) {
            model = configuredModel;
        }
        return new EmbeddingResult(vector, model);
    }
}
