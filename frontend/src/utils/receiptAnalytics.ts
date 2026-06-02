import type { CurrencyCode, ExpenseCard, ReceiptStatusFilter, Totals } from "../appTypes";
import type { InsightsResponse, MonthlySummary, Receipt, ReceiptStatus, SummaryItem } from "../types";
import { titleCase } from "./format";

export function buildReceiptMonthOptions(receipts: Receipt[]) {
  const counts = new Map<string, number>();
  receipts.forEach((receipt) => {
    const key = receiptMonthKey(receipt);
    counts.set(key, (counts.get(key) ?? 0) + 1);
  });

  return Array.from(counts.entries())
    .sort(([a], [b]) => b.localeCompare(a))
    .map(([key, count]) => ({ key, count, label: formatMonthLabel(key) }));
}

export function buildExpenseCards(receipts: Receipt[], mode: "monthly" | "yearly", currency: CurrencyCode): ExpenseCard[] {
  const completedReceipts = receipts.filter((receipt) => receipt.status === "COMPLETED");
  const grouped = new Map<string, Receipt[]>();

  completedReceipts.forEach((receipt) => {
    const key = mode === "monthly" ? receiptMonthKey(receipt) : receiptMonthKey(receipt).slice(0, 4);
    grouped.set(key, [...(grouped.get(key) ?? []), receipt]);
  });

  return Array.from(grouped.entries())
    .sort(([a], [b]) => b.localeCompare(a))
    .map(([key, groupReceipts]) => {
      const categories = summarizeExpenseCategories(groupReceipts);
      return {
        key,
        label: mode === "monthly" ? formatMonthLabel(key) : key,
        periodType: mode === "monthly" ? "Month" : "Year",
        total: groupReceipts.reduce((sum, receipt) => sum + Number(receipt.total ?? 0), 0),
        count: groupReceipts.length,
        average: groupReceipts.reduce((sum, receipt) => sum + Number(receipt.total ?? 0), 0) / Math.max(groupReceipts.length, 1),
        latestDate: groupReceipts.map(receiptDateValue).sort((a, b) => b.localeCompare(a))[0],
        topCategory: categories[0]?.name,
        categories
      };
    });
}

export function summarizeExpenseCategories(receipts: Receipt[]) {
  const totals = new Map<string, number>();
  receipts.forEach((receipt) => {
    const category = receipt.merchantCategory ? titleCase(receipt.merchantCategory) : "Uncategorized";
    totals.set(category, (totals.get(category) ?? 0) + Number(receipt.total ?? 0));
  });

  return Array.from(totals.entries())
    .map(([name, amount]) => ({ name, amount }))
    .sort((a, b) => b.amount - a.amount);
}

export function receiptMonthKey(receipt: Receipt) {
  return receiptDateValue(receipt).slice(0, 7);
}

export function receiptDateValue(receipt: Receipt) {
  return receipt.receiptDate ?? receipt.createdAt.slice(0, 10);
}

export function summarizeReceipts(receipts: Receipt[], currency: CurrencyCode): Totals {
  const completed = receipts.filter((receipt) => receipt.status === "COMPLETED");
  return {
    total: completed.reduce((sum, receipt) => sum + Number(receipt.total ?? 0), 0),
    currency,
    count: receipts.length,
    completed: completed.length,
    failed: receipts.filter((receipt) => receipt.status === "FAILED" || receipt.status === "PERMANENTLY_FAILED").length,
    processing: receipts.filter((receipt) => receipt.status === "PROCESSING" || receipt.status === "PENDING").length,
    duplicate: receipts.filter((receipt) => receipt.status === "DUPLICATE").length
  };
}

export function buildAnalytics(receipts: Receipt[], currency: CurrencyCode) {
  const completed = receipts.filter((receipt) => receipt.status === "COMPLETED");
  const monthlyTotals = new Map<string, { total: number; count: number }>();

  completed.forEach((receipt) => {
    const key = receiptMonthKey(receipt);
    const current = monthlyTotals.get(key) ?? { total: 0, count: 0 };
    monthlyTotals.set(key, {
      total: current.total + Number(receipt.total ?? 0),
      count: current.count + 1
    });
  });

  const monthlyTrend = Array.from(monthlyTotals.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .slice(-6)
    .map(([key, value]) => ({
      key,
      label: shortMonthLabel(key),
      total: value.total,
      count: value.count
    }));

  const statusOrder: ReceiptStatus[] = ["COMPLETED", "NEEDS_REVIEW", "PROCESSING", "PENDING", "FAILED", "PERMANENTLY_FAILED", "DUPLICATE"];
  const statusMix = statusOrder
    .map((status) => ({
      status,
      count: receipts.filter((receipt) => receipt.status === status).length
    }))
    .filter((row) => row.count > 0);

  const highValueReceipts = [...completed]
    .sort((a, b) => Number(b.total ?? 0) - Number(a.total ?? 0))
    .slice(0, 5);

  return {
    monthlyTrend,
    statusMix,
    highValueReceipts,
    averageReceipt: completed.reduce((sum, receipt) => sum + Number(receipt.total ?? 0), 0) / Math.max(completed.length, 1),
    needsReview: receipts.filter((receipt) => receipt.status === "NEEDS_REVIEW").length,
    duplicates: receipts.filter((receipt) => receipt.status === "DUPLICATE").length,
    currency
  };
}

export function filterReceipts(receipts: Receipt[], query: string) {
  const term = query.trim().toLowerCase();
  if (!term) return receipts;

  return receipts.filter((receipt) => {
    const haystack = [
      receipt.vendor,
      receipt.originalFilename,
      receipt.merchantCategory,
      receipt.status,
      receipt.receiptDate,
      receipt.createdAt,
      receipt.total,
      receipt.currency,
      receipt.failureReason
    ]
      .filter((value) => value !== null && value !== undefined)
      .join(" ")
      .toLowerCase();

    return haystack.includes(term);
  });
}

export function filterReceiptsByStatus(receipts: Receipt[], status: ReceiptStatusFilter) {
  if (status === "ALL") return receipts;
  if (status === "HAS_FAILURE_REASON") {
    return receipts.filter((receipt) => Boolean(receipt.failureReason));
  }
  return receipts.filter((receipt) => receipt.status === status);
}

export function buildStatusFilterOptions(receipts: Receipt[]) {
  const options: Array<{ status: ReceiptStatusFilter; label: string; count: number }> = [
    { status: "ALL", label: "All", count: receipts.length },
    { status: "HAS_FAILURE_REASON", label: "With reasons", count: receipts.filter((receipt) => Boolean(receipt.failureReason)).length },
    { status: "PROCESSING", label: "Processing", count: 0 },
    { status: "COMPLETED", label: "Completed", count: 0 },
    { status: "NEEDS_REVIEW", label: "Needs review", count: 0 },
    { status: "FAILED", label: "Failed", count: 0 },
    { status: "DUPLICATE", label: "Duplicate", count: 0 },
    { status: "PERMANENTLY_FAILED", label: "Permanent", count: 0 }
  ];
  const byStatus = new Map<ReceiptStatusFilter, number>();
  receipts.forEach((receipt) => {
    byStatus.set(receipt.status, (byStatus.get(receipt.status) ?? 0) + 1);
  });

  return options.map((option) =>
    option.status === "ALL"
      ? option
      : { ...option, count: byStatus.get(option.status) ?? 0 }
  );
}

export function exportReceiptsCsv(receipts: Receipt[]) {
  const headers = ["Date", "Vendor", "Filename", "Category", "Status", "Failure Reason", "Total", "Currency"];
  const rows = receipts.map((receipt) => [
    receiptDateValue(receipt),
    receipt.vendor ?? "",
    receipt.originalFilename,
    receipt.merchantCategory ?? "",
    receipt.status,
    receipt.failureReason ?? "",
    receipt.total ?? "",
    receipt.currency ?? ""
  ]);
  const csv = [headers, ...rows]
    .map((row) => row.map((value) => `"${String(value).replaceAll("\"", "\"\"")}"`).join(","))
    .join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `ledgerlens-receipts-${new Date().toISOString().slice(0, 10)}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function groupReceiptsByDate(receipts: Receipt[]) {
  const groups = new Map<string, Receipt[]>();
  receipts.forEach((receipt) => {
    const key = receiptDateValue(receipt);
    groups.set(key, [...(groups.get(key) ?? []), receipt]);
  });

  return Array.from(groups.entries())
    .sort(([a], [b]) => b.localeCompare(a))
    .map(([key, groupReceipts]) => {
      const completed = groupReceipts.filter((receipt) => receipt.status === "COMPLETED");
      return {
        key,
        receipts: groupReceipts,
        total: completed.reduce((sum, receipt) => sum + Number(receipt.total ?? 0), 0),
        currency: completed[0]?.currency ?? groupReceipts[0]?.currency ?? "INR"
      };
    });
}

export function formatMonthLabel(monthKey: string) {
  const [year, month] = monthKey.split("-").map(Number);
  return new Intl.DateTimeFormat("en-IN", { month: "long", year: "numeric" }).format(new Date(year, month - 1, 1));
}

export function normalizeCategories(
  summary: MonthlySummary | SummaryItem[] | Record<string, number> | null,
  insights: InsightsResponse | null
) {
  if (insights?.byCategory) {
    return Object.entries(insights.byCategory).map(([name, amount]) => ({ name, amount: Number(amount) }));
  }

  if (summary && "byCategory" in summary) {
    return Object.entries(summary.byCategory).map(([name, amount]) => ({ name, amount: Number(amount) }));
  }

  if (Array.isArray(summary)) {
    return summary.map((item) => ({
      name: item.category ?? item.merchantCategory ?? "Other",
      amount: Number(item.total ?? item.amount ?? 0)
    }));
  }

  if (summary && typeof summary === "object") {
    return Object.entries(summary)
      .filter(([, amount]) => typeof amount === "number")
      .map(([name, amount]) => ({ name, amount: Number(amount) }));
  }

  return [];
}

function shortMonthLabel(monthKey: string) {
  const [year, month] = monthKey.split("-").map(Number);
  return new Intl.DateTimeFormat("en-IN", { month: "short", year: "2-digit" }).format(new Date(year, month - 1, 1));
}
