import type { ReactNode } from "react";
import type { ReceiptStatus } from "./types";

export type AuthMode = "login" | "register";
export type AppPage = "overview" | "upload" | "expenses" | "receipts" | "insights" | "settings";
export type Theme = "light" | "dark";
export type CurrencyCode = "INR" | "USD" | "EUR" | "GBP" | "AUD" | "CAD" | "SGD" | "LKR";
export type ReceiptStatusFilter = "ALL" | "HAS_FAILURE_REASON" | ReceiptStatus;

export type WorkspacePreferences = {
  openLedgerAfterUpload: boolean;
  liveStatusUpdates: boolean;
  defaultPage: AppPage;
  monthlyBudget: number;
  budgetWarningPercent: number;
  compactMode: boolean;
  autoRefreshSeconds: number;
};

export type Totals = {
  total: number;
  currency: CurrencyCode;
  count: number;
  completed: number;
  failed: number;
  processing: number;
  duplicate: number;
};

export type ExpenseCard = {
  key: string;
  label: string;
  periodType: "Month" | "Year";
  total: number;
  count: number;
  average: number;
  latestDate: string;
  topCategory?: string;
  categories: Array<{ name: string; amount: number }>;
};

export type NavItem = {
  id: AppPage;
  label: string;
  icon: ReactNode;
};
