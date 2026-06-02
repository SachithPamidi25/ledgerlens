export function money(value: number, currency: string | null = "INR") {
  if (!currency) {
    return `${new Intl.NumberFormat("en-IN", { maximumFractionDigits: 0 }).format(Number(value || 0))} mixed`;
  }

  return new Intl.NumberFormat(localeForCurrency(currency ?? "INR"), {
    style: "currency",
    currency: currency ?? "INR",
    maximumFractionDigits: 0
  }).format(Number(value || 0));
}

export function localeForCurrency(currency: string) {
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

export function formatDate(value: string) {
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split("-").map(Number);
    return new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", year: "numeric" }).format(new Date(year, month - 1, day));
  }

  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric"
  }).format(new Date(value));
}

export function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

export function titleCase(value: string) {
  return value
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}
