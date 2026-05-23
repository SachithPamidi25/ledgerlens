package com.ledgerlens.merchant;

import com.ledgerlens.ai.LocalTextEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantEmbeddingService {

    static final int DIMENSIONS = LocalTextEmbeddingService.DIMENSIONS;

    private final LocalTextEmbeddingService localTextEmbeddingService;

    public String embedForPgVector(String text) {
        return localTextEmbeddingService.embedForPgVector(text);
    }

    double[] embed(String text) {
        return localTextEmbeddingService.embed(text);
    }
}
