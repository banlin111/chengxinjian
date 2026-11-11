<script setup lang="ts">
defineProps<{ asset: string }>();
</script>
<template><image class="app-icon" :src="`/static/icons/${asset}.svg`" mode="aspectFit" aria-hidden="true" /></template>
<style scoped>
.app-icon { display: inline-block; width: 1em; height: 1em; flex-shrink: 0; vertical-align: -.125em; }
</style>
