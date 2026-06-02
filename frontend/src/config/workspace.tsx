import {
  CalendarDays,
  Home,
  ReceiptText,
  Settings,
  Sparkles,
  UploadCloud
} from "lucide-react";
import type { AppPage, CurrencyCode, NavItem, WorkspacePreferences } from "../appTypes";

export const currencyOptions: { code: CurrencyCode; label: string }[] = [
  { code: "INR", label: "INR" },
  { code: "USD", label: "USD" },
  { code: "EUR", label: "EUR" },
  { code: "GBP", label: "GBP" },
  { code: "AUD", label: "AUD" },
  { code: "CAD", label: "CAD" },
  { code: "SGD", label: "SGD" },
  { code: "LKR", label: "LKR" }
];

export const defaultPreferences: WorkspacePreferences = {
  openLedgerAfterUpload: true,
  liveStatusUpdates: true,
  defaultPage: "overview",
  monthlyBudget: 0,
  budgetWarningPercent: 80,
  compactMode: false,
  autoRefreshSeconds: 0
};

export const navItems: NavItem[] = [
  { id: "overview", label: "Overview", icon: <Home size={18} /> },
  { id: "upload", label: "Upload", icon: <UploadCloud size={18} /> },
  { id: "expenses", label: "Monthly Expenses", icon: <CalendarDays size={18} /> },
  { id: "receipts", label: "Receipts", icon: <ReceiptText size={18} /> },
  { id: "insights", label: "Insights", icon: <Sparkles size={18} /> },
  { id: "settings", label: "Settings", icon: <Settings size={18} /> }
];

export const pageCopy: Record<AppPage, { title: string; description: string }> = {
  overview: {
    title: "Overview",
    description: "A compact readout of verified spend, receipt flow, and category concentration."
  },
  upload: {
    title: "Upload Center",
    description: "Create receipt records, send files to storage, and queue extraction jobs."
  },
  expenses: {
    title: "Monthly Expenses",
    description: "Browse spending by receipt date using monthly or yearly expense cards."
  },
  receipts: {
    title: "Receipt Ledger",
    description: "Review every receipt as an expense record with status, merchant, category, and running totals."
  },
  insights: {
    title: "Spend Insights",
    description: "Compare category mix, merchant concentration, and AI-generated spending signals."
  },
  settings: {
    title: "Workspace Settings",
    description: "Session controls and runtime connection details for this local LedgerLens workspace."
  }
};
