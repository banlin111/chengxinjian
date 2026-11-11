<script setup lang="ts">
import PageShell from './PageShell.vue';
import PageHeader from './PageHeader.vue';
import { preview } from '../navigation';
defineProps<{ title: string; subtitle: string; entry: string }>();
</script>
<template><PageShell><PageHeader :title="title" :subtitle="subtitle" /><view class="section"><view class="card"><text class="placeholder-title">这一页正在准备</text><text class="placeholder-copy">导航已接通，页面将按甲方效果图逐步还原。</text><button class="entry pressable" hover-class="is-pressed" @click="preview(entry)"><text>{{ entry }}</text><text>›</text></button></view></view></PageShell></template>
<style scoped>.placeholder-title { display:block;font-size:18px;margin-bottom:10px; }.placeholder-copy { display:block;font-size:13px;line-height:1.8;color:var(--muted);margin-bottom:20px; }.entry { display:flex;justify-content:space-between;padding:16px 0;font-size:14px;border-top:1px solid var(--line);text-align:left; }</style>
