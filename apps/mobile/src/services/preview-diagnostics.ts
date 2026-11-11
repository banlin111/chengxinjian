// Local, bounded diagnostics for development previews. No network reporting.
export const PREVIEW_BUILD = '2026-11-11.android.2';
const KEY = 'cxj.preview-errors.v1';
type Entry = { time: string; build: string; source: string; route: string; message: string };
let enabled = false;
// #ifdef MP-WEIXIN
try { enabled = uni.getAccountInfoSync().miniProgram.envVersion === 'develop'; } catch { /* unavailable */ }
// #endif
export const previewDiagnosticsEnabled = enabled;
let recording = false;
export function recordPreviewError(source: string, error: unknown) {
  if (!enabled || recording) return;
  recording = true;
  try {
    const message = error instanceof Error ? `${error.message}\n${error.stack || ''}` : String(error);
    const pages = getCurrentPages();
    const saved = uni.getStorageSync(KEY);
    const entries: Entry[] = Array.isArray(saved) ? saved.slice(-9) : [];
    entries.push({ time: new Date().toISOString(), build: PREVIEW_BUILD, source,
      route: pages[pages.length - 1]?.route || '', message: message.slice(0, 2000) });
    uni.setStorageSync(KEY, entries);
  } catch { /* Diagnostics must never interrupt the app. */ }
  finally { recording = false; }
}
export function copyPreviewDiagnostics() {
  if (!enabled) return;
  try {
    const system = uni.getSystemInfoSync();
    const saved = uni.getStorageSync(KEY);
    const data = JSON.stringify({ build: PREVIEW_BUILD, platform: system.platform,
      system: system.system, wechat: system.version, sdk: system.SDKVersion,
      errors: Array.isArray(saved) ? saved : [] }, null, 2);
    uni.setClipboardData({ data, fail: () => uni.showToast({ title: '复制失败，请重试', icon: 'none' }) });
  } catch { uni.showToast({ title: '暂时无法读取诊断', icon: 'none' }); }
}
