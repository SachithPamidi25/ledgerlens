package com.ledgerlens.receipt;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ReceiptRepository receiptRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getMonthlySummary(UUID userId, Integer year, Integer month) {
        List<Receipt> receipts;
        if (year != null && month != null) {
            YearMonth period = YearMonth.of(year, month);
            LocalDate fromDate = period.atDay(1);
            LocalDate toDate = period.plusMonths(1).atDay(1);
            receipts = receiptRepository.findCompletedForSummaryBetween(userId, fromDate, toDate);
        } else {
            receipts = receiptRepository.findCompletedForSummary(userId);
        }

        BigDecimal totalSpent = receipts.stream()
                .map(r -> r.getTotal() != null ? r.getTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> byCategory = receipts.stream()
                .filter(r -> r.getMerchantCategory() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getMerchantCategory().name(),
                        Collectors.reducing(BigDecimal.ZERO,
                                r -> r.getTotal() != null ? r.getTotal() : BigDecimal.ZERO,
                                BigDecimal::add)
                ));

        Map<String, BigDecimal> byMerchant = receipts.stream()
                .filter(r -> r.getVendor() != null)
                .collect(Collectors.groupingBy(
                        Receipt::getVendor,
                        Collectors.reducing(BigDecimal.ZERO,
                                r -> r.getTotal() != null ? r.getTotal() : BigDecimal.ZERO,
                                BigDecimal::add)
                ));

        return Map.of(
                "totalSpent", totalSpent,
                "receiptCount", receipts.size(),
                "byCategory", byCategory,
                "byMerchant", byMerchant,
                "period", year != null ? year + "-" + String.format("%02d", month) : "all-time"
        );
    }
}
