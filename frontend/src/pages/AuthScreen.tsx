import { ReceiptText } from "lucide-react";
import { FormEvent, useState } from "react";
import { login, register } from "../api";
import type { AuthMode, Theme } from "../appTypes";
import { ThemeToggle } from "../components/ThemeToggle";

export function AuthScreen({ theme, onToggleTheme, onSignedIn }: { theme: Theme; onToggleTheme: () => void; onSignedIn: () => void }) {
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
