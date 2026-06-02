import { Moon, Sun } from "lucide-react";
import type { Theme } from "../appTypes";

export function ThemeToggle({ theme, onToggle }: { theme: Theme; onToggle: () => void }) {
  const isDark = theme === "dark";

  return (
    <button className="theme-toggle" type="button" onClick={onToggle} aria-label={`Switch to ${isDark ? "light" : "dark"} theme`} title={`${isDark ? "Light" : "Dark"} theme`}>
      {isDark ? <Sun size={18} /> : <Moon size={18} />}
      <span>{isDark ? "Light" : "Dark"}</span>
    </button>
  );
}
