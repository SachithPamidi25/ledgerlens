import { ChevronDown } from "lucide-react";
import { useState } from "react";
import type { CurrencyCode } from "../appTypes";
import { currencyOptions } from "../config/workspace";

export function CurrencySelect({ value, onChange }: { value: CurrencyCode; onChange: (currency: CurrencyCode) => void }) {
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
