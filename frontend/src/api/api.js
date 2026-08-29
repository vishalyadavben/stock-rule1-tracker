import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export const auth = {
  register: (data) => api.post('/auth/register', data),
  login: (data) => api.post('/auth/login', data),
  me: () => api.get('/auth/me'),
};

export const stocks = {
  add: (ticker, currency) => api.post(`/stocks/${ticker}`, null, { params: currency ? { currency } : {} }),
  get: (ticker) => api.get(`/stocks/${ticker}`),
  refreshPrice: (ticker) => api.post(`/stocks/${ticker}/refresh-price`),
  setManualPrice: (ticker, price) => api.post(`/stocks/${ticker}/manual-price`, { price }),
  refreshBigFive: (ticker) => api.post(`/stocks/${ticker}/refresh-big-five`),
  saveManualBigFive: (ticker, data) => api.post(`/stocks/${ticker}/big-five/manual`, data),
  getBigFive: (ticker) => api.get(`/stocks/${ticker}/big-five`),
  getBigFiveBySource: (ticker, source) => api.get(`/stocks/${ticker}/big-five/${source}`),
  deleteBigFiveYear: (ticker, source, fiscalYear) => api.delete(`/stocks/${ticker}/big-five/${source}/${fiscalYear}`),
  growthRates: (ticker, source, years) => api.get(`/stocks/${ticker}/growth-rates`, { params: { source, years } }),
};

export const investments = {
  buy: (data) => api.post('/investments/buy', data),
  sell: (data) => api.post('/investments/sell', data),
  holdings: () => api.get('/investments/holdings'),
  history: () => api.get('/investments/history'),
  refreshPrices: () => api.post('/investments/refresh-prices'),
  deleteLot: (lotId) => api.delete(`/investments/lots/${lotId}`),
  editLot: (lotId, data) => api.put(`/investments/lots/${lotId}`, data),
  deleteLotConfirmed: (lotId, password) => api.post(`/investments/lots/${lotId}/delete-confirmed`, { password }),
  editExit: (exitId, data) => api.put(`/investments/exits/${exitId}`, data),
  deleteExitConfirmed: (exitId, password) => api.post(`/investments/exits/${exitId}/delete-confirmed`, { password }),
};

export const stickerPrice = {
  calculate: (data) => api.post('/sticker-price/calculate', data),
  defaultPe: (growthPct) => api.get(`/sticker-price/default-pe?estimatedGrowthPct=${growthPct}`),
  history: (ticker, ownerId) => api.get(`/sticker-price/history/${ticker}`, { params: ownerId ? { ownerId } : {} }),
  suggest: (ticker, source) => api.get(`/sticker-price/suggest/${ticker}?source=${source}`),
  remove: (id) => api.delete(`/sticker-price/${id}`),
};

export const exportApi = {
  csv: () => api.get('/export/csv', { responseType: 'blob' }),
  historyCsv: () => api.get('/export/history-csv', { responseType: 'blob' }),
  stockReport: (ticker) => api.get(`/export/report/${ticker}`, { responseType: 'blob' }),
};

export const notes = {
  list: () => api.get('/notes'),
  create: (content) => api.post('/notes', { content }),
  remove: (id) => api.delete(`/notes/${id}`),
};

export const exchangeRate = {
  get: (from, to) => api.get('/exchange-rate', { params: { from, to } }),
};

// Alias used by Dashboard's currency-conversion feature — same endpoint, kept as a separate
// export name for readability at the call site (fx.rate(...) reads more naturally there).
export const fx = {
  rate: (from, to) => api.get('/exchange-rate', { params: { from, to } }),
};

export const checklist = {
  items: () => api.get('/checklist/items'),
  responses: (ticker, ownerId) => api.get(`/checklist/${ticker}/responses`, { params: ownerId ? { ownerId } : {} }),
  save: (ticker, data) => api.post(`/checklist/${ticker}/responses`, data),
};

export const score = {
  get: (ticker, ownerId) => api.get(`/score/${ticker}`, { params: ownerId ? { ownerId } : {} }),
};

export const watchlist = {
  list: () => api.get('/watchlist'),
  add: (ticker, notes) => api.post(`/watchlist/${ticker}`, notes),
};

export const shares = {
  share: (ticker, email, permission) => api.post(`/shares/${ticker}`, { email, permission }),
  myShares: (ticker) => api.get(`/shares/${ticker}`),
  revoke: (id) => api.delete(`/shares/${id}`),
  sharedWithMe: () => api.get('/shares/shared-with-me'),
};

export default api;
