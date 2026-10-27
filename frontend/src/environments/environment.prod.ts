export const environment = {
  production: true,
  // Served by nginx, which proxies /api and /ws to the backend container,
  // so the base URL is same-origin. HealthService appends '/api/health'.
  apiBaseUrl: '',
  wsUrl: '/ws'
};
