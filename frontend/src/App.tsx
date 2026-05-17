import {
  AlertTriangle,
  BarChart3,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  FileUp,
  Home,
  Layers3,
  Loader2,
  LogOut,
  Moon,
  ReceiptText,
  RefreshCcw,
  Settings,
  Sparkles,
  Sun,
  TimerReset,
  Trash2,
  UploadCloud,
  WalletCards
} from "lucide-react";
import { FormEvent, Fragment, useCallback, useEffect, useMemo, useState } from "react";
import {
  clearTokens,
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
import type { InsightsResponse, MonthlySummary, Page, Receipt, ReceiptStatus, SummaryItem } from "./types";

type AuthMode = "login" | "register";
type AppPage = "overview" | "upload" | "receipts" | "insights" | "settings";
type Theme = "light" | "dark";
type CurrencyCode = "INR" | "USD" | "EUR" | "GBP" | "AUD" | "CAD" | "SGD" | "LKR";

type Totals = {
  total: number;
  currency: CurrencyCode;
  count: number;
  completed: number;
  failed: number;
  processing: number;
  duplicate: number;
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

const navItems: Array<{ id: AppPage; label: string; icon: React.ReactNode }> = [
  { id: "overview", label: "Overview", icon: <Home size={18} /> },
  { id: "upload", label: "Upload", icon: <UploadCloud size={18} /> },
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
  const [page, setPage] = useState<AppPage>("overview");
  const [receipts, setReceipts] = useState<Page<Receipt> | null>(null);
  const [summary, setSummary] = useState<MonthlySummary | null>(null);
  const [insights, setInsights] = useState<InsightsResponse | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<{ current: number; total: number } | null>(null);
  const [activeReceiptIds, setActiveReceiptIds] = useState<string[]>([]);
  const [deletingReceiptId, setDeletingReceiptId] = useState<string | null>(null);
  const [deletingLedger, setDeletingLedger] = useState(false);
  const [displayCurrency, setDisplayCurrency] = useState<CurrencyCode>(() => {
    const saved = localStorage.getItem("ledgerlens.currency");
    return isCurrencyCode(saved) ? saved : "INR";
  });

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
    localStorage.setItem("ledgerlens.currency", displayCurrency);
  }, [displayCurrency]);

  useEffect(() => {
    if (!activeReceiptIds.length) return;

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
  }, [activeReceiptIds, loadData]);

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
      setPage("receipts");
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
        {page === "receipts" && (
          <ReceiptsPage
            receipts={receiptList}
            loading={loading}
            totals={totals}
            deletingReceiptId={deletingReceiptId}
            onDeleteReceipt={handleDeleteReceipt}
          />
        )}
        {page === "insights" && <InsightsPage summary={summary} insights={insights} totals={totals} loading={loading} />}
        {page === "settings" && (
          <SettingsPage
            theme={theme}
            totals={totals}
            displayCurrency={displayCurrency}
            deletingLedger={deletingLedger}
            onToggleTheme={onToggleTheme}
            onCurrencyChange={setDisplayCurrency}
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
    : "Drop in receipt images or PDFs.";

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
            accept="image/*,.pdf"
            multiple
            disabled={uploading}
            onChange={(event) => onFiles(event.target.files ?? undefined)}
          />
          {uploading ? <Loader2 size={30} /> : <UploadCloud size={34} />}
          <span>{uploading ? progressLabel : "Choose receipt files"}</span>
          <small>PNG, JPG, WEBP, or PDF</small>
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

function ReceiptsPage({
  receipts,
  loading,
  totals,
  deletingReceiptId,
  onDeleteReceipt
}: {
  receipts: Receipt[];
  loading: boolean;
  totals: Totals;
  deletingReceiptId: string | null;
  onDeleteReceipt: (receipt: Receipt) => void;
}) {
  return (
    <div className="page-stack">
      <section className="status-grid">
        <MiniStat label="Completed" value={String(totals.completed)} />
        <MiniStat label="Processing" value={String(totals.processing)} />
        <MiniStat label="Duplicates" value={String(totals.duplicate)} />
        <MiniStat label="Failed" value={String(totals.failed)} tone="danger" />
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Ledger Entries</h2>
            <p>{loading ? "Loading..." : `${receipts.length} receipts visible`}</p>
          </div>
        </div>
        <LedgerTable
          receipts={receipts}
          loading={loading}
          deletingReceiptId={deletingReceiptId}
          onDeleteReceipt={onDeleteReceipt}
        />
      </section>
    </div>
  );
}

function InsightsPage({
  summary,
  insights,
  totals,
  loading
}: {
  summary: MonthlySummary | null;
  insights: InsightsResponse | null;
  totals: Totals;
  loading: boolean;
}) {
  return (
    <section className="insights-layout">
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
  deletingLedger,
  onToggleTheme,
  onCurrencyChange,
  onRefresh,
  onLogout,
  onDeleteLedger
}: {
  theme: Theme;
  totals: Totals;
  displayCurrency: CurrencyCode;
  deletingLedger: boolean;
  onToggleTheme: () => void;
  onCurrencyChange: (currency: CurrencyCode) => void;
  onRefresh: () => void;
  onLogout: () => void;
  onDeleteLedger: () => void;
}) {
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
          <SettingRow label="Insights" value="Claude-backed summaries" />
        </div>
      </div>

      <div className="panel">
        <div className="panel-heading">
          <div>
            <h2>Preferences</h2>
            <p>Choose how ledger values are displayed in this browser.</p>
          </div>
        </div>
        <div className="preference-controls">
          <ThemePreference theme={theme} onToggle={onToggleTheme} />
          <CurrencySelect value={displayCurrency} onChange={onCurrencyChange} />
        </div>
      </div>

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
  return status === "COMPLETED" || status === "FAILED" || status === "DUPLICATE" || status === "PERMANENTLY_FAILED";
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

function LedgerTable({
  receipts,
  loading,
  deletingReceiptId,
  onDeleteReceipt
}: {
  receipts: Receipt[];
  loading: boolean;
  deletingReceiptId: string | null;
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
      ))}
    </div>
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
      <div className="journal-lines" aria-label={`Journal entry for ${receipt.vendor || receipt.originalFilename}`}>
        <span>Account</span>
        <span>Debit</span>
        <span>Credit</span>
        {receipt.journalEntry.lines.map((line) => {
          const debit = Number(line.debit);
          const credit = Number(line.credit);

          return (
            <Fragment key={`${line.accountId}-${debit}-${credit}`}>
              <strong>{debit > 0 ? `Dr ${line.accountName}` : `Cr ${line.accountName}`}</strong>
              <span>{debit > 0 ? money(debit, currency) : "-"}</span>
              <span>{credit > 0 ? money(credit, currency) : "-"}</span>
            </Fragment>
          );
        })}
        <strong>Total</strong>
        <strong>{money(debitTotal, currency)}</strong>
        <strong>{money(creditTotal, currency)}</strong>
      </div>
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

function SettingRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="setting-row">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
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
