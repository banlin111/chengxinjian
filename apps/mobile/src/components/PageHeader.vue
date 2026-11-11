<script setup lang="ts">defineProps<{ title: string; subtitle?: string }>();</script>
<template><view class="page-header"><text class="page-title">{{ title }}</text><text v-if="subtitle" class="page-subtitle">{{ subtitle }}</text></view></template>
