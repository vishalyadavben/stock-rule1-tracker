export function currencySymbol(currency) {
  if (!currency) return '$';
  if (currency.toUpperCase() === 'INR') return '₹';
  if (currency.toUpperCase() === 'USD') return '$';
  return currency.toUpperCase() + ' ';
}

export function formatMoney(value, currency) {
  if (value === null || value === undefined) return '—';
  return `${currencySymbol(currency)}${Number(value).toFixed(2)}`;
}
