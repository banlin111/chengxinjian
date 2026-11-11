<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import ScreenFrame from '../../components/ScreenFrame.vue';
import MotionButton from '../../components/MotionButton.vue';
import { meditationMoods, meditationPreviews } from '../../services/meditation-preview';
import { usePreviewNavigation } from '../../composables/usePreviewNavigation';

const category = ref('all');
const { navigating, navigate } = usePreviewNavigation();
onLoad(options => { category.value = typeof options?.category === 'string' ? options.category : 'all'; });
const mood = computed(() => meditationMoods.find(item => item.id === category.value));
const title = computed(() => category.value === 'all' ? '全部冥想' : mood.value?.label || '分类不存在');
const tracks = computed(() => category.value === 'all' ? Object.values(meditationPreviews)
  : mood.value && meditationPreviews[category.value] ? [meditationPreviews[category.value]] : []);
</script>
<template>
  <ScreenFrame :screen="0">
    <view class="meditation-category">
      <text class="category-eyebrow">MEDITATION</text>
      <text class="category-title">{{ title }}</text>
      <text class="category-description">{{ tracks.length ? '选择一段引导，留一点时间给自己。' : '暂时找不到这个分类，请返回重新选择。' }}</text>
      <text v-if="tracks.length" class="category-note">示例内容 · 每类暂展示一条</text>
      <view class="category-list">
        <MotionButton v-for="item in tracks" :key="item.id" class="category-track" feedback="card"
          :aria-label="item.title" :disabled="navigating" @click="navigate(`/pages/meditation-detail/index?id=${encodeURIComponent(item.id)}`)">
          <image class="category-cover" :src="item.coverUrl" mode="aspectFill" />
          <view class="category-copy">
            <text class="category-track-title">{{ item.title }}</text>
            <text class="category-meta">{{ Math.round(item.totalSeconds / 60) }} 分钟 · 导师 {{ item.teacher }}</text>
            <text class="category-access" :class="{ 'access-vip': item.vip }">{{ item.vip ? 'VIP' : '免费' }}</text>
          </view>
          <image class="category-chevron" src="/static/ui/chevron.svg" />
        </MotionButton>
      </view>
    </view>
  </ScreenFrame>
</template>
<style scoped>
.meditation-category { padding: var(--top) 20px 24px; color: var(--text); }
.category-eyebrow { display: block; color: var(--accent); font-size: 10px; letter-spacing: 2px; }
.category-title { display: block; margin-top: 9px; font-size: 26px; font-weight: 700; line-height: 38px; }
.category-description { display: block; margin-top: 10px; color: var(--muted); font-size: 13px; line-height: 22px; }
.category-note { display: block; margin-top: 7px; color: var(--muted); font-size: 11px; line-height: 18px; }
.category-list { display: flex; flex-direction: column; gap: 12px; margin-top: 28px; }
.category-track { display: flex; align-items: center; gap: 14px; width: 100%; padding: 12px; text-align: left; border: 1px solid var(--line); border-radius: 18px; background-image: linear-gradient(var(--surface),var(--surface)); }
.category-cover { width: 72px; height: 82px; flex-shrink: 0; border-radius: 12px; }
.category-copy { min-width: 0; flex: 1; }
.category-track-title { display: block; color: var(--text); font-size: 14px; font-weight: 600; line-height: 22px; }
.category-meta { display: block; margin-top: 5px; color: var(--muted); font-size: 11px; line-height: 18px; }
.category-access { display: inline-block; margin-top: 8px; padding: 1px 8px; border-radius: 12px; color: var(--muted); background: rgba(128,160,150,.12); font-size: 10px; line-height: 17px; }
.access-vip { color: #fff; background: #2C8C82; }
.category-chevron { width: 11px; height: 11px; flex-shrink: 0; }
@media (max-width: 350px) { .category-track { gap: 10px; }.category-cover { width: 60px; height: 76px; } }
</style>
