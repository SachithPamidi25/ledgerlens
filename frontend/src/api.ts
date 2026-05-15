import type { AuthResponse, InsightsResponse, MonthlySummary, Page, Receipt, UploadUrlResponse } from "./types";

const ACCESS_TOKEN_KEY = "ledgerlens.accessToken";
const REFRESH_TOKEN_KEY = "ledgerlens.refreshToken";

let refreshPromise: Promise<AuthResponse> | null = null;

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function saveTokens(tokens: AuthResponse) {
  localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

async function parseResponse<T>(response: Response): Promise<T> {
  const text = await response.text();
  const data = text ? safeJson(text) : null;

  if (!response.ok) {
    const message = typeof data === "string" ? data : data?.error ?? response.statusText;
    throw new Error(message);
  }

  return data as T;
}

function safeJson(text: string) {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function request<T>(path: string, options: RequestInit = {}, allowRefresh = true): Promise<T> {
  const headers = new Headers(options.headers);
  if (!headers.has("Content-Type") && options.body) {
    headers.set("Content-Type", "application/json");
  }

  const token = getAccessToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(path, { ...options, headers });
  if (response.status === 401 && allowRefresh && getRefreshToken()) {
    await refreshSession();
    return request<T>(path, options, false);
  }

  return parseResponse<T>(response);
}

export async function login(email: string, password: string) {
  const tokens = await request<AuthResponse>(
    "/api/auth/login",
    { method: "POST", body: JSON.stringify({ email, password }) },
    false
  );
  saveTokens(tokens);
  return tokens;
}

export async function register(fullName: string, email: string, password: string) {
  const tokens = await request<AuthResponse>(
    "/api/auth/register",
    { method: "POST", body: JSON.stringify({ fullName, email, password }) },
    false
  );
  saveTokens(tokens);
  return tokens;
}

export async function refreshSession() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new Error("Session expired");
  }

  if (!refreshPromise) {
    refreshPromise = request<AuthResponse>(
      "/api/auth/refresh",
      { method: "POST", body: JSON.stringify({ refreshToken }) },
      false
    )
      .then((tokens) => {
        saveTokens(tokens);
        return tokens;
      })
      .catch((error) => {
        clearTokens();
        throw error;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }

  return refreshPromise;
}

export async function logout() {
  const refreshToken = getRefreshToken();
  try {
    await request<string>(
      "/api/auth/logout",
      {
        method: "POST",
        body: refreshToken ? JSON.stringify({ refreshToken }) : undefined
      },
      false
    );
  } finally {
    clearTokens();
  }
}

export function getReceipts() {
  return request<Page<Receipt>>("/api/receipts?size=12");
}

export function deleteReceipt(id: string) {
  return request<void>(`/api/receipts/${id}`, { method: "DELETE" });
}

export function deleteLedger() {
  return request<{ deleted: number }>("/api/receipts", { method: "DELETE" });
}

export function getMonthlySummary(year?: number, month?: number) {
  const query = year && month ? `?year=${year}&month=${month}` : "";
  return request<MonthlySummary>(`/api/expenses/summary${query}`);
}

export function getInsights(months = 3) {
  return request<InsightsResponse>(`/api/insights?months=${months}`);
}

export async function uploadReceipt(file: File, onUploaded?: () => void) {
  const idempotencyKey = crypto.randomUUID();
  const upload = await request<UploadUrlResponse>(
    `/api/receipts/upload-url?filename=${encodeURIComponent(file.name)}`,
    {
      method: "POST",
      headers: { "X-Idempotency-Key": idempotencyKey }
    }
  );

  const uploadResponse = await fetch(upload.uploadUrl, {
    method: "PUT",
    body: file,
    headers: {
      "Content-Type": file.type || "application/octet-stream"
    }
  });

  if (!uploadResponse.ok) {
    throw new Error("Upload failed");
  }

  onUploaded?.();
  await request<void>(`/api/receipts/${upload.receiptId}/process`, { method: "POST" });
  return upload.receiptId;
}
