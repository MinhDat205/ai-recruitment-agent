package com.recruitment.ai.embedding;

// NOI DUY NHAT sinh chuoi vector dinh dang pgvector ("[v1,v2,...]") trong toan du an - moi noi goi
// JobEmbeddingRepository.upsertEmbedding/ResumeParsedDataRepository.updateEmbedding deu phai di qua
// day, KHONG tu viet String.join/StringBuilder rieng o noi khac (de dinh dang co the lech nhau giua
// cac noi goi - vd thieu dau phay, sai ky tu ngoac). Validate SO CHIEU ngay o day (khong phai o
// repository): repository la API cong khai, chuoi sai dinh dang hoac sai so chieu di thang xuong
// Postgres se nem loi CAST kho doc va xa noi gay loi that su (xem yeu cau review Dot 2).
public final class EmbeddingTextFormat {

    private EmbeddingTextFormat() {
    }

    public static String toVectorText(float[] vector) {
        if (vector.length != EmbeddingService.EXPECTED_DIMENSIONS) {
            throw new EmbeddingFailedException(EmbeddingErrorCode.INVALID_DIMENSION);
        }
        StringBuilder text = new StringBuilder(vector.length * 8);
        text.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(vector[i]);
        }
        return text.append(']').toString();
    }
}
