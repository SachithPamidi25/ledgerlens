import {
  AlertTriangle,
  BarChart3,
  Bell,
  CalendarDays,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Clipboard,
  Database,
  Download,
  Edit3,
  FileUp,
  Home,
  Layers3,
  ListChecks,
  Loader2,
  LogOut,
  Moon,
  ReceiptText,
  RefreshCcw,
  RotateCcw,
  Search,
  Settings,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Sun,
  Target,
  TimerReset,
  Trash2,
  UploadCloud,
  WalletCards
} from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  clearTokens,
  correctReceipt,
  deleteLedger,
  deleteReceipt,
  getAccessToken,
  getInsights,
  getMonthlySummary,
  getReceipts,
  login,
  logout,
  register,
  uploadReceipt
} from "./api";
import type { InsightsResponse, MonthlySummary, Page, Receipt, ReceiptCorrectionRequest, ReceiptStatus, SummaryItem } from "./types";

type AuthMode = "login" | "register";
type AppPage = "overview" | "upload" | "expenses" | "receipts" | "insights" | "settings";
type Theme = "light" | "dark";
type CurrencyCode = "INR" | "USD" | "EUR" | "GBP" | "AUD" | "CAD" | "SGD" | "LKR";

type WorkspacePreferences = {
  openLedgerAfterUpload: boolean;
  liveStatusUpdates: boolean;
  defaultPage: AppPage;
  monthlyBudget: number;
  budgetWarningPercent: number;
};

type Totals = {
  total: number;
  currency: CurrencyCode;
  count: number;
  completed: number;
  failed: number;
  processing: number;
  duplicate: number;
};

type ExpenseCard = {
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

const currencyOptions: { code: CurrencyCode; label: string }[] = [
  { code: "INR", label: "INR" },
  { code: "USD", label: "USD" },
  { code: "EUR", label: "EUR" },
  { code: "GBP", label: "GBP" },
  { code: "AUD", label: "AUD" },
  { code: "CAD", label: "CAD" },
  { code: "SGD", label: "SGD" },
  { code: "LKR", label: "LKR" }
];

const defaultPreferences: WorkspacePreferences = {
  openLedgerAfterUpload: true,
  liveStatusUpdates: true,
  defaultPage: "overview",
  monthlyBudget: 0,
  budgetWarningPercent: 80
};

const navItems: Array<{ id: AppPage; label: string; icon: React.ReactNode }> = [
  { id: "overview", label: "Overview", icon: <Home size={18} /> },
  { id: "upload", label: "Upload", icon: <UploadCloud size={18} /> },
  { id: "expenses", label: "Monthly Expenses", icon: <CalendarDays size={18} /> },
  { id: "receipts", label: "Receipts", icon: <ReceiptText size={18} /> },
  { id: "insights", label: "Insights", icon: <Sparkles size={18} /> },
  { id: "settings", label: "Settings", icon: <Settings size={18} /> }
];

const pageCopy: Record<AppPage, { title: string; description: string }> = {
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

function App() {
  const [isAuthenticated, setAuthenticated] = useState(Boolean(getAccessToken()));
  const [theme, setTheme] = useState<Theme>(() => {
    const saved = localStorage.getItem("ledgerlens.theme");
    if (saved === "light" || saved === "dark") return saved;
    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  });

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem("ledgerlens.theme", theme);
  }, [theme]);

  const toggleTheme = useCallback(() => {
    setTheme((current) => (current === "dark" ? "light" : "dark"));
  }, []);

  return isAuthenticated ? (
    <Dashboard theme={theme} onToggleTheme={toggleTheme} onSignedOut={() => setAuthenticated(false)} />
  ) : (
    <AuthScreen theme={theme} onToggleTheme={toggleTheme} onSignedIn={() => setAuthenticated(true)} />
  );
}

function ThemeToggle({ theme, onToggle }: { theme: Theme; onToggle: () => void }) {
  const isDark = theme === "dark";

  return (
    <button className="theme-toggle" type="button" onClick={onToggle} aria-label={`Switch to ${isDark ? "light" : "dark"} theme`} title={`${isDark ? "Light" : "Dark"} theme`}>
      {isDark ? <Sun size={18} /> : <Moon size={18} />}
      <span>{isDark ? "Light" : "Dark"}</span>
    </button>
  );
}

function CurrencySelect({ value, onChange }: { value: CurrencyCode; onChange: (currency: CurrencyCode) => void }) {
  const [open, setOpen] = useState(false);
  const selected = currencyOptions.find((currency) => currency.code === value) ?? currencyOptions[0];

  return (
    <div className="currency-select">
      <span>Currency</span>
      <div className="currency-dropdown">
        <button
          className="currency-trigger"
          type="button"
          onClick={() => setOpen((current) => !current)}
          aria-expanded={open}
          aria-haspopup="listbox"
        >
          {selected.label}
          <ChevronDown size={15} />
        </button>
        {open && (
          <div className="currency-menu" role="listbox" aria-label="Display currency">
            {currencyOptions.map((currency) => (
              <button
                key={currency.code}
                className={value === currency.code ? "active" : ""}
                type="button"
                onClick={() => {
                  onChange(currency.code);
                  setOpen(false);
                }}
                role="option"
                aria-selected={value === currency.code}
              >
                {currency.label}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function isCurrencyCode(value: unknown): value is CurrencyCode {
  return typeof value === "string" && currencyOptions.some((currency) => currency.code === value);
}

const DISPLAY_CURRENCY_KEY = "ledgerlens.currency";

function readDisplayCurrency(): CurrencyCode {
  const saved = localStorage.getItem(DISPLAY_CURRENCY_KEY);
  return isCurrencyCode(saved) ? saved : "INR";
}

function writeDisplayCurrency(currency: CurrencyCode) {
  localStorage.setItem(DISPLAY_CURRENCY_KEY, currency);
}

function isAppPage(value: unknown): value is AppPage {
  return typeof value === "string" && navItems.some((item) => item.id === value);
}

function isStartupPage(value: unknown): value is AppPage {
  return isAppPage(value) && value !== "settings";
}

function readWorkspacePreferences(): WorkspacePreferences {
  const saved = localStorage.getItem("ledgerlens.preferences");
  if (!saved) return defaultPreferences;

  try {
    const parsed = JSON.parse(saved) as Partial<WorkspacePreferences>;
    return {
      openLedgerAfterUpload: parsed.openLedgerAfterUpload ?? defaultPreferences.openLedgerAfterUpload,
      liveStatusUpdates: parsed.liveStatusUpdates ?? defaultPreferences.liveStatusUpdates,
      defaultPage: isStartupPage(parsed.defaultPage) ? parsed.defaultPage : defaultPreferences.defaultPage,
      monthlyBudget: typeof parsed.monthlyBudget === "number" ? parsed.monthlyBudget : defaultPreferences.monthlyBudget,
      budgetWarningPercent:
        typeof parsed.budgetWarningPercent === "number"
          ? parsed.budgetWarningPercent
          : defaultPreferences.budgetWarningPercent
    };
  } catch {
    return defaultPreferences;
  }
}

function AuthScreen({ theme, onToggleTheme, onSignedIn }: { theme: Theme; onToggleTheme: () => void; onSignedIn: () => void }) {
  const [mode, setMode] = useState<AuthMode>("login");
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError("");

    try {
      if (mode === "login") {
        await login(email, password);
      } else {
        await register(fullName, email, password);
      }
      onSignedIn();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-shell">
      <section className="brand-panel">
        <div className="auth-theme-action">
          <ThemeToggle theme={theme} onToggle={onToggleTheme} />
        </div>
        <div className="auth-brand-lockup">
          <div className="logo-mark">
            <ReceiptText size={28} />
          </div>
          <div>
            <strong>LedgerLens</strong>
            <span>Receipt intelligence workspace</span>
          </div>
        </div>
        <h1>Turn receipts into clean expense records.</h1>
        <p>Upload, classify, reconcile, and review spending from a workspace built around the receipt lifecycle.</p>
        <div className="receipt-preview" aria-hidden="true">
          <div className="preview-top">
            <span>Receipt extraction</span>
            <strong>Ready</strong>
          </div>
          <div className="preview-bars">
            <span />
            <span />
            <span />
            <span />
          </div>
          <div className="preview-total">
            <span>Merchant, date, tax, total</span>
            <span>AI PARSED</span>
          </div>
        </div>
      </section>

      <section className="auth-panel">
        <div className="auth-card">
          <div className="auth-card-heading">
            <h2>{mode === "login" ? "Sign in" : "Create account"}</h2>
            <p>{mode === "login" ? "Continue to your LedgerLens workspace." : "Create a local account for this workspace."}</p>
          </div>
          <div className="segmented" role="tablist" aria-label="Authentication mode">
            <button className={mode === "login" ? "active" : ""} onClick={() => setMode("login")} type="button">
              Sign in
            </button>
            <button className={mode === "register" ? "active" : ""} onClick={() => setMode("register")} type="button">
              Create account
            </button>
          </div>

          <form onSubmit={submit} className="auth-form">
            {mode === "register" && (
              <label>
                Full name
                <input value={fullName} onChange={(event) => setFullName(event.target.value)} required />
              </label>
            )}
            <label>
              Email
              <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
            </label>
            <label>
              Password
              <input
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                minLength={8}
                required
              />
            </label>
            {error && <p className="form-error">{error}</p>}
            <button className="primary-action" type="submit" disabled={loading}>
              {loading ? "Working..." : mode === "login" ? "Sign in" : "Create account"}
            </button>
          </form>
        </div>
      </section>
    </main>
  );
}

function Dashboard({
  theme,
  onToggleTheme,
  onSignedOut
}: {
  theme: Theme;
  onToggleTheme: () => void;
  onSignedOut: () => void;
}) {
  const [preferences, setPreferences] = useState<WorkspacePreferences>(() => readWorkspacePreferences());
  const [page, setPage] = useState<AppPage>(() => preferences.defaultPage);
  const [receipts, setReceipts] = useState<Page<Receipt> | null>(null);
  const [summary, setSummary] = useState<MonthlySummary | null>(null);
  const [insights, setInsights] = useState<InsightsResponse | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<{ current: number; total: number } | null>(null);
  const [activeReceiptIds, setActiveReceiptIds] = useState<string[]>([]);
  const [deletingReceiptId, setDeletingReceiptId] = useState<string | null>(null);
  const [editingReceipt, setEditingReceipt] = useState<Receipt | null>(null);
  const [correctingReceiptId, setCorrectingReceiptId] = useState<string | null>(null);
  const [deletingLedger, setDeletingLedger] = useState(false);
  const [displayCurrency, setDisplayCurrency] = useState<CurrencyCode>(() => readDisplayCurrency());

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    const results = await Promise.allSettled([getReceipts(), getMonthlySummary(), getInsights(3)]);
    const messages: string[] = [];

    results.forEach((result, index) => {
      if (result.status === "fulfilled") {
        if (index === 0) setReceipts(result.value as Page<Receipt>);
        if (index === 1) setSummary(result.value as MonthlySummary);
        if (index === 2) setInsights(result.value as InsightsResponse);
        return;
      }

      const message = result.reason instanceof Error ? result.reason.message : "Request failed";
      if (message.toLowerCase().includes("unauthorized")) {
        clearTokens();
        onSignedOut();
      }
      messages.push(message);
    });

    setError(messages.length ? Array.from(new Set(messages)).join(". ") : "");
    setLoading(false);
  }, [onSignedOut]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    writeDisplayCurrency(displayCurrency);
  }, [displayCurrency]);

  useEffect(() => {
    localStorage.setItem("ledgerlens.preferences", JSON.stringify(preferences));
  }, [preferences]);

  useEffect(() => {
    if (!preferences.liveStatusUpdates || !activeReceiptIds.length) return;

    const token = getAccessToken();
    if (!token) return;

    const sources = activeReceiptIds.map((id) => {
      const source = new EventSource(`/api/receipts/${id}/status-stream?access_token=${encodeURIComponent(token)}`);
      source.addEventListener("status", (event) => {
        loadData();
        const status = parseStatusEvent(event);
        if (status && isTerminalReceiptStatus(status)) {
          setActiveReceiptIds((current) => current.filter((receiptId) => receiptId !== id));
          source.close();
        }
      });
      source.onerror = () => {
        source.close();
      };
      return source;
    });

    return () => sources.forEach((source) => source.close());
  }, [activeReceiptIds, loadData, preferences.liveStatusUpdates]);

  useEffect(() => {
    if (!activeReceiptIds.length) return;

    const intervalId = window.setInterval(() => {
      loadData();
    }, 4000);

    return () => window.clearInterval(intervalId);
  }, [activeReceiptIds.length, loadData]);

  useEffect(() => {
    if (!activeReceiptIds.length || !receipts) return;

    const statusById = new Map(receipts.content.map((receipt) => [receipt.id, receipt.status]));
    setActiveReceiptIds((current) =>
      current.filter((id) => {
        const status = statusById.get(id);
        return status ? !isTerminalReceiptStatus(status) : true;
      })
    );
  }, [activeReceiptIds.length, receipts]);

  const receiptList = receipts?.content ?? [];
  const totals = useMemo<Totals>(() => {
    const completedReceipts = receiptList.filter((receipt) => receipt.status === "COMPLETED");
    const completedTotal = receiptList.reduce((sum, receipt) => sum + Number(receipt.total ?? 0), 0);
    const failed = receiptList.filter((receipt) => receipt.status === "FAILED" || receipt.status === "PERMANENTLY_FAILED").length;
    const processing = receiptList.filter((receipt) => receipt.status === "PROCESSING" || receipt.status === "PENDING").length;
    const duplicate = receiptList.filter((receipt) => receipt.status === "DUPLICATE").length;

    return {
      total: Number(summary?.totalSpent ?? insights?.totalSpent ?? completedTotal),
      currency: displayCurrency,
      count: receipts?.totalElements ?? 0,
      completed: completedReceipts.length,
      failed,
      processing,
      duplicate
    };
  }, [displayCurrency, insights, receiptList, receipts?.totalElements, summary]);

  async function handleFiles(files?: FileList | File[]) {
    const selected = Array.from(files ?? []);
    if (!selected.length) return;

    setUploading(true);
    setUploadProgress({ current: 0, total: selected.length });
    setError("");
    const failures: string[] = [];
    try {
      for (const [index, file] of selected.entries()) {
        setUploadProgress({ current: index + 1, total: selected.length });
        try {
          const receiptId = await uploadReceipt(file, loadData);
          setActiveReceiptIds((current) => Array.from(new Set([...current, receiptId])));
        } catch (err) {
          const message = err instanceof Error ? err.message : "Upload failed";
          failures.push(`${file.name}: ${message}`);
        }
      }
      await loadData();
      if (preferences.openLedgerAfterUpload) {
        setPage("receipts");
      }
      setError(failures.length ? failures.join(". ") : "");
    } finally {
      setUploading(false);
      setUploadProgress(null);
    }
  }

  async function handleLogout() {
    await logout();
    onSignedOut();
  }

  async function handleDeleteReceipt(receipt: Receipt) {
    const label = receipt.vendor || receipt.originalFilename;
    if (!window.confirm(`Delete "${label}" from your ledger? This also removes the stored receipt file.`)) return;

    setDeletingReceiptId(receipt.id);
    setError("");
    try {
      await deleteReceipt(receipt.id);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Delete failed");
    } finally {
      setDeletingReceiptId(null);
    }
  }

  async function handleCorrectReceipt(receiptId: string, correction: ReceiptCorrectionRequest) {
    setCorrectingReceiptId(receiptId);
    setError("");
    try {
      await correctReceipt(receiptId, correction);
      setEditingReceipt(null);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Correction failed");
    } finally {
      setCorrectingReceiptId(null);
    }
  }

  async function handleDeleteLedger() {
    const confirmation = window.prompt(
      "This will permanently delete every receipt in this ledger and remove stored receipt files. Type DELETE LEDGER to continue."
    );
    if (confirmation !== "DELETE LEDGER") return;

    setDeletingLedger(true);
    setError("");
    try {
      await deleteLedger();
      await loadData();
      setPage("receipts");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Ledger delete failed");
    } finally {
      setDeletingLedger(false);
    }
  }

  function updatePreference<K extends keyof WorkspacePreferences>(key: K, value: WorkspacePreferences[K]) {
    setPreferences((current) => ({ ...current, [key]: value }));
  }

  function handleDisplayCurrencyChange(currency: CurrencyCode) {
    writeDisplayCurrency(currency);
    setDisplayCurrency(currency);
  }

  function resetBrowserSettings() {
    writeDisplayCurrency("INR");
    setDisplayCurrency("INR");
    setPreferences(defaultPreferences);
    setPage(defaultPreferences.defaultPage);
  }

  return (
    <main className="workspace-shell">
      <aside className="sidebar">
        <div className="brand-lockup">
          <div className="logo-mark small">
            <ReceiptText size={22} />
          </div>
          <div>
            <strong>LedgerLens</strong>
            <span>Receipt ops</span>
          </div>
        </div>

        <nav className="sidebar-nav" aria-label="Workspace pages">
          {navItems.map((item) => (
            <button
              className={page === item.id ? "active" : ""}
              key={item.id}
              onClick={() => setPage(item.id)}
              type="button"
            >
              {item.icon}
              <span>{item.label}</span>
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="run-state">
            <span className="pulse-dot" />
            API connected
          </div>
          <button className="secondary-action" type="button" onClick={handleLogout}>
            <LogOut size={17} />
            Sign out
          </button>
        </div>
      </aside>

      <section className="workspace-main">
        <header className="topbar">
          <div className="page-title compact">
            <div>
              <h1>{pageCopy[page].title}</h1>
              <p>{pageCopy[page].description}</p>
            </div>
          </div>
          <div className="topbar-actions">
            <button className="icon-button" type="button" onClick={loadData} aria-label="Refresh workspace" title="Refresh">
              <RefreshCcw size={18} />
            </button>
          </div>
        </header>

        {error && (
          <div className="notice">
            <AlertTriangle size={18} />
            <span>{error}</span>
          </div>
        )}

        {page === "overview" && (
          <OverviewPage
            receipts={receiptList}
            summary={summary}
            insights={insights}
            totals={totals}
            loading={loading}
            onOpenPage={setPage}
          />
        )}
        {page === "upload" && (
          <UploadPage
            uploading={uploading}
            uploadProgress={uploadProgress}
            receipts={receiptList}
            onFiles={handleFiles}
          />
        )}
        {page === "expenses" && (
          <ExpensesPage
            receipts={receiptList}
            currency={totals.currency}
            loading={loading}
            onOpenReceipts={() => setPage("receipts")}
          />
        )}
        {page === "receipts" && (
          <ReceiptsPage
            receiptPage={receipts}
            receipts={receiptList}
            loading={loading}
            totals={totals}
            deletingReceiptId={deletingReceiptId}
            editingReceipt={editingReceipt}
            correctingReceiptId={correctingReceiptId}
            onEditReceipt={setEditingReceipt}
            onCancelEdit={() => setEditingReceipt(null)}
            onCorrectReceipt={handleCorrectReceipt}
            onDeleteReceipt={handleDeleteReceipt}
          />
        )}
        {page === "insights" && (
          <InsightsPage receipts={receiptList} summary={summary} insights={insights} totals={totals} loading={loading} />
        )}
        {page === "settings" && (
          <SettingsPage
            theme={theme}
            totals={totals}
            displayCurrency={displayCurrency}
            preferences={preferences}
            deletingLedger={deletingLedger}
            onToggleTheme={onToggleTheme}
            onCurrencyChange={handleDisplayCurrencyChange}
            onPreferenceChange={updatePreference}
            onResetBrowserSettings={resetBrowserSettings}
            onRefresh={loadData}
            onLogout={handleLogout}
            onDeleteLedger={handleDeleteLedger}
          />
        )}
      </section>
    </main>
  );
}

function OverviewPage({
  receipts,
  summary,
  insights,
  totals,
  loading,
  onOpenPage
}: {
  receipts: Receipt[];
  summary: MonthlySummary | null;
  insights: InsightsResponse | null;
  totals: Totals;
  loading: boolean;
  onOpenPage: (page: AppPage) => void;
}) {
  return (
    <div className="page-stack">
      <section className="metric-grid">
        <Metric icon={<WalletCards size={20} />} label="Verified spend" value={money(totals.total, totals.currency)} detail={`Displayed in ${totals.currency}`} />
        <Metric icon={<ReceiptText size={20} />} label="Receipts tracked" value={String(totals.count)} detail={`${totals.completed} completed`} />
        <Metric icon={<BarChart3 size={20} />} label="Needs attention" value={String(totals.failed)} detail={`${totals.processing} still processing`} />
      </section>

      <section className="overview-grid">
        <div className="panel span-2">
          <div className="panel-heading">
            <div>
              <h2>Recent Receipt Flow</h2>
              <p>{loading ? "Loading..." : `${receipts.length} latest records from the ledger`}</p>
            </div>
            <button className="text-action" type="button" onClick={() => onOpenPage("receipts")}>
              Open ledger
              <ChevronRight size={16} />
            </button>
          </div>
          <ReceiptTimeline receipts={receipts.slice(0, 6)} loading={loading} />
        </div>

        <div className="panel">
          <div className="panel-heading">
            <div>
              <h2>Category Mix</h2>
              <p>{summary?.period ?? insights?.period ?? "Completed receipts"}</p>
            </div>
          </div>
          <CategoryList summary={summary} insights={insights} currency={totals.currency} />
        </div>

        <div className="panel">
          <div className="panel-heading">
            <div>
              <h2>Merchant Concentration</h2>
              <p>Top spend destinations</p>
            </div>
          </div>
          <MerchantList summary={summary} currency={totals.currency} />
        </div>

        <div className="panel span-2">
          <div className="panel-heading">
            <div>
              <h2>AI Signals</h2>
              <p>{insights?.period ?? "Last 3 months"}</p>
            </div>
            <button className="text-action" type="button" onClick={() => onOpenPage("insights")}>
              View insights
              <ChevronRight size={16} />
            </button>
          </div>
          <InsightList insights={insights} loading={loading} limit={3} />
        </div>
      </section>
    </div>
  );
}

function UploadPage({
  uploading,
  uploadProgress,
  receipts,
  onFiles
}: {
  uploading: boolean;
  uploadProgress: { current: number; total: number } | null;
  receipts: Receipt[];
  onFiles: (files?: FileList | File[]) => void;
}) {
  const progressLabel = uploadProgress
    ? `Uploading ${uploadProgress.current} of ${uploadProgress.total}`
    : "Drop in receipt images.";

  return (
    <section className="upload-layout">
      <div className="panel upload-station">
        <div className="panel-heading">
          <div>
            <h2>New Receipts</h2>
            <p>{uploading ? progressLabel : "Upload one receipt or a small batch."}</p>
          </div>
          <FileUp size={22} />
        </div>
        <label className={`dropzone large ${uploading ? "busy" : ""}`}>
          <input
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            multiple
            disabled={uploading}
            onChange={(event) => onFiles(event.target.files ?? undefined)}
          />
          {uploading ? <Loader2 size={30} /> : <UploadCloud size={34} />}
          <span>{uploading ? progressLabel : "Choose receipt files"}</span>
          <small>PNG, JPG, WEBP, or GIF</small>
        </label>
        <div className="pipeline">
          <PipelineStep label="Store" active={uploading} />
          <PipelineStep label="Extract" active={uploading} />
          <PipelineStep label="Review" active={uploading} />
        </div>
      </div>

      <div className="panel">
        <div className="panel-heading">
          <div>
            <h2>Processing Queue</h2>
            <p>Newest receipt jobs</p>
          </div>
        </div>
        <ReceiptTimeline receipts={receipts.slice(0, 8)} loading={false} />
      </div>
    </section>
  );
}

function ExpensesPage({
  receipts,
  currency,
  loading,
  onOpenReceipts
}: {
  receipts: Receipt[];
  currency: CurrencyCode;
  loading: boolean;
  onOpenReceipts: () => void;
}) {
  const [mode, setMode] = useState<"monthly" | "yearly">("monthly");
  const cards = useMemo(() => buildExpenseCards(receipts, mode, currency), [currency, mode, receipts]);
  const topCard = useMemo(() => [...cards].sort((a, b) => b.total - a.total)[0], [cards]);
  const totalSpend = cards.reduce((sum, card) => sum + card.total, 0);

  return (
    <section className="expenses-page">
      <div className="expenses-hero">
        <div>
          <span>Expense calendar</span>
          <h2>{mode === "monthly" ? "Monthly receipt spending" : "Yearly receipt spending"}</h2>
          <p>
            {loading
              ? "Loading expense periods..."
              : `${cards.length} ${mode === "monthly" ? "months" : "years"} with completed receipt spend`}
          </p>
        </div>
        <div className="expenses-mode-control" role="group" aria-label="Expense period">
          <button className={mode === "monthly" ? "active" : ""} type="button" onClick={() => setMode("monthly")}>
            Monthly
          </button>
          <button className={mode === "yearly" ? "active" : ""} type="button" onClick={() => setMode("yearly")}>
            Yearly
          </button>
        </div>
      </div>

      <section className="expense-summary-grid">
        <MiniStat label="Total shown" value={money(totalSpend, currency)} />
        <MiniStat label="Periods" value={String(cards.length)} />
        <MiniStat label="Top period" value={topCard ? topCard.label : "-"} />
        <MiniStat label="Top spend" value={topCard ? money(topCard.total, currency) : money(0, currency)} />
      </section>

      <section className="expense-card-grid">
        {cards.map((card) => (
          <ExpensePeriodCard key={card.key} card={card} currency={currency} onOpenReceipts={onOpenReceipts} />
        ))}
      </section>

      {!loading && !cards.length && (
        <div className="panel">
          <p className="empty-state">No completed expenses yet. Upload receipts and completed totals will appear here.</p>
        </div>
      )}
    </section>
  );
}

function ExpensePeriodCard({
  card,
  currency,
  onOpenReceipts
}: {
  card: ExpenseCard;
  currency: CurrencyCode;
  onOpenReceipts: () => void;
}) {
  const topCategory = card.topCategory ?? "Uncategorized";

  return (
    <article className="expense-period-card">
      <div className="expense-card-top">
        <div>
          <span>{card.periodType}</span>
          <strong>{card.label}</strong>
        </div>
        <CalendarDays size={22} />
      </div>
      <div className="expense-card-total">{money(card.total, currency)}</div>
      <div className="expense-card-meta">
        <span>{card.count} receipts</span>
        <span>{topCategory}</span>
      </div>
      <div className="expense-card-kpis">
        <div>
          <span>Average receipt</span>
          <strong>{money(card.average, currency)}</strong>
        </div>
        <div>
          <span>Latest receipt</span>
          <strong>{formatDate(card.latestDate)}</strong>
        </div>
      </div>
      <div className="expense-card-bars">
        {card.categories.slice(0, 3).map((category) => (
          <div key={category.name}>
            <span>{category.name}</span>
            <strong>{money(category.amount, currency)}</strong>
            <div className="bar-track">
              <span style={{ width: `${Math.max((category.amount / Math.max(card.total, 1)) * 100, 6)}%` }} />
            </div>
          </div>
        ))}
      </div>
      <button className="text-action" type="button" onClick={onOpenReceipts}>
        Open receipts
        <ChevronRight size={16} />
      </button>
    </article>
  );
}

function ReceiptsPage({
  receiptPage,
  receipts,
  loading,
  totals,
  deletingReceiptId,
  editingReceipt,
  correctingReceiptId,
  onEditReceipt,
  onCancelEdit,
  onCorrectReceipt,
  onDeleteReceipt
}: {
  receiptPage: Page<Receipt> | null;
  receipts: Receipt[];
  loading: boolean;
  totals: Totals;
  deletingReceiptId: string | null;
  editingReceipt: Receipt | null;
  correctingReceiptId: string | null;
  onEditReceipt: (receipt: Receipt) => void;
  onCancelEdit: () => void;
  onCorrectReceipt: (receiptId: string, correction: ReceiptCorrectionRequest) => void;
  onDeleteReceipt: (receipt: Receipt) => void;
}) {
  const monthOptions = useMemo(() => buildReceiptMonthOptions(receipts), [receipts]);
  const [selectedMonth, setSelectedMonth] = useState("all");
  const [query, setQuery] = useState("");
  const loadedCount = receipts.length;
  const totalCount = receiptPage?.totalElements ?? totals.count;
  const totalPages = receiptPage?.totalPages ?? 0;
  const pageNumber = (receiptPage?.number ?? 0) + 1;
  const hiddenCount = Math.max(totalCount - loadedCount, 0);
  const monthlyReceipts = useMemo(
    () => selectedMonth === "all" ? receipts : receipts.filter((receipt) => receiptMonthKey(receipt) === selectedMonth),
    [receipts, selectedMonth]
  );
  const visibleReceipts = useMemo(() => filterReceipts(monthlyReceipts, query), [monthlyReceipts, query]);
  const selectedMonthLabel = selectedMonth === "all" ? "All months" : formatMonthLabel(selectedMonth);
  const monthlyStats = useMemo(() => summarizeReceipts(visibleReceipts, totals.currency), [visibleReceipts, totals.currency]);

  useEffect(() => {
    if (selectedMonth !== "all" && !monthOptions.some((option) => option.key === selectedMonth)) {
      setSelectedMonth("all");
    }
  }, [monthOptions, selectedMonth]);

  return (
    <div className="page-stack">
      <section className="month-panel">
        <div className="month-panel-copy">
          <CalendarDays size={22} />
          <div>
            <h2>{selectedMonthLabel}</h2>
            <p>{visibleReceipts.length} receipts organized by receipt date</p>
          </div>
        </div>
        <div className="ledger-controls">
          <label className="ledger-search">
            <Search size={17} />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search vendor, category, status..."
            />
            {query && (
              <button type="button" onClick={() => setQuery("")} aria-label="Clear receipt search">
                Clear
              </button>
            )}
          </label>
          <label className="month-select">
            Month
            <select value={selectedMonth} onChange={(event) => setSelectedMonth(event.target.value)}>
              <option value="all">All months</option>
              {monthOptions.map((option) => (
                <option key={option.key} value={option.key}>
                  {option.label} ({option.count})
                </option>
              ))}
            </select>
          </label>
          <button className="secondary-action compact-control" type="button" onClick={() => exportReceiptsCsv(visibleReceipts)} disabled={!visibleReceipts.length}>
            <Download size={16} />
            Export CSV
          </button>
        </div>
      </section>

      <section className="status-grid">
        <MiniStat label="Month spend" value={money(monthlyStats.total, monthlyStats.currency)} />
        <MiniStat label="Receipts" value={String(monthlyStats.count)} />
        <MiniStat label="Completed" value={String(monthlyStats.completed)} />
        <MiniStat label="Failed" value={String(monthlyStats.failed)} tone="danger" />
      </section>

      {editingReceipt && (
        <CorrectionPanel
          receipt={editingReceipt}
          saving={correctingReceiptId === editingReceipt.id}
          onCancel={onCancelEdit}
          onSubmit={onCorrectReceipt}
        />
      )}

      <section className="panel ledger-panel">
        <div className="panel-heading ledger-panel-heading">
          <div>
            <h2>Ledger Entries</h2>
            <p>
              {loading
                ? "Loading receipts..."
                : hiddenCount
                  ? `Showing ${loadedCount} of ${totalCount} receipts`
                  : `${visibleReceipts.length} receipts in ${selectedMonthLabel.toLowerCase()}`}
            </p>
          </div>
          <div className="ledger-summary" aria-label="Receipt list summary">
          <span>{loading ? "Syncing" : `${loadedCount} loaded`}</span>
            {query && <span>{visibleReceipts.length} search matches</span>}
            <span>Grouped by date</span>
            {totalPages > 1 && <span>Page {pageNumber} of {totalPages}</span>}
          </div>
        </div>
        <MonthlyLedger
          receipts={visibleReceipts}
          loading={loading}
          deletingReceiptId={deletingReceiptId}
          onEditReceipt={onEditReceipt}
          onDeleteReceipt={onDeleteReceipt}
        />
      </section>
    </div>
  );
}

function InsightsPage({
  receipts,
  summary,
  insights,
  totals,
  loading
}: {
  receipts: Receipt[];
  summary: MonthlySummary | null;
  insights: InsightsResponse | null;
  totals: Totals;
  loading: boolean;
}) {
  const analytics = useMemo(() => buildAnalytics(receipts, totals.currency), [receipts, totals.currency]);

  return (
    <section className="insights-layout">
      <div className="analytics-kpi-strip span-2">
        <MiniStat label="Average receipt" value={money(analytics.averageReceipt, totals.currency)} />
        <MiniStat label="Highest receipt" value={analytics.highValueReceipts[0] ? money(Number(analytics.highValueReceipts[0].total ?? 0), totals.currency) : "-"} />
        <MiniStat label="Review queue" value={String(analytics.needsReview)} tone={analytics.needsReview > 0 ? "danger" : undefined} />
        <MiniStat label="Duplicates" value={String(analytics.duplicates)} />
      </div>

      <div className="panel span-2 analytics-trend-panel">
        <div className="panel-heading">
          <div>
            <h2>Spend Trend</h2>
            <p>{analytics.monthlyTrend.length ? `${analytics.monthlyTrend.length} active months from completed receipts` : "Waiting for completed receipts"}</p>
          </div>
          <BarChart3 size={22} />
        </div>
        <MonthlySpendChart rows={analytics.monthlyTrend} currency={totals.currency} loading={loading} />
      </div>

      <div className="panel">
        <div className="panel-heading">
          <div>
            <h2>Category Breakdown</h2>
            <p>{summary?.period ?? insights?.period ?? "Completed receipts"}</p>
          </div>
        </div>
        <CategoryList summary={summary} insights={insights} currency={totals.currency} />
      </div>

      <div className="panel">
        <div className="panel-heading">
          <div>
            <h2>Merchant Spend</h2>
            <p>Grouped by normalized vendor</p>
          </div>
        </div>
        <MerchantList summary={summary} currency={totals.currency} />
      </div>

      <div className="panel">
        <div className="panel-heading">
          <div>
            <h2>Receipt Operations</h2>
            <p>Status mix across the loaded ledger</p>
          </div>
          <ListChecks size={22} />
        </div>
        <StatusMix rows={analytics.statusMix} total={receipts.length} />
      </div>

      <div className="panel">
        <div className="panel-heading">
          <div>
            <h2>High-Value Receipts</h2>
            <p>Largest completed transactions</p>
          </div>
          <Target size={22} />
        </div>
        <HighValueList receipts={analytics.highValueReceipts} currency={totals.currency} />
      </div>

      <div className="panel span-2">
        <div className="panel-heading">
          <div>
            <h2>AI Insight Feed</h2>
            <p>{insights?.generatedAt ? `Generated ${formatDateTime(insights.generatedAt)}` : "Waiting for enough receipt data"}</p>
          </div>
          <Sparkles size={22} />
        </div>
        <InsightList insights={insights} loading={loading} />
      </div>
    </section>
  );
}

function SettingsPage({
  theme,
  totals,
  displayCurrency,
  preferences,
  deletingLedger,
  onToggleTheme,
  onCurrencyChange,
  onPreferenceChange,
  onResetBrowserSettings,
  onRefresh,
  onLogout,
  onDeleteLedger
}: {
  theme: Theme;
  totals: Totals;
  displayCurrency: CurrencyCode;
  preferences: WorkspacePreferences;
  deletingLedger: boolean;
  onToggleTheme: () => void;
  onCurrencyChange: (currency: CurrencyCode) => void;
  onPreferenceChange: <K extends keyof WorkspacePreferences>(key: K, value: WorkspacePreferences[K]) => void;
  onResetBrowserSettings: () => void;
  onRefresh: () => void;
  onLogout: () => void;
  onDeleteLedger: () => void;
}) {
  const [copied, setCopied] = useState(false);
  const tokenPresent = Boolean(getAccessToken());

  async function copyDiagnostics() {
    const diagnostics = {
      apiBase: "/api",
      theme,
      displayCurrency,
      liveStatusUpdates: preferences.liveStatusUpdates,
      openLedgerAfterUpload: preferences.openLedgerAfterUpload,
      defaultPage: preferences.defaultPage,
      monthlyBudget: preferences.monthlyBudget,
      budgetWarningPercent: preferences.budgetWarningPercent,
      session: tokenPresent ? "active" : "missing"
    };

    try {
      await navigator.clipboard.writeText(JSON.stringify(diagnostics, null, 2));
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  }

  return (
    <section className="settings-layout">
      <div className="panel">
        <div className="panel-heading">
          <div>
            <h2>Runtime</h2>
            <p>Local development endpoints used by the current app.</p>
          </div>
          <Layers3 size={22} />
        </div>
        <div className="settings-list">
          <SettingRow label="API base" value="/api" />
          <SettingRow label="Storage flow" value="MinIO presigned upload" />
          <SettingRow label="Processing" value="RabbitMQ outbox queue" />
          <SettingRow label="Insights" value="AI-backed summaries" />
          <SettingRow label="Session token" value={tokenPresent ? "Present in browser storage" : "Missing"} />
        </div>
      </div>

      <div className="panel">
        <div className="panel-heading">
          <div>
            <h2>Preferences</h2>
            <p>Choose how ledger values are displayed in this browser.</p>
          </div>
          <SlidersHorizontal size={22} />
        </div>
        <div className="preference-controls">
          <ThemePreference theme={theme} onToggle={onToggleTheme} />
          <CurrencySelect value={displayCurrency} onChange={onCurrencyChange} />
        </div>
      </div>

      <div className="panel">
        <div className="panel-heading">
          <div>
            <h2>Startup</h2>
            <p>Pick the first workspace view for this browser.</p>
          </div>
          <Home size={22} />
        </div>
        <div className="startup-grid">
          {navItems
            .filter((item) => item.id !== "settings")
            .map((item) => (
              <button
                className={preferences.defaultPage === item.id ? "startup-option active" : "startup-option"}
                key={item.id}
                type="button"
                onClick={() => onPreferenceChange("defaultPage", item.id)}
              >
                {item.icon}
                <span>{item.label}</span>
              </button>
            ))}
        </div>
      </div>

      <div className="panel">
        <div className="panel-heading">
          <div>
            <h2>Upload Behavior</h2>
            <p>Control what happens after receipt files are queued.</p>
          </div>
          <Bell size={22} />
        </div>
        <div className="toggle-list">
          <ToggleRow
            icon={<ListChecks size={18} />}
            label="Open ledger after upload"
            detail="Jump to Receipts when a batch finishes"
            checked={preferences.openLedgerAfterUpload}
            onChange={(checked) => onPreferenceChange("openLedgerAfterUpload", checked)}
          />
          <ToggleRow
            icon={<ShieldCheck size={18} />}
            label="Live processing updates"
            detail="Listen for status changes while receipts process"
            checked={preferences.liveStatusUpdates}
            onChange={(checked) => onPreferenceChange("liveStatusUpdates", checked)}
          />
        </div>
      </div>

      <BudgetSettings
        budget={preferences.monthlyBudget}
        warningPercent={preferences.budgetWarningPercent}
        totals={totals}
        onBudgetChange={(value) => onPreferenceChange("monthlyBudget", value)}
        onWarningPercentChange={(value) => onPreferenceChange("budgetWarningPercent", value)}
      />

      <div className="panel">
        <div className="panel-heading">
          <div>
            <h2>Session</h2>
            <p>Refresh data or end the current login session.</p>
          </div>
        </div>
        <div className="settings-actions">
          <button className="secondary-action" type="button" onClick={onRefresh}>
            <RefreshCcw size={17} />
            Refresh workspace
          </button>
          <button className="danger-action" type="button" onClick={onLogout}>
            <LogOut size={17} />
            Sign out
          </button>
        </div>
      </div>

      <div className="panel">
        <div className="panel-heading">
          <div>
            <h2>Browser Tools</h2>
            <p>Utilities for local troubleshooting and preferences.</p>
          </div>
          <Database size={22} />
        </div>
        <div className="settings-actions">
          <button className="secondary-action" type="button" onClick={copyDiagnostics}>
            <Clipboard size={17} />
            {copied ? "Copied" : "Copy diagnostics"}
          </button>
          <button className="secondary-action" type="button" onClick={onResetBrowserSettings}>
            <RotateCcw size={17} />
            Reset browser settings
          </button>
        </div>
      </div>

      <div className="panel span-2">
        <div className="panel-heading">
          <div>
            <h2>Workspace Totals</h2>
            <p>Snapshot from the latest dashboard load.</p>
          </div>
        </div>
        <section className="status-grid">
          <MiniStat label="Receipts" value={String(totals.count)} />
          <MiniStat label="Verified spend" value={money(totals.total, totals.currency)} />
          <MiniStat label="Completed" value={String(totals.completed)} />
          <MiniStat label="Failed" value={String(totals.failed)} tone="danger" />
        </section>
      </div>

      <div className="panel span-2 danger-panel">
        <div className="panel-heading">
          <div>
            <h2>Danger Zone</h2>
            <p>Permanent ledger cleanup for this account.</p>
          </div>
          <AlertTriangle size={22} />
        </div>
        <div className="danger-zone-row">
          <div>
            <strong>Delete complete ledger</strong>
            <span>Removes every receipt record and stored receipt file. This cannot be undone.</span>
          </div>
          <button className="danger-action" type="button" onClick={onDeleteLedger} disabled={deletingLedger || totals.count === 0}>
            {deletingLedger ? <Loader2 size={17} /> : <Trash2 size={17} />}
            {deletingLedger ? "Deleting..." : "Delete ledger"}
          </button>
        </div>
      </div>
    </section>
  );
}

function BudgetSettings({
  budget,
  warningPercent,
  totals,
  onBudgetChange,
  onWarningPercentChange
}: {
  budget: number;
  warningPercent: number;
  totals: Totals;
  onBudgetChange: (value: number) => void;
  onWarningPercentChange: (value: number) => void;
}) {
  const budgetEnabled = budget > 0;
  const usedPercent = budgetEnabled ? Math.min((totals.total / budget) * 100, 999) : 0;
  const meterPercent = Math.min(usedPercent, 100);
  const overBudget = budgetEnabled && totals.total > budget;
  const nearLimit = budgetEnabled && usedPercent >= warningPercent;
  const stateLabel = !budgetEnabled
    ? "No target set"
    : overBudget
      ? "Over budget"
      : nearLimit
        ? "Near limit"
        : "On track";

  return (
    <div className="panel budget-panel">
      <div className="panel-heading">
        <div>
          <h2>Budget Guardrail</h2>
          <p>Track verified spend against a monthly target.</p>
        </div>
        <Target size={22} />
      </div>

      <div className="budget-editor">
        <label>
          Monthly target
          <input
            type="number"
            min="0"
            step="1"
            value={budget || ""}
            placeholder="Set amount"
            onChange={(event) => onBudgetChange(Number(event.target.value || 0))}
          />
        </label>
        <label>
          Warning point
          <select value={warningPercent} onChange={(event) => onWarningPercentChange(Number(event.target.value))}>
            {[60, 70, 80, 90, 100].map((percent) => (
              <option key={percent} value={percent}>
                {percent}%
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className={`budget-meter ${overBudget ? "over" : nearLimit ? "warn" : ""}`}>
        <div>
          <span>{stateLabel}</span>
          <strong>{budgetEnabled ? `${Math.round(usedPercent)}% used` : "Set a target"}</strong>
        </div>
        <div className="budget-track">
          <span style={{ width: `${meterPercent}%` }} />
        </div>
        <small>
          {budgetEnabled
            ? `${money(totals.total, totals.currency)} of ${money(budget, totals.currency)}`
            : "Budget tracking starts after you enter an amount."}
        </small>
      </div>
    </div>
  );
}

function ThemePreference({ theme, onToggle }: { theme: Theme; onToggle: () => void }) {
  const isDark = theme === "dark";

  return (
    <div className="theme-preference">
      <span>Theme</span>
      <div className="theme-mode-control" role="group" aria-label="Theme">
        <button className={!isDark ? "active" : ""} type="button" onClick={() => isDark && onToggle()}>
          <Sun size={16} />
          Light
        </button>
        <button className={isDark ? "active" : ""} type="button" onClick={() => !isDark && onToggle()}>
          <Moon size={16} />
          Dark
        </button>
      </div>
    </div>
  );
}

function Metric({ icon, label, value, detail }: { icon: React.ReactNode; label: string; value: string; detail: string }) {
  return (
    <article className="metric-card">
      <div className="metric-icon">{icon}</div>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  );
}

function MiniStat({ label, value, tone }: { label: string; value: string; tone?: "danger" }) {
  return (
    <article className={`mini-stat ${tone ?? ""}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function PipelineStep({ label, active }: { label: string; active: boolean }) {
  return (
    <div className={`pipeline-step ${active ? "active" : ""}`}>
      {active ? <Loader2 size={15} /> : <CheckCircle2 size={15} />}
      <span>{label}</span>
    </div>
  );
}

function StatusBadge({ status }: { status: Receipt["status"] }) {
  const icon = status === "COMPLETED" ? <CheckCircle2 size={14} /> : status === "PROCESSING" || status === "PENDING" ? <TimerReset size={14} /> : <AlertTriangle size={14} />;

  return (
    <span className={`status ${status.toLowerCase()}`}>
      {icon}
      {status.replace("_", " ")}
    </span>
  );
}

function isTerminalReceiptStatus(status: ReceiptStatus) {
  return status === "COMPLETED" || status === "NEEDS_REVIEW" || status === "FAILED" || status === "DUPLICATE" || status === "PERMANENTLY_FAILED";
}

function parseStatusEvent(event: Event): ReceiptStatus | null {
  if (!(event instanceof MessageEvent)) return null;
  try {
    const data = JSON.parse(event.data) as { status?: ReceiptStatus };
    return data.status ?? null;
  } catch {
    return null;
  }
}

function ReceiptTimeline({ receipts, loading }: { receipts: Receipt[]; loading: boolean }) {
  if (!loading && !receipts.length) {
    return <p className="empty-state">No receipts uploaded yet.</p>;
  }

  return (
    <div className="timeline-list">
      {receipts.map((receipt) => (
        <article className="timeline-item" key={receipt.id}>
          <div className="timeline-icon">
            <ReceiptText size={16} />
          </div>
          <div>
            <strong>{receipt.vendor || receipt.originalFilename}</strong>
            <span>{receipt.receiptDate ?? formatDate(receipt.createdAt)} {receipt.merchantCategory ? `/ ${titleCase(receipt.merchantCategory)}` : ""}</span>
          </div>
          <StatusBadge status={receipt.status} />
          <strong className="amount-cell">{receipt.total ? money(receipt.total, receipt.currency ?? "INR") : "Pending"}</strong>
        </article>
      ))}
    </div>
  );
}

function MonthlyLedger({
  receipts,
  loading,
  deletingReceiptId,
  onEditReceipt,
  onDeleteReceipt
}: {
  receipts: Receipt[];
  loading: boolean;
  deletingReceiptId: string | null;
  onEditReceipt: (receipt: Receipt) => void;
  onDeleteReceipt: (receipt: Receipt) => void;
}) {
  const groups = useMemo(() => groupReceiptsByDate(receipts), [receipts]);

  if (!loading && !receipts.length) {
    return <p className="empty-state">No ledger entries for this month.</p>;
  }

  return (
    <div className="monthly-ledger">
      {groups.map((group) => (
        <section className="date-ledger-group" key={group.key}>
          <div className="date-ledger-heading">
            <div>
              <strong>{formatDate(group.key)}</strong>
              <span>{group.receipts.length} receipts</span>
            </div>
            <span>{money(group.total, group.currency)}</span>
          </div>
          <LedgerTable
            receipts={group.receipts}
            loading={loading}
            deletingReceiptId={deletingReceiptId}
            onEditReceipt={onEditReceipt}
            onDeleteReceipt={onDeleteReceipt}
          />
        </section>
      ))}
    </div>
  );
}

function LedgerTable({
  receipts,
  loading,
  deletingReceiptId,
  onEditReceipt,
  onDeleteReceipt
}: {
  receipts: Receipt[];
  loading: boolean;
  deletingReceiptId: string | null;
  onEditReceipt: (receipt: Receipt) => void;
  onDeleteReceipt: (receipt: Receipt) => void;
}) {
  const rows = useMemo(() => {
    let running = 0;
    return receipts.map((receipt) => {
      const amount = Number(receipt.total ?? 0);
      const isCompleted = receipt.status === "COMPLETED";
      if (isCompleted) running += amount;

      return {
        receipt,
        amount: isCompleted ? amount : 0,
        running
      };
    });
  }, [receipts]);

  if (!loading && !receipts.length) {
    return <p className="empty-state">No ledger entries yet.</p>;
  }

  return (
    <div className="ledger-table">
      <div className="ledger-row ledger-head">
        <span>Receipt</span>
        <span>Status</span>
        <span>Date</span>
        <span>Category</span>
        <span>Journal</span>
        <span>Actions</span>
      </div>
      {rows.map(({ receipt, amount, running }) => (
        <div className="ledger-row" key={receipt.id}>
          <div className="receipt-cell">
            <strong>{receipt.vendor || receipt.originalFilename}</strong>
            <span>{receipt.originalFilename}</span>
          </div>
          <StatusBadge status={receipt.status} />
          <span className="date-cell">{receipt.receiptDate ?? formatDate(receipt.createdAt)}</span>
          <span>{receipt.merchantCategory ? titleCase(receipt.merchantCategory) : "Uncategorized"}</span>
          <JournalPreview receipt={receipt} amount={amount} running={running} />
          <div className="row-actions">
            <button
              className="row-action-button"
              type="button"
              onClick={() => onEditReceipt(receipt)}
              disabled={receipt.status !== "COMPLETED"}
              aria-label={`Correct ${receipt.vendor || receipt.originalFilename}`}
              title={receipt.status === "COMPLETED" ? "Edit ledger entry" : "Only completed receipts can be edited"}
            >
              <Edit3 size={16} />
              <span>Edit</span>
            </button>
            <button
              className="icon-button danger-icon"
              type="button"
              onClick={() => onDeleteReceipt(receipt)}
              disabled={deletingReceiptId === receipt.id}
              aria-label={`Delete ${receipt.vendor || receipt.originalFilename}`}
              title="Delete receipt"
            >
              {deletingReceiptId === receipt.id ? <Loader2 size={16} /> : <Trash2 size={16} />}
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}

function CorrectionPanel({
  receipt,
  saving,
  onCancel,
  onSubmit
}: {
  receipt: Receipt;
  saving: boolean;
  onCancel: () => void;
  onSubmit: (receiptId: string, correction: ReceiptCorrectionRequest) => void;
}) {
  const panelRef = useRef<HTMLElement | null>(null);
  const [vendor, setVendor] = useState(receipt.vendor ?? "");
  const [merchantCategory, setMerchantCategory] = useState(receipt.merchantCategory ?? "OTHER");
  const [receiptDate, setReceiptDate] = useState(receipt.receiptDate ?? "");
  const [total, setTotal] = useState(String(receipt.total ?? ""));
  const [currency, setCurrency] = useState(receipt.currency ?? "INR");
  const [reason, setReason] = useState("");

  useEffect(() => {
    panelRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [receipt.id]);

  function submit(event: FormEvent) {
    event.preventDefault();
    onSubmit(receipt.id, {
      vendor,
      merchantCategory,
      receiptDate: receiptDate || null,
      total: total ? Number(total) : null,
      currency,
      reason
    });
  }

  return (
    <section className="panel correction-panel" ref={panelRef}>
      <div className="panel-heading">
        <div>
          <h2>Edit Ledger Entry</h2>
          <p>{receipt.vendor || receipt.originalFilename}</p>
        </div>
        <Edit3 size={22} />
      </div>
      <form className="correction-form" onSubmit={submit}>
        <label>
          Vendor
          <input value={vendor} onChange={(event) => setVendor(event.target.value)} />
        </label>
        <label>
          Category
          <select value={merchantCategory} onChange={(event) => setMerchantCategory(event.target.value)}>
            {["FOOD", "TRANSPORT", "SHOPPING", "ENTERTAINMENT", "HEALTH", "UTILITIES", "OTHER"].map((category) => (
              <option key={category} value={category}>
                {titleCase(category)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Date
          <input type="date" value={receiptDate} onChange={(event) => setReceiptDate(event.target.value)} />
        </label>
        <label>
          Total
          <input type="number" min="0.01" step="0.01" value={total} onChange={(event) => setTotal(event.target.value)} required />
        </label>
        <label>
          Currency
          <input value={currency} maxLength={10} onChange={(event) => setCurrency(event.target.value.toUpperCase())} />
        </label>
        <label className="span-2">
          Reason
          <input value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Wrong total, recategorized, vendor fix..." />
        </label>
        <div className="correction-actions span-2">
          <button className="secondary-action" type="button" onClick={onCancel} disabled={saving}>
            Cancel
          </button>
          <button className="primary-action compact" type="submit" disabled={saving}>
            {saving ? "Saving..." : "Save ledger edit"}
          </button>
        </div>
      </form>
    </section>
  );
}

function JournalPreview({ receipt, amount, running }: { receipt: Receipt; amount: number; running: number }) {
  if (!receipt.journalEntry?.lines.length) {
    return (
      <div className="amount-stack">
        <strong>{amount ? money(amount, receipt.currency ?? "INR") : "-"}</strong>
        <span>{running ? `Register ${money(running, receipt.currency ?? "INR")}` : "No journal yet"}</span>
      </div>
    );
  }

  const currency = receipt.journalEntry.currency;
  const debitTotal = receipt.journalEntry.lines.reduce((sum, line) => sum + Number(line.debit), 0);
  const creditTotal = receipt.journalEntry.lines.reduce((sum, line) => sum + Number(line.credit), 0);
  const balanced = Math.abs(debitTotal - creditTotal) < 0.01;

  return (
    <div className="journal-preview">
      <div className="journal-entry-head">
        <strong>{receipt.journalEntry.description}</strong>
        <span className={balanced ? "balance-pill balanced" : "balance-pill unbalanced"}>
          {balanced ? "Balanced" : "Unbalanced"}
        </span>
      </div>
      <div className="journal-compact" aria-label={`Journal entry for ${receipt.vendor || receipt.originalFilename}`}>
        <span>
          Debit <strong>{money(debitTotal, currency)}</strong>
        </span>
        <span>
          Credit <strong>{money(creditTotal, currency)}</strong>
        </span>
      </div>
      {(receipt.journalEntries?.length ?? 0) > 1 && (
        <span className="journal-audit">{receipt.journalEntries?.length} audit entries</span>
      )}
    </div>
  );
}

function InsightList({ insights, loading, limit }: { insights: InsightsResponse | null; loading: boolean; limit?: number }) {
  const rows = (insights?.insights ?? []).slice(0, limit);

  return (
    <div className="insight-list">
      {rows.map((insight) => (
        <article key={`${insight.type}-${insight.title}`} className="insight-item">
          <span>{insight.type}</span>
          <h3>{insight.title}</h3>
          <p>{insight.detail}</p>
        </article>
      ))}
      {!loading && !rows.length && <p className="empty-state">No insights yet.</p>}
    </div>
  );
}

function CategoryList({
  summary,
  insights,
  currency
}: {
  summary: MonthlySummary | SummaryItem[] | Record<string, number> | null;
  insights: InsightsResponse | null;
  currency: string | null;
}) {
  const rows = useMemo(() => normalizeCategories(summary, insights), [summary, insights]);
  const max = Math.max(...rows.map((row) => row.amount), 1);

  if (!rows.length) {
    return <p className="empty-state">No category totals yet.</p>;
  }

  return (
    <div className="category-list">
      {rows.slice(0, 8).map((row) => (
        <div className="category-row" key={row.name}>
          <div>
            <strong>{titleCase(row.name)}</strong>
            <span>{money(row.amount, currency)}</span>
          </div>
          <div className="bar-track">
            <span style={{ width: `${Math.max((row.amount / max) * 100, 5)}%` }} />
          </div>
        </div>
      ))}
    </div>
  );
}

function MerchantList({ summary, currency }: { summary: MonthlySummary | null; currency: string | null }) {
  const rows = Object.entries(summary?.byMerchant ?? {})
    .map(([name, amount]) => ({ name, amount: Number(amount) }))
    .sort((a, b) => b.amount - a.amount)
    .slice(0, 8);
  const max = Math.max(...rows.map((row) => row.amount), 1);

  if (!rows.length) {
    return <p className="empty-state">No merchant totals yet.</p>;
  }

  return (
    <div className="category-list">
      {rows.map((row) => (
        <div className="category-row" key={row.name}>
          <div>
            <strong>{row.name}</strong>
            <span>{money(row.amount, currency)}</span>
          </div>
          <div className="bar-track merchant">
            <span style={{ width: `${Math.max((row.amount / max) * 100, 5)}%` }} />
          </div>
        </div>
      ))}
    </div>
  );
}

function MonthlySpendChart({
  rows,
  currency,
  loading
}: {
  rows: Array<{ key: string; label: string; total: number; count: number }>;
  currency: string;
  loading: boolean;
}) {
  const max = Math.max(...rows.map((row) => row.total), 1);

  if (!loading && !rows.length) {
    return <p className="empty-state">No completed receipt trend yet.</p>;
  }

  return (
    <div className="monthly-chart" aria-label="Monthly spend trend">
      {rows.map((row) => {
        const height = Math.max((row.total / max) * 100, 8);
        return (
          <div className="monthly-chart-column" key={row.key}>
            <div className="monthly-chart-value">{money(row.total, currency)}</div>
            <div className="monthly-chart-bar" aria-hidden="true">
              <span style={{ "--bar-size": `${height}%` } as React.CSSProperties} />
            </div>
            <strong>{row.label}</strong>
            <small>{row.count} receipts</small>
          </div>
        );
      })}
    </div>
  );
}

function StatusMix({ rows, total }: { rows: Array<{ status: ReceiptStatus; count: number }>; total: number }) {
  if (!rows.length) {
    return <p className="empty-state">No receipt status data yet.</p>;
  }

  return (
    <div className="status-mix">
      {rows.map((row) => {
        const percent = total ? Math.round((row.count / total) * 100) : 0;
        return (
          <div className="status-mix-row" key={row.status}>
            <div>
              <StatusBadge status={row.status} />
              <strong>{row.count}</strong>
            </div>
            <div className="bar-track">
              <span style={{ width: `${Math.max(percent, row.count ? 5 : 0)}%` }} />
            </div>
            <small>{percent}% of loaded receipts</small>
          </div>
        );
      })}
    </div>
  );
}

function HighValueList({ receipts, currency }: { receipts: Receipt[]; currency: string }) {
  if (!receipts.length) {
    return <p className="empty-state">No completed receipt totals yet.</p>;
  }

  return (
    <div className="high-value-list">
      {receipts.map((receipt, index) => (
        <div className="high-value-row" key={receipt.id}>
          <span>{index + 1}</span>
          <div>
            <strong>{receipt.vendor || receipt.originalFilename}</strong>
            <small>{receipt.receiptDate ? formatDate(receipt.receiptDate) : formatDate(receipt.createdAt)}</small>
          </div>
          <b>{money(Number(receipt.total ?? 0), receipt.currency ?? currency)}</b>
        </div>
      ))}
    </div>
  );
}

function SettingRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="setting-row">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function ToggleRow({
  icon,
  label,
  detail,
  checked,
  onChange
}: {
  icon: React.ReactNode;
  label: string;
  detail: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className="toggle-row">
      <span className="toggle-icon">{icon}</span>
      <span>
        <strong>{label}</strong>
        <small>{detail}</small>
      </span>
      <input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} />
    </label>
  );
}

function buildReceiptMonthOptions(receipts: Receipt[]) {
  const counts = new Map<string, number>();
  receipts.forEach((receipt) => {
    const key = receiptMonthKey(receipt);
    counts.set(key, (counts.get(key) ?? 0) + 1);
  });

  return Array.from(counts.entries())
    .sort(([a], [b]) => b.localeCompare(a))
    .map(([key, count]) => ({ key, count, label: formatMonthLabel(key) }));
}

function buildExpenseCards(receipts: Receipt[], mode: "monthly" | "yearly", currency: CurrencyCode): ExpenseCard[] {
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

function summarizeExpenseCategories(receipts: Receipt[]) {
  const totals = new Map<string, number>();
  receipts.forEach((receipt) => {
    const category = receipt.merchantCategory ? titleCase(receipt.merchantCategory) : "Uncategorized";
    totals.set(category, (totals.get(category) ?? 0) + Number(receipt.total ?? 0));
  });

  return Array.from(totals.entries())
    .map(([name, amount]) => ({ name, amount }))
    .sort((a, b) => b.amount - a.amount);
}

function receiptMonthKey(receipt: Receipt) {
  return receiptDateValue(receipt).slice(0, 7);
}

function receiptDateValue(receipt: Receipt) {
  return receipt.receiptDate ?? receipt.createdAt.slice(0, 10);
}

function summarizeReceipts(receipts: Receipt[], currency: CurrencyCode): Totals {
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

function buildAnalytics(receipts: Receipt[], currency: CurrencyCode) {
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

function filterReceipts(receipts: Receipt[], query: string) {
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
      receipt.currency
    ]
      .filter((value) => value !== null && value !== undefined)
      .join(" ")
      .toLowerCase();

    return haystack.includes(term);
  });
}

function exportReceiptsCsv(receipts: Receipt[]) {
  const headers = ["Date", "Vendor", "Filename", "Category", "Status", "Total", "Currency"];
  const rows = receipts.map((receipt) => [
    receiptDateValue(receipt),
    receipt.vendor ?? "",
    receipt.originalFilename,
    receipt.merchantCategory ?? "",
    receipt.status,
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

function groupReceiptsByDate(receipts: Receipt[]) {
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

function formatMonthLabel(monthKey: string) {
  const [year, month] = monthKey.split("-").map(Number);
  return new Intl.DateTimeFormat("en-IN", { month: "long", year: "numeric" }).format(new Date(year, month - 1, 1));
}

function shortMonthLabel(monthKey: string) {
  const [year, month] = monthKey.split("-").map(Number);
  return new Intl.DateTimeFormat("en-IN", { month: "short", year: "2-digit" }).format(new Date(year, month - 1, 1));
}

function normalizeCategories(
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

function money(value: number, currency: string | null = "INR") {
  if (!currency) {
    return `${new Intl.NumberFormat("en-IN", { maximumFractionDigits: 0 }).format(Number(value || 0))} mixed`;
  }

  return new Intl.NumberFormat(localeForCurrency(currency), {
    style: "currency",
    currency,
    maximumFractionDigits: 0
  }).format(Number(value || 0));
}

function localeForCurrency(currency: string) {
  const locales: Record<string, string> = {
    INR: "en-IN",
    USD: "en-US",
    EUR: "de-DE",
    GBP: "en-GB",
    AUD: "en-AU",
    CAD: "en-CA",
    SGD: "en-SG",
    LKR: "en-LK"
  };

  return locales[currency.toUpperCase()] ?? "en-US";
}

function formatDate(value: string) {
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split("-").map(Number);
    return new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", year: "numeric" }).format(new Date(year, month - 1, day));
  }

  return new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", year: "numeric" }).format(new Date(value));
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function titleCase(value: string) {
  return value
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export default App;
