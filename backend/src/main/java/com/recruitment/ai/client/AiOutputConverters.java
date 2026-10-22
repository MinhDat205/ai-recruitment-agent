package com.recruitment.ai.client;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.util.JacksonUtils;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

// Noi DUY NHAT tao BeanOutputConverter cho output LLM (D1, D2, D4, F2, K3) - khong "new BeanOutputConverter<>(" o noi
// khac.
//
// Ly do (fix/llm-json-control-chars, 21/10/2026): CV DOCX co tab stop ("Nhan vien Hanh chinh<TAB>03/2023 - nay"), LLM
// chep nguyen van ky tu TAB vao chuoi JSON KHONG escape -> mapper mac dinh nem StreamReadException "Illegal unquoted
// character ((CTRL-CHAR, code 9))" ca hai lan -> LLM_INVALID_JSON. Khong chuan hoa raw_text o D1 (giu nguyen van CV) -
// noi long parser thay vao do.
//
// Mapper = DUNG mapper mac dinh cua BeanOutputConverter.getJsonMapper() (javap -c spring-ai-model-2.0.0: addModules(
// JacksonUtils.instantiateAvailableModules()) + disable(FAIL_ON_UNKNOWN_PROPERTIES)) + MOT feature
// ALLOW_UNESCAPED_CONTROL_CHARS (javap jackson-core-3.1.4). Constructor (Class, JsonMapper) truyen cleaner = null ->
// van dung createDefaultTextCleaner(); schema ({format} trong prompt) sinh tu
// JsonSchemaGenerator.generateForType(type), khong phu thuoc mapper - prompt khong doi. JsonMapper bat bien, an toan
// dung chung giua cac luong.
public final class AiOutputConverters {

    private static final JsonMapper AI_OUTPUT_MAPPER = JsonMapper.builder()
            .addModules(JacksonUtils.instantiateAvailableModules())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .build();

    private AiOutputConverters() {}

    public static <T> BeanOutputConverter<T> forType(Class<T> type) {
        return new BeanOutputConverter<>(type, AI_OUTPUT_MAPPER);
    }
}
