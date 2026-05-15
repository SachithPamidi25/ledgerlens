export type AuthResponse = {
  accessToken: string;
  refreshToken: string;
};

export type ReceiptStatus =
  | "PENDING"
  | "PROCESSING"
  | "COMPLETED"
  | "FAILED"
  | "DUPLICATE"
  | "PERMANENTLY_FAILED";

export type Receipt = {
  id: string;
  originalFilename: string;
  storageKey: string;
  status: ReceiptStatus;
  vendor?: string | null;
  merchantCategory?: string | null;
  receiptDate?: string | null;
  subtotal?: number | null;
  tax?: number | null;
  tip?: number | null;
  total?: number | null;
  currency?: string | null;
  journalEntry?: JournalEntry | null;
  createdAt: string;
  updatedAt: string;
};

export type AccountType = "ASSET" | "LIABILITY" | "EQUITY" | "INCOME" | "EXPENSE";

export type JournalLine = {
  accountId: string;
  accountCode: string;
  accountName: string;
  accountType: AccountType;
  debit: number;
  credit: number;
};

export type JournalEntry = {
  id: string;
  entryDate: string;
  description: string;
  currency: string;
  lines: JournalLine[];
};

export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export type SummaryItem = {
  category?: string;
  merchantCategory?: string;
  total?: number;
  amount?: number;
  receiptCount?: number;
  count?: number;
};

export type MonthlySummary = {
  totalSpent: number;
  receiptCount: number;
  byCategory: Record<string, number>;
  byMerchant: Record<string, number>;
  period: string;
};

export type Insight = {
  type: string;
  title: string;
  detail: string;
};

export type InsightsResponse = {
  period: string;
  totalSpent: number;
  receiptCount: number;
  byCategory: Record<string, number>;
  insights: Insight[];
  generatedAt: string;
};

export type UploadUrlResponse = {
  receiptId: string;
  uploadUrl: string;
  storageKey: string;
};
