<script setup lang="ts">
import PlayerScene from '../../components/PlayerScene.vue';
import { usePlayerTransition } from '../../composables/usePlayerTransition';

import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import ScreenFrame from '../../components/ScreenFrame.vue';
import MotionButton from '../../components/MotionButton.vue';
import { meditationPreviews } from '../../services/meditation-preview';

const id = ref('');
const { state: playerTransition, busy: playerBusy, open: openPlayer, close: closePlayer } = usePlayerTransition(false);
const navigating = playerBusy;
onLoad(options => { id.value = typeof options?.id === 'string' ? options.id : ''; });
const track = computed(() => Object.values(meditationPreviews).find(item => item.id === id.value));
function play() {
  if (!track.value || navigating.value) return;
  openPlayer(track.value, { from: 'meditation', label: '冥想精选', selector: '.detail-artwork', fallbackSelector: '#meditation-player-origin' });
}
</script>
<template>
  <!-- #ifdef MP-WEIXIN -->
  <page-meta :page-style="playerTransition.nativeOpen ? 'overflow:hidden' : ''" />
  <page-container v-if="playerTransition.nativeOpen" :show="playerTransition.nativeOpen" :duration="0" :overlay="false" :z-index="1000"
    custom-style="height:0;background:transparent;" @beforeleave="closePlayer" />
  <!-- #endif -->

  <ScreenFrame :screen="0">
    <view class="meditation-detail">
      <template v-if="track">
        <view class="detail-artwork"><image :src="track.coverUrl" mode="aspectFill" /><view class="detail-artwork-shade" /></view>
        <text class="detail-access">{{ track.vip ? 'VIP · 示例内容' : '免费 · 示例内容' }}</text>
        <text class="detail-title">{{ track.title }}</text>
        <text class="detail-meta">{{ Math.round(track.totalSeconds / 60) }} 分钟 · 导师 {{ track.teacher }}</text>
        <view class="detail-introduction">
          <text class="detail-heading">练习介绍</text>
          <text class="detail-description">在一段安静的时间里，跟随声音放慢节奏，将注意力带回当下。选择舒适的姿势，以自己的节奏聆听和感受。</text>
        </view>
        <view id="meditation-player-origin"><MotionButton class="detail-start" aria-label="开始练习" feedback="icon" :busy="navigating" @click="play">开始练习</MotionButton></view>
        <text class="detail-note">当前为界面演示，暂未接入音频与会员权益。</text>
      </template>
      <template v-else>
        <text class="detail-title">暂时找不到这段冥想</text>
        <text class="detail-description">请返回分类列表重新选择。</text>
      </template>
    </view>
  </ScreenFrame>
  <PlayerScene v-if="playerTransition.nativeOpen || playerTransition.expansion" :transition-state="playerTransition" @close="closePlayer" />
</template>
<style scoped>
.meditation-detail { padding: var(--top) 24px 24px; color: var(--text); }
.detail-artwork { position: relative; width: 100%; height: 210px; overflow: hidden; border-radius: 24px; background: var(--surface); }
.detail-artwork image { width: 100%; height: 100%; }
.detail-artwork-shade { position: absolute; inset: 0; background: linear-gradient(transparent 50%,rgba(15,29,31,.35)); }
.detail-access { display: inline-block; margin-top: 24px; padding: 3px 10px; color: var(--accent); background: rgba(63,179,163,.12); border-radius: 12px; font-size: 11px; line-height: 18px; }
.detail-title { display: block; margin-top: 12px; font-size: 25px; font-weight: 700; line-height: 36px; }
.detail-meta { display: block; margin-top: 9px; color: var(--muted); font-size: 13px; line-height: 22px; }
.detail-introduction { margin: 28px 0; padding-top: 24px; border-top: 1px solid var(--line); }
.detail-heading { display: block; font-size: 17px; font-weight: 600; line-height: 26px; }
.detail-description { display: block; margin-top: 10px; color: var(--muted); font-size: 14px; line-height: 25px; }
.detail-start { display: flex; align-items: center; justify-content: center; width: 100%; min-height: 50px; border-radius: 25px; background-image: linear-gradient(100deg,#2C8C82,#3FB3A3); color: #fff; font-size: 15px; font-weight: 600; }
.detail-note { display: block; margin-top: 12px; color: var(--muted); font-size: 11px; line-height: 18px; text-align: center; }
@media (max-width: 350px) { .meditation-detail { padding-left: 20px; padding-right: 20px; }.detail-artwork { height: 180px; }.detail-title { font-size: 23px; } }
</style>
