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
};

export const stocks = {
  add: (ticker) => api.post(`/stocks/${ticker}`),
  get: (ticker) => api.get(`/stocks/${ticker}`),
  refreshPrice: (ticker) => api.post(`/stocks/${ticker}/refresh-price`),
  refreshBigFive: (ticker) => api.post(`/stocks/${ticker}/refresh-big-five`),
  saveManualBigFive: (ticker, data) => api.post(`/stocks/${ticker}/big-five/manual`, data),
  getBigFive: (ticker) => api.get(`/stocks/${ticker}/big-five`),
  getBigFiveBySource: (ticker, source) => api.get(`/stocks/${ticker}/big-five/${source}`),
};

export const investments = {
  buy: (data) => api.post('/investments/buy', data),
  sell: (data) => api.post('/investments/sell', data),
  holdings: () => api.get('/investments/holdings'),
  history: () => api.get('/investments/history'),
  refreshPrices: () => api.post('/investments/refresh-prices'),
};

export const stickerPrice = {
  calculate: (data) => api.post('/sticker-price/calculate', data),
  defaultPe: (growthPct) => api.get(`/sticker-price/default-pe?estimatedGrowthPct=${growthPct}`),
  history: (ticker) => api.get(`/sticker-price/history/${ticker}`),
  suggest: (ticker, source) => api.get(`/sticker-price/suggest/${ticker}?source=${source}`),
};

export const exportApi = {
  csv: () => api.get('/export/csv', { responseType: 'blob' }),
};

export const checklist = {
  items: () => api.get('/checklist/items'),
  responses: (ticker) => api.get(`/checklist/${ticker}/responses`),
  save: (ticker, data) => api.post(`/checklist/${ticker}/responses`, data),
};

export const score = {
  get: (ticker) => api.get(`/score/${ticker}`),
};

export const watchlist = {
  list: () => api.get('/watchlist'),
  add: (ticker, notes) => api.post(`/watchlist/${ticker}`, notes),
};

export default api;
