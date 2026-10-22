package com.recruitment.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.core.JacksonException;

// fix/llm-json-control-chars - test thuan Java, khong Spring context. Mapper dung chung chi THEM mot feature
// (ALLOW_UNESCAPED_CONTROL_CHARS) so voi mapper mac dinh cua BeanOutputConverter; moi hanh vi khac giu nguyen.
class AiOutputConvertersTest {

    public record Quote(String quote, String section) {
    }

    public record Scored(Double score, List<Quote> evidence) {
    }

    private static String quoteJson(String quote) {
        return "{\"quote\": \"" + quote + "\", \"section\": \"experience\"}";
    }

    // ---- Case duong ----

    @ParameterizedTest
    @ValueSource(strings = {"Nhân viên Hành chính\t03/2023 – nay", "dong1\rdong2", "dong1\ndong2", "dong1\r\ndong2"})
    void forType_unescapedControlCharInString_parsesVerbatim(String quote) {
        Quote result = AiOutputConverters.forType(Quote.class).convert(quoteJson(quote));

        assertThat(result.quote()).isEqualTo(quote);
        assertThat(result.section()).isEqualTo("experience");
    }

    @Test
    void forType_escapedTab_stillParsesToTab() {
        Quote result = AiOutputConverters.forType(Quote.class).convert(quoteJson("A\\tB"));

        assertThat(result.quote()).isEqualTo("A\tB");
    }

    @Test
    void forType_unknownProperty_isIgnoredLikeDefaultConverter() {
        Quote result = AiOutputConverters.forType(Quote.class)
                .convert("{\"quote\": \"A\", \"section\": \"skills\", \"confidence\": 0.9}");

        assertThat(result.quote()).isEqualTo("A");
    }

    @Test
    void forType_markdownCodeFence_isStillCleanedByDefaultTextCleaner() {
        Quote result = AiOutputConverters.forType(Quote.class).convert("```json\n" + quoteJson("A\tB") + "\n```");

        assertThat(result.quote()).isEqualTo("A\tB");
    }

    @Test
    void forType_nestedList_parsesNumbersAndControlChars() {
        Scored result = AiOutputConverters.forType(Scored.class)
                .convert("{\"score\": 3.5, \"evidence\": [" + quoteJson("X\tY") + "]}");

        assertThat(result.score()).isEqualTo(3.5);
        assertThat(result.evidence()).extracting(Quote::quote).containsExactly("X\tY");
    }

    // {format} gui trong prompt khong doi - schema khong phu thuoc mapper.
    @Test
    void forType_formatAndSchema_identicalToDefaultConverter() {
        BeanOutputConverter<Scored> shared = AiOutputConverters.forType(Scored.class);
        BeanOutputConverter<Scored> defaultConverter = new BeanOutputConverter<>(Scored.class);

        assertThat(shared.getFormat()).isEqualTo(defaultConverter.getFormat());
        assertThat(shared.getJsonSchema()).isEqualTo(defaultConverter.getJsonSchema());
    }

    // Moc doi chieu: chinh mapper mac dinh tu choi TAB tho - ly do ton tai cua AiOutputConverters.
    @Test
    void defaultConverter_unescapedTab_throwsJacksonException() {
        BeanOutputConverter<Quote> defaultConverter = new BeanOutputConverter<>(Quote.class);

        assertThatThrownBy(() -> defaultConverter.convert(quoteJson("A\tB"))).isInstanceOf(JacksonException.class);
    }

    // ---- Case am: JSON hong that van nem JacksonException -> luong thu lai/LLM_INVALID_JSON giu nguyen ----

    @Test
    void forType_notJson_throwsJacksonException() {
        assertThatThrownBy(() -> AiOutputConverters.forType(Quote.class).convert("day khong phai JSON hop le"))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void forType_unclosedObject_throwsJacksonException() {
        assertThatThrownBy(() -> AiOutputConverters.forType(Quote.class).convert("{\"quote\": \"A\tB\""))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void forType_unescapedQuoteInsideString_throwsJacksonException() {
        assertThatThrownBy(() -> AiOutputConverters.forType(Quote.class).convert(quoteJson("noi \"A\" ok")))
                .isInstanceOf(JacksonException.class);
    }
}
