import type { AppPage, CurrencyCode, WorkspacePreferences } from "../appTypes";
import { currencyOptions, defaultPreferences } from "../config/workspace";

const DISPLAY_CURRENCY_KEY = "ledgerlens.currency";

export function isCurrencyCode(value: unknown): value is CurrencyCode {
  return typeof value === "string" && currencyOptions.some((option) => option.code === value);
}

export function readDisplayCurrency(): CurrencyCode {
  const saved = localStorage.getItem(DISPLAY_CURRENCY_KEY);
  return isCurrencyCode(saved) ? saved : "INR";
}

export function writeDisplayCurrency(currency: CurrencyCode) {
  localStorage.setItem(DISPLAY_CURRENCY_KEY, currency);
}

export function isAppPage(value: unknown): value is AppPage {
  return typeof value === "string" && ["overview", "upload", "expenses", "receipts", "insights", "settings"].includes(value);
}

export function isStartupPage(value: unknown): value is AppPage {
  return isAppPage(value) && value !== "settings";
}

export function readWorkspacePreferences(): WorkspacePreferences {
  const raw = localStorage.getItem("ledgerlens.preferences");
  if (!raw) return defaultPreferences;

  try {
    const parsed = JSON.parse(raw) as Partial<WorkspacePreferences>;
    return {
      openLedgerAfterUpload: typeof parsed.openLedgerAfterUpload === "boolean" ? parsed.openLedgerAfterUpload : defaultPreferences.openLedgerAfterUpload,
      liveStatusUpdates: typeof parsed.liveStatusUpdates === "boolean" ? parsed.liveStatusUpdates : defaultPreferences.liveStatusUpdates,
      defaultPage: isStartupPage(parsed.defaultPage) ? parsed.defaultPage : defaultPreferences.defaultPage,
      monthlyBudget: typeof parsed.monthlyBudget === "number" ? parsed.monthlyBudget : defaultPreferences.monthlyBudget,
      budgetWarningPercent: typeof parsed.budgetWarningPercent === "number" ? parsed.budgetWarningPercent : defaultPreferences.budgetWarningPercent,
      compactMode: typeof parsed.compactMode === "boolean" ? parsed.compactMode : defaultPreferences.compactMode,
      autoRefreshSeconds: typeof parsed.autoRefreshSeconds === "number" ? parsed.autoRefreshSeconds : defaultPreferences.autoRefreshSeconds
    };
  } catch {
    return defaultPreferences;
  }
}
