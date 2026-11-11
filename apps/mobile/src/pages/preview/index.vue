<script setup lang="ts">
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import PageShell from '../../components/PageShell.vue';
import PageHeader from '../../components/PageHeader.vue';
import { tab } from '../../navigation';
const title = ref('页面预留'); const demo = ref(false);
onLoad((query) => { title.value = query?.title ? decodeURIComponent(query.title) : title.value; demo.value = query?.demo === '1'; });
function back() { if (getCurrentPages().length > 1) uni.navigateBack(); else tab('home'); }
</script>
<template><PageShell><PageHeader :title="title" :subtitle="demo ? '演示功能 · 暂未开放' : '页面还原进行中'" /><view class="state-card"><text class="state-title">{{ demo ? '这里将展示演示页面' : '入口已经准备好了' }}</text><text class="state-description">{{ demo ? '后续按原型还原布局和交互，使用独立示例数据，不发生真实交易。' : '当前先搭建工程与预览链路；这一页将在后续批次实现，尚未接入业务功能。' }}</text><button role="button" class="primary-button pressable" hover-class="is-pressed" @click="back">返回上一页</button></view></PageShell></template>
