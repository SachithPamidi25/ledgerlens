package com.ledgerlens.merchant;

import com.ledgerlens.ai.LocalTextEmbeddingService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantEmbeddingServiceTest {

    private final MerchantEmbeddingService embeddingService =
            new MerchantEmbeddingService(new LocalTextEmbeddingService());

    @Test
    void embedForPgVector_returnsStablePgVectorLiteral() {
        String embedding = embeddingService.embedForPgVector("SQ *STARBUCKS #4821");

        assertThat(embedding)
                .isEqualTo("[0.304276,0.152138,0.000000,0.434680,0.000000,0.304276,0.434680,0.152138,0.000000,0.000000,0.000000,0.152138,0.000000,0.152138,0.586818,0.000000]");
    }

    @Test
    void embed_normalizesSemanticallySimilarMerchantTextCloseEnoughForSeededAlias() {
        String aliasEmbedding = embeddingService.embedForPgVector("AMZN MKTP IN");

        assertThat(aliasEmbedding)
                .isEqualTo("[0.535288,0.187351,0.000000,0.000000,0.000000,0.535288,0.187351,0.187351,0.000000,0.000000,0.535288,0.000000,0.000000,0.000000,0.187351,0.000000]");
    }

    @Test
    void embedForPgVector_returnsZeroVectorForBlankText() {
        String embedding = embeddingService.embedForPgVector("   ");

        assertThat(embedding)
                .isEqualTo("[0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000,0.000000]");
    }
}
