import { createSSRApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import { previewDiagnosticsEnabled, recordPreviewError } from './services/preview-diagnostics';
export function createApp() {
  const app = createSSRApp(App);
  app.use(createPinia());
  if (previewDiagnosticsEnabled) {
    const previousHandler = app.config.errorHandler;
    app.config.errorHandler = (error, instance, info) => {
      recordPreviewError(`vue:${info}`, error);
      if (previousHandler) previousHandler(error, instance, info);
      else console.error(error);
    };
  }
  return { app };
}
