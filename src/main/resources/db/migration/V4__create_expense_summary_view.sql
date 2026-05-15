CREATE OR REPLACE VIEW monthly_expense_summary AS
SELECT
    user_id,
    DATE_TRUNC('month', receipt_date) AS month,
    merchant_category AS category,
    COUNT(*) AS receipt_count,
    SUM(total) AS total_spent,
    currency
FROM receipts
WHERE status = 'COMPLETED'
  AND receipt_date IS NOT NULL
GROUP BY user_id, DATE_TRUNC('month', receipt_date), merchant_category, currency
ORDER BY month DESC;