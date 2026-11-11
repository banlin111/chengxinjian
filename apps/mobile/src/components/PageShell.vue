<script setup lang="ts">
import { ref } from 'vue';
import { onHide, onShow } from '@dcloudio/uni-app';
import { useThemeStore } from '../stores/theme';
const props = defineProps<{ home?: boolean; secondary?: boolean; motion?: boolean }>();
const theme = useThemeStore();
const active = ref(true);
const top = ref(64);
function updateTop() {
  // #ifdef MP-WEIXIN
  const system = uni.getSystemInfoSync();
  const capsule = uni.getMenuButtonBoundingClientRect();
  top.value = props.secondary
    ? Math.max(64, capsule.bottom + 16)
    : Math.max(64, capsule.top + 8, (system.statusBarHeight || 24) + 20);
  // #endif
}
updateTop();
onShow(() => {
  active.value = true; theme.applyNative();
  updateTop();
});
onHide(() => { active.value = false; });
</script>
<template>
  <view class="page-shell" :class="[{ 'home-page': home, 'home-motion': motion, 'motion-reduced': theme.reducedMotion, 'page-inactive': !active }, `theme-${theme.current}`]" :style="`${theme.cssVars};--top:${top}px`">
    <slot />
  </view>
</template>
