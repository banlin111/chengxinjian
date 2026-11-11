<script setup lang="ts">
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import ScreenFrame from '../../components/ScreenFrame.vue';
import AppIcon from '../../components/AppIcon.vue';
import MotionButton from '../../components/MotionButton.vue';
import { meditationMoods, meditationPreviews } from '../../services/meditation-preview';
import type { MeditationPreview } from '../../services/meditation-preview';

const navigating = ref(false);
const track = meditationPreviews.sleep;
function chooseMood(id: string) { navigate(`/pages/meditation-category/index?category=${encodeURIComponent(id)}`); }
function navigate(url: string) {
  if (navigating.value) return;
  navigating.value = true;
  uni.navigateTo({ url, fail: () => {
    navigating.value = false;
    uni.showToast({ title: '暂时无法打开，请重试', icon: 'none' });
  } });
}
function openDetail(value: MeditationPreview) {
  navigate(`/pages/meditation-detail/index?id=${encodeURIComponent(value.id)}`);
}
onShow(() => { navigating.value = false; });
</script>

<template>
  <ScreenFrame :screen="2" primary gradient>
    <view class="meditation-body">
      <text class="meditation-title">冥想</text>
      <MotionButton class="sleep-banner" feedback="card" aria-label="助眠专区" :busy="navigating" @click="chooseMood('sleep')">
        <image class="sleep-image" src="/static/prototype/fd88956af286.jpg" mode="aspectFill" />
        <view class="sleep-shade" />
        <view class="sleep-copy">
          <text class="sleep-title">助眠专区</text>
          <text class="sleep-description">12 组引导 · 白噪音 · 身体扫描</text>
        </view>
      </MotionButton>

      <view class="mood-section">
        <text class="meditation-section-title">按心境选</text>
        <view class="mood-grid" role="group" aria-label="冥想分类入口">
          <MotionButton v-for="mood in meditationMoods" :key="mood.id" class="mood-button" feedback="icon"
            :aria-label="mood.label"
            :busy="navigating" @click="chooseMood(mood.id)">
            <view class="mood-icon" :class="{ 'mood-all': mood.id === 'all' }"
              :style="mood.colors ? `background-image:linear-gradient(135deg,${mood.colors})` : ''">
              <AppIcon :asset="mood.icon" />
            </view>
            <text class="mood-label">{{ mood.label }}</text>
          </MotionButton>
        </view>
      </view>

      <view class="meditation-popular">
        <view class="popular-heading">
          <text class="meditation-section-title">今晚热门</text>
          <MotionButton class="meditation-more" feedback="link" aria-label="更多冥想" :busy="navigating" @click="chooseMood('all')">
            <text>更多</text><image src="/static/ui/chevron.svg" />
          </MotionButton>
        </view>
        <view class="track-transition">
          <MotionButton class="meditation-track" feedback="card" :aria-label="track.title" :busy="navigating" @click="openDetail(track)">
            <image class="track-cover" :src="track.coverUrl" mode="aspectFill" />
            <view class="track-copy">
              <text class="track-title">{{ track.title }}</text>
              <view class="track-meta">
                <text>{{ Math.round(track.totalSeconds / 60) }}分钟 · 导师 {{ track.teacher }}</text>
                <view class="track-listeners"><AppIcon asset="fas-headphones-a8c5bd" /><text>{{ track.listeners }}</text></view>
              </view>
            </view>
            <view class="track-access" :class="{ 'track-vip': track.vip }">
              <AppIcon v-if="track.vip" asset="fas-crown-ffffff" /><text>{{ track.vip ? 'VIP' : '免费' }}</text>
            </view>
            <view class="track-play"><AppIcon asset="fas-circle-play-3fb3a3" /></view>
          </MotionButton>
        </view>
      </view>
    </view>
  </ScreenFrame>
</template>

<style scoped>
.meditation-body { padding: var(--top) 20px 0; }
.meditation-title { display: block; padding: 0 4px; color: var(--text); font-size: 22px; font-weight: 700; line-height: 33px; letter-spacing: -.35px; }
.sleep-banner { position: relative; display: block; width: 100%; height: 150px; margin-top: 12px; overflow: hidden; border-radius: 16px; text-align: left; background: #16302F; }
.sleep-image { position: absolute; inset: 0; width: 100%; height: 100%; }
.sleep-shade { position: absolute; inset: 0; background: linear-gradient(90deg,rgba(15,29,31,.72),rgba(15,29,31,.06)); }
.sleep-copy { position: absolute; inset: 0; display: flex; flex-direction: column; justify-content: center; padding: 20px; color: #fff; }
.sleep-title { display: block; font-size: 17px; font-weight: 700; line-height: 26px; }
.sleep-description { display: block; margin-top: 4px; color: rgba(247,245,239,.84); font-size: 11px; line-height: 18px; }
.mood-section { margin-top: 20px; }
.meditation-section-title { display: block; color: var(--text); font-size: 17px; font-weight: 700; line-height: 26px; }
.mood-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 14px 12px; margin-top: 12px; }
.mood-button { display: flex; flex-direction: column; align-items: center; gap: 7px; width: 100%; overflow: visible; color: var(--text); }
.mood-icon { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 56px; height: 56px; border-radius: 16px; font-size: 20px; box-shadow: 0 0 0 2px transparent; transition: box-shadow 220ms ease, transform 220ms cubic-bezier(.22,1,.36,1); }
.mood-all { background: rgba(255,255,255,.1); border: 1px solid rgba(255,255,255,.15); font-size: 18px; }
.mood-label { display: block; font-size: 11px; line-height: 18px; white-space: nowrap; }
.meditation-popular { margin-top: 16px; }
.popular-heading { display: flex; align-items: center; justify-content: space-between; min-height: 44px; margin-bottom: 4px; }
.meditation-more { display: flex; align-items: center; justify-content: flex-end; gap: 5px; min-width: 60px; min-height: 44px; padding-left: 12px; color: var(--muted); font-size: 11px; }
.meditation-more image { width: 9px; height: 9px; }
.meditation-track { display: flex; align-items: center; gap: 10px; width: 100%; min-height: 76px; padding: 10px; text-align: left; border: 1px solid rgba(255,255,255,.13); border-radius: 14px; background-image: linear-gradient(rgba(255,255,255,.055),rgba(255,255,255,.055)); }
.track-cover { flex-shrink: 0; width: 48px; height: 48px; border-radius: 8px; }
.track-copy { min-width: 0; flex: 1; }
.track-title { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; color: var(--text); font-size: 13px; font-weight: 600; line-height: 19px; }
.track-meta { display: flex; flex-wrap: wrap; gap: 0 5px; margin-top: 3px; color: var(--muted); font-size: 10px; line-height: 16px; }
.track-listeners { display: flex; align-items: center; gap: 3px; }
.track-access { display: flex; align-items: center; gap: 2px; flex-shrink: 0; padding: 2px 7px; border-radius: 20px; color: var(--muted); background: rgba(255,255,255,.1); font-size: 9px; line-height: 15px; }
.track-vip { background: linear-gradient(90deg,#3FB3A3,#6FCABB); color: #fff; }
.track-play { display: flex; align-items: center; flex-shrink: 0; font-size: 23px; }
@media (min-width: 390px) and (max-width: 599px) {
  .meditation-title { font-size: 24px; line-height: 36px; }
  .sleep-banner { height: 162px; }
  .mood-icon { width: 60px; height: 60px; border-radius: 18px; }
  .mood-label { font-size: 12px; }
}
@media (max-width: 350px) {
  .meditation-body { padding-left: 16px; padding-right: 16px; }
  .sleep-copy { padding: 16px; }
  .mood-grid { column-gap: 8px; }
  .mood-icon { width: 52px; height: 52px; border-radius: 15px; }
  .meditation-track { gap: 7px; padding: 9px; }
  .track-title { font-size: 12px; line-height: 18px; }
  .track-access { padding: 2px 5px; }
  .track-play { font-size: 21px; }
}
@media (prefers-reduced-motion: reduce) {
  .track-transition { animation: none !important; }
  .mood-icon { transition: none; }
}
</style>

<style>
.pdf-screen[data-screen="2"] { min-height: 0; padding-bottom: 0; }
.motion-reduced .meditation-body .track-transition { animation: none !important; }
.motion-reduced .meditation-body .mood-icon { transition: none !important; transform: none; }
</style>
