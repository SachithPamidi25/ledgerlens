package com.ledgerlens.merchant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantNormalizationServiceTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private MerchantEmbeddingService merchantEmbeddingService;
    @Mock private MerchantVectorRepository merchantVectorRepository;

    @InjectMocks
    private MerchantNormalizationService normalizationService;

    @Test
    void normalize_prefersVectorMatch() {
        when(merchantEmbeddingService.embedForPgVector("SQ *STARBUCKS #4821")).thenReturn("[vector]");
        when(merchantVectorRepository.findNearest("[vector]")).thenReturn(Optional.of("Starbucks"));

        assertThat(normalizationService.normalize("SQ *STARBUCKS #4821")).isEqualTo("Starbucks");
        verify(merchantRepository, never()).findBestMatch(anyString(), anyDouble());
    }

    @Test
    void normalize_fallsBackToTrigramWhenVectorMisses() {
        when(merchantEmbeddingService.embedForPgVector("AMZN MKTP IN")).thenReturn("[vector]");
        when(merchantVectorRepository.findNearest("[vector]")).thenReturn(Optional.empty());
        when(merchantRepository.findBestMatch(anyString(), anyDouble())).thenReturn(Optional.of("Amazon"));

        assertThat(normalizationService.normalize("AMZN MKTP IN")).isEqualTo("Amazon");
    }

    @Test
    void normalize_keepsRawVendorWhenNoMatchFound() {
        when(merchantEmbeddingService.embedForPgVector("Unknown Store")).thenReturn("[vector]");
        when(merchantVectorRepository.findNearest("[vector]")).thenReturn(Optional.empty());
        when(merchantRepository.findBestMatch(anyString(), anyDouble())).thenReturn(Optional.empty());

        assertThat(normalizationService.normalize("Unknown Store")).isEqualTo("Unknown Store");
    }

    @Test
    void normalize_returnsBlankOrNullWithoutLookup() {
        assertThat(normalizationService.normalize(null)).isNull();
        assertThat(normalizationService.normalize("   ")).isEqualTo("   ");

        verifyNoInteractions(merchantEmbeddingService, merchantVectorRepository, merchantRepository);
    }
}
