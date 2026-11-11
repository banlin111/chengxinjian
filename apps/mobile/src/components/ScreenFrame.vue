<script setup lang="ts">
import PageShell from './PageShell.vue';
import { backScreen } from '../config/screens';
const props = defineProps<{ screen: number; primary?: boolean; gradient?: boolean; footer?: boolean; onBack?: () => void }>();
function back() { if (props.onBack) props.onBack(); else backScreen(); }
</script>
<template>
  <PageShell :home="gradient" :secondary="!primary">
    <view class="pdf-screen" :class="{ 'screen-with-footer': footer, 'screen-primary': primary, 'screen-refined': screen >= 9 && screen !== 13 && screen !== 17 }" :data-screen="screen">
      <!-- #ifdef H5 -->
      <view class="screen-status" aria-hidden="true">
        <text v-if="primary">9:41</text><view v-else />
        <view class="screen-status-icons"><image src="/static/ui/signal.svg" /><image src="/static/ui/wifi.svg" /><image src="/static/ui/battery.svg" /></view>
      </view>
      <!-- #endif -->
      <button role="button" v-if="!primary" class="screen-back" aria-label="返回上一页" @click="back"><image src="/static/ui/chevron.svg" /></button>
      <text v-if="[7,8,9,15,16,19,20,21,22].includes(screen)" class="screen-demo">演示数据</text>
      <slot />
      <text v-if="screen >= 9 && screen !== 13 && screen !== 17" class="refined-note">页面数据为演示，不代表真实账户与权益</text>
    </view>
  </PageShell>
</template>
<style scoped>
.pdf-screen { position: relative; min-height: calc(100vh - 30px); color: #fff; padding-bottom: 32px; font-size: 14px; line-height: 1.5; }
.screen-primary { min-height: calc(100vh - 80px); }
.screen-with-footer { padding-bottom: 118px; }
.screen-back { position: absolute; top: max(8px,calc(var(--top) - 48px)); left: 12px; z-index: 40; width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; background: rgba(15,29,31,.5); border-radius: 50%; }
.screen-back image { width: 16px; height: 16px; transform: rotate(180deg); }
.screen-demo { position: absolute; top: max(18px,calc(var(--top) - 38px)); left: 58px; z-index: 40; padding: 2px 8px; border-radius: 12px; background: rgba(15,29,31,.5); color: #A8C5BD; font-size: 10px; }
/* #ifdef H5 */
.screen-status { position: absolute; top: 0; left: 0; right: 0; z-index: 35; height: 48px; padding: 0 28px; display: flex; align-items: center; justify-content: space-between; font-size: 13px; font-weight: 600; pointer-events: none; }
.screen-status-icons { display: flex; gap: 4px; }.screen-status-icons image { width: 13px; height: 13px; }
/* #endif */
</style>
