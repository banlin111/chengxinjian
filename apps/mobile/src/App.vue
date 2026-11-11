<script lang="ts">
import { recordPreviewError } from './services/preview-diagnostics';
import { useThemeStore } from './stores/theme';
import { usePreviewPreferences } from './stores/preview-preferences';
export default {
  onLaunch() { useThemeStore().restore(); usePreviewPreferences().restore(); },
  onError(error: string) { recordPreviewError('app', error); },
  onUnhandledRejection(event: { reason: unknown }) { recordPreviewError('promise', event.reason); },
};
</script>
<style>
@import './styles/base.css';
@import './styles/prototype-utilities.css';
@import './styles/screens.css';
@import './styles/polish.css';
@import './styles/remaining.css';
</style>
