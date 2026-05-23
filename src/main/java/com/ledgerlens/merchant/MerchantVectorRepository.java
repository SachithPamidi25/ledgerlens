package com.ledgerlens.merchant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MerchantVectorRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${merchant.vector-distance-threshold:0.45}")
    private double vectorDistanceThreshold;

    public Optional<String> findNearest(String embedding) {
        try {
            List<?> rows = entityManager.createNativeQuery("""
                            SELECT m.canonical_name
                            FROM merchant_embedding me
                            JOIN merchants m ON m.id = me.merchant_id
                            WHERE me.embedding <=> CAST(:embedding AS vector) < :threshold
                            ORDER BY me.embedding <=> CAST(:embedding AS vector)
                            LIMIT 1
                            """)
                    .setParameter("embedding", embedding)
                    .setParameter("threshold", vectorDistanceThreshold)
                    .getResultList();

            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of((String) rows.getFirst());
        } catch (RuntimeException e) {
            if (isMissingPgvectorPath(e)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private boolean isMissingPgvectorPath(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("merchant_embedding")
                    || message.contains("type \"vector\"")
                    || message.contains("operator does not exist")
                    || message.contains("relation \"merchant_embedding\""))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
