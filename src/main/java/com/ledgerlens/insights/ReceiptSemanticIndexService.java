package com.ledgerlens.insights;

import com.ledgerlens.ai.LocalTextEmbeddingService;
import com.ledgerlens.receipt.MerchantCategory;
import com.ledgerlens.receipt.Receipt;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptSemanticIndexService {

    private final LocalTextEmbeddingService embeddingService;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${receipt.semantic-distance-threshold:0.55}")
    private double semanticDistanceThreshold;

    public void indexReceipt(Receipt receipt) {
        if (receipt == null || receipt.getId() == null || receipt.getUser() == null) {
            return;
        }

        String searchText = buildSearchText(receipt);
        String embedding = embeddingService.embedForPgVector(searchText);

        try {
            entityManager.createNativeQuery("""
                            INSERT INTO receipt_embedding (receipt_id, user_id, search_text, embedding, updated_at)
                            VALUES (:receiptId, :userId, :searchText, CAST(:embedding AS vector), now())
                            ON CONFLICT (receipt_id) DO UPDATE SET
                                user_id = EXCLUDED.user_id,
                                search_text = EXCLUDED.search_text,
                                embedding = EXCLUDED.embedding,
                                updated_at = now()
                            """)
                    .setParameter("receiptId", receipt.getId())
                    .setParameter("userId", receipt.getUser().getId())
                    .setParameter("searchText", searchText)
                    .setParameter("embedding", embedding)
                    .executeUpdate();
        } catch (RuntimeException e) {
            if (isMissingPgvectorPath(e)) {
                log.debug("Skipping receipt semantic index because pgvector path is unavailable");
                return;
            }
            throw e;
        }
    }

    public List<ReceiptSearchResult> search(UUID userId, String query, int limit) {
        String embedding = embeddingService.embedForPgVector(query);

        try {
            List<?> rows = entityManager.createNativeQuery("""
                            SELECT r.id,
                                   r.vendor,
                                   r.merchant_category,
                                   r.receipt_date,
                                   r.total,
                                   r.currency,
                                   re.search_text,
                                   re.embedding <=> CAST(:embedding AS vector) AS distance
                            FROM receipt_embedding re
                            JOIN receipts r ON r.id = re.receipt_id
                            WHERE re.user_id = :userId
                              AND r.status = 'COMPLETED'
                              AND re.embedding <=> CAST(:embedding AS vector) < :threshold
                            ORDER BY re.embedding <=> CAST(:embedding AS vector)
                            LIMIT :limit
                            """)
                    .setParameter("userId", userId)
                    .setParameter("embedding", embedding)
                    .setParameter("threshold", semanticDistanceThreshold)
                    .setParameter("limit", limit)
                    .getResultList();

            return rows.stream()
                    .map(row -> toSearchResult((Object[]) row))
                    .toList();
        } catch (RuntimeException e) {
            if (isMissingPgvectorPath(e)) {
                return List.of();
            }
            throw e;
        }
    }

    public void recordInsightSources(UUID userId, String question, String answer, List<ReceiptSearchResult> sources) {
        for (ReceiptSearchResult source : sources) {
            try {
                entityManager.createNativeQuery("""
                                INSERT INTO insight_source_receipt (user_id, receipt_id, question, answer_excerpt)
                                VALUES (:userId, :receiptId, :question, :answerExcerpt)
                                """)
                        .setParameter("userId", userId)
                        .setParameter("receiptId", source.receiptId())
                        .setParameter("question", question)
                        .setParameter("answerExcerpt", abbreviate(answer, 500))
                        .executeUpdate();
            } catch (RuntimeException e) {
                if (isMissingPgvectorPath(e)) {
                    return;
                }
                throw e;
            }
        }
    }

    String buildSearchText(Receipt receipt) {
        return String.join(" ",
                Optional.ofNullable(receipt.getVendor()).orElse("unknown merchant"),
                Optional.ofNullable(receipt.getMerchantCategory()).map(Enum::name).orElse("unknown category"),
                Optional.ofNullable(receipt.getReceiptDate()).map(Object::toString).orElse("unknown date"),
                Optional.ofNullable(receipt.getTotal()).map(BigDecimal::toPlainString).orElse("unknown amount"),
                Optional.ofNullable(receipt.getCurrency()).orElse("unknown currency"),
                Optional.ofNullable(receipt.getRawExtraction()).orElse("")
        ).trim();
    }

    private ReceiptSearchResult toSearchResult(Object[] row) {
        return new ReceiptSearchResult(
                UUID.fromString(row[0].toString()),
                (String) row[1],
                row[2] == null ? null : MerchantCategory.valueOf(row[2].toString()),
                row[3] == null ? null : ((Date) row[3]).toLocalDate(),
                (BigDecimal) row[4],
                (String) row[5],
                (String) row[6],
                ((Number) row[7]).doubleValue()
        );
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean isMissingPgvectorPath(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("receipt_embedding")
                    || message.contains("insight_source_receipt")
                    || message.contains("type \"vector\"")
                    || message.contains("operator does not exist")
                    || message.contains("relation \"receipt_embedding\""))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
