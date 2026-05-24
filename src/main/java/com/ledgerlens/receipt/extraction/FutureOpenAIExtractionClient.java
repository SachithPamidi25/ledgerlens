package com.ledgerlens.receipt.extraction;

/**
 * Placeholder adapter for a future OpenAI vision implementation.
 * Kept out of Spring wiring until the provider is actually configured.
 */
public class FutureOpenAIExtractionClient implements AiExtractionClient {

    @Override
    public ReceiptExtractionResult extractReceiptData(byte[] imageBytes) {
        throw new UnsupportedOperationException("OpenAI extraction provider is not configured yet");
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public String modelName() {
        return "future-openai-vision-model";
    }
}
