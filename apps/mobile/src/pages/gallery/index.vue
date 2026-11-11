<script setup lang="ts">
import PageShell from '../../components/PageShell.vue';
import { screens, goScreen } from '../../config/screens';
</script>
<template>
  <PageShell>
    <view class="gallery">
      <text class="gallery-title">前端页面目录</text>
      <text class="gallery-copy">按 PDF 的 S1–S22 顺序查看。本阶段为页面与基础动效演示，数据均为示例；真实业务接口尚未完成联调。</text>
      <button role="button" v-for="screen in screens" :key="screen.id" class="gallery-row" @click="goScreen(screen.id)">
        <text class="gallery-number">S{{ screen.id }}</text><text>{{ screen.title }}</text><text class="gallery-arrow">›</text>
      </button>
    </view>
  </PageShell>
</template>
<style scoped>
.gallery { padding: 40px 20px; }.gallery-title { display: block; font-size: 24px; font-weight: 700; }.gallery-copy { display: block; margin: 12px 0 24px; color: var(--muted); font-size: 13px; line-height: 1.8; }.gallery-row { display: flex; align-items: center; width: 100%; padding: 16px; margin-bottom: 10px; border: 1px solid var(--line); border-radius: 12px; text-align: left; font-size: 14px; }.gallery-number { width: 44px; color: var(--accent); font-size: 12px; }.gallery-arrow { margin-left: auto; color: var(--muted); }
</style>
