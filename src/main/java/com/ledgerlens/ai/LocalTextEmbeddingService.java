package com.ledgerlens.ai;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class LocalTextEmbeddingService {

    public static final int DIMENSIONS = 16;

    public String embedForPgVector(String text) {
        double[] vector = embed(text);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.6f", vector[i]));
        }
        return sb.append(']').toString();
    }

    public double[] embed(String text) {
        double[] vector = new double[DIMENSIONS];
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return vector;
        }

        String[] tokens = normalized.split("\\s+");
        for (String token : tokens) {
            addToken(vector, token, 1.0);
            for (int i = 0; i < token.length() - 2; i++) {
                addToken(vector, token.substring(i, i + 3), 0.35);
            }
        }

        normalizeUnitLength(vector);
        return vector;
    }

    private void addToken(double[] vector, String token, double weight) {
        int bucket = Math.floorMod(token.hashCode(), vector.length);
        vector[bucket] += weight;
    }

    private void normalizeUnitLength(double[] vector) {
        double sumSquares = 0;
        for (double value : vector) {
            sumSquares += value * value;
        }
        if (sumSquares == 0) {
            return;
        }
        double magnitude = Math.sqrt(sumSquares);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / magnitude;
        }
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
