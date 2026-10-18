package com.recruitment.ai.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.ai.embedding.EmbeddingResult;
import com.recruitment.common.exception.AiSyncErrorCode;
import com.recruitment.common.exception.AiSyncFailedException;
import com.recruitment.resume.LlmTestConfiguration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// FR-U13 T2 - can transaction manager THAT nen dung Spring context (Testcontainers). SyncEmbeddingCaller lay tu context
// (bean cua AiSyncExecutorConfig) - kiem luon viec noi day va khoa app.ai-sync.embedding-timeout-ms cua profile test
// (2000; production 5000). Khang dinh tren LUONG GOI: goi trong TransactionTemplate -> IllegalStateException, 0 lan
// goi EmbeddingModel.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class SyncEmbeddingCallerTransactionIntegrationTest {

    @Autowired
    private SyncEmbeddingCaller syncEmbeddingCaller;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${app.ai-sync.embedding-timeout-ms}")
    private long embeddingTimeoutMs;

    @BeforeEach
    void resetEmbeddingModelMock() {
        Mockito.reset(embeddingModel);
    }

    private static EmbeddingResponse validResponse() {
        float[] vector = new float[1536];
        vector[0] = 0.1f;
        return new EmbeddingResponse(
                List.of(new Embedding(vector, 0)),
                new EmbeddingResponseMetadata("text-embedding-3-small", new DefaultUsage(10, 0)));
    }

    @Test
    void embed_insideTransaction_throwsIllegalState_andNeverCallsEmbeddingModel() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                        status -> syncEmbeddingCaller.embed("noi dung")))
                .isInstanceOf(IllegalStateException.class);
        verify(embeddingModel, never()).embedForResponse(anyList());
    }

    @Test
    void embed_outsideTransaction_runsNormally_withOneCall() {
        doReturn(validResponse()).when(embeddingModel).embedForResponse(anyList());

        EmbeddingResult result = syncEmbeddingCaller.embed("noi dung");

        assertThat(result.vector()).hasSize(1536);
        assertThat(result.model()).isEqualTo("text-embedding-3-small");
        verify(embeddingModel, times(1)).embedForResponse(anyList());
    }

    // Bean doc dung han cua profile test: khoa = 2000, va lan goi cho latch het han trong khoang [2000, 5000) - tuc la
    // dung 2000 cua application-test.yml, khong phai 5000 cua application.yml.
    @Test
    void embed_beanUsesTestProfileTimeout_timesOutAfterAbout2s() {
        assertThat(embeddingTimeoutMs).isEqualTo(2000L);
        CountDownLatch neverReleased = new CountDownLatch(1);
        doAnswer(invocation -> {
            neverReleased.await(10, TimeUnit.SECONDS);
            return validResponse();
        }).when(embeddingModel).embedForResponse(anyList());

        try {
            long start = System.nanoTime();
            assertThatThrownBy(() -> syncEmbeddingCaller.embed("noi dung"))
                    .isInstanceOf(AiSyncFailedException.class)
                    .satisfies(e -> assertThat(((AiSyncFailedException) e).errorCode())
                            .isEqualTo(AiSyncErrorCode.AI_TIMEOUT));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(elapsedMs).isBetween(1900L, 4900L);
        } finally {
            neverReleased.countDown();
        }
    }
}
