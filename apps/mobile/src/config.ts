export const config = Object.freeze({
  useMock: import.meta.env.VITE_USE_MOCK !== 'false',
  demoModules: import.meta.env.VITE_DEMO_MODULES !== 'false',
  apiBase: (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1').replace(/\/$/, ''),
});
