package com.ledgerlens.merchant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantNormalizationService {

    private final MerchantRepository merchantRepository;

    @Value("${merchant.similarity-threshold:0.3}")
    private double similarityThreshold;

    public String normalize(String rawVendor) {
        if (rawVendor == null || rawVendor.isBlank()) {
            return rawVendor;
        }

        return merchantRepository.findBestMatch(rawVendor, similarityThreshold)
                .map(canonical -> {
                    log.info("Normalized '{}' → '{}'", rawVendor, canonical);
                    return canonical;
                })
                .orElseGet(() -> {
                    log.info("No match found for '{}', keeping original", rawVendor);
                    return rawVendor;
                });
    }
}