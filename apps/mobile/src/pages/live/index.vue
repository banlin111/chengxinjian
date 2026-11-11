<script setup lang="ts">
import PlayerScene from '../../components/PlayerScene.vue';
import { usePlayerTransition } from '../../composables/usePlayerTransition';
import { computed } from 'vue';
import { replayPreview } from '../../services/replay-preview';

import MotionButton from '../../components/MotionButton.vue';
import { usePreviewPreferences } from '../../stores/preview-preferences';
import ScreenFrame from '../../components/ScreenFrame.vue';
import AppIcon from '../../components/AppIcon.vue';
import { useScreenPreview } from '../../composables/useScreenPreview';
const { busy: previewBusy, notice, action, go } = useScreenPreview(11);
const { state: playerTransition, busy: playerBusy, open: openPlayer, close: closePlayer } = usePlayerTransition(false);
const busy = computed(() => previewBusy.value || playerBusy.value);
function playReplay() {
  if (!busy.value) openPlayer(replayPreview, { from: 'replay', label: '回放演示', selector: '#replay-cover', fallbackSelector: '#replay-player-origin' });
}
const { booked } = usePreviewPreferences();
</script>

<template>
  <!-- #ifdef MP-WEIXIN -->
  <page-meta :page-style="playerTransition.nativeOpen ? 'overflow:hidden' : ''" />
  <page-container v-if="playerTransition.nativeOpen" :show="playerTransition.nativeOpen" :duration="0" :overlay="false" :z-index="1000"
    custom-style="height:0;background:transparent;" @beforeleave="closePlayer" />
  <!-- #endif -->

<ScreenFrame :screen="11" gradient>

<view class="u-z-20 screen-content relative" style="padding-top:calc(var(--top) + 0px)">
<view class="u-px-6 u-text-_20px_ u-font-bold">共修直播</view>

<view class="preserve-dark u-mx-5 u-mt-3_5 relative u-rounded-2xl u-overflow-hidden">
<image class="u-w-full u-h-_200px_ u-object-cover" mode="aspectFill" src="/static/prototype/33ac846e8cc1.jpg"/>
<view class="u-absolute u-inset-0 u-bg-gradient-to-t u-from-night_85 u-via-transparent u-to-night_20"></view>
<view class="u-absolute u-top-3 u-left-3 u-px-2_5 u-py-1 u-rounded-md u-bg-__FF4D6A_ u-text-_10px_ u-font-bold"><AppIcon asset="fas-tower-broadcast-ffffff" class="u-mr-1"></AppIcon>直播演示</view>
<view class="u-absolute u-top-3 u-right-3 u-px-2_5 u-py-1 u-rounded-full u-bg-black_45 u-text-_10px_"><AppIcon asset="fas-eye-ffffff" class="u-mr-1"></AppIcon>2,341 人</view>
<view class="u-absolute u-bottom-3 u-inset-x-3 u-flex u-items-end u-justify-between">
<view>
<view class="u-text-_15px_ u-font-bold">月圆之夜 · 全球同步颂钵共修</view>
<view class="u-text-_10px_ u-text-sage u-mt-1">导师 明一 · 已进行 42 分钟</view>
</view>
<MotionButton @click="go(17)" aria-label="进入" class="legacy-action action-label u-px-4 u-py-2 u-rounded-full u-bg-gradient-to-r u-from-jade u-to-__6FCABB_ u-text-_12px_ u-font-bold u-shadow-lg u-shadow-jade_40 ui-action" data-action="进入" role="button" feedback="icon" :busy="busy">进入</MotionButton>
</view>
</view>

<view class="u-px-5 u-mt-5">
<view class="u-text-_16px_ u-font-bold u-mb-2_5">直播预告</view>
<view class="u-space-y-3">

<view class="u-flex u-gap-3 u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3">
<view class="relative u-shrink-0">
<image class="u-w-_88px_ u-h-_66px_ u-rounded-xl u-object-cover" mode="aspectFill" src="/static/prototype/40c4403f07ce.jpg"/>
<view class="u-absolute u-bottom-1 u-left-1 u-px-1_5 u-py-0_5 u-rounded u-bg-black_60 u-text-_8px_">周六 09:00</view>
</view>
<view class="u-flex-1 u-min-w-0">
<view class="u-text-_13px_ u-font-semibold u-truncate">晨间正念 · 七日共修开营</view>
<view class="u-text-_10px_ u-text-sage u-mt-1">导师 一苇 · 864 人已预约</view>
<MotionButton @click="booked[1] = !booked[1]" :aria-label="booked[1] ? '取消预约' : '提醒我'" :aria-pressed="booked[1] ? 'true' : 'false'" class="legacy-action action-label live-reminder u-mt-1_5 u-px-3 u-py-1 u-rounded-full u-bg-jade u-text-_10px_ u-font-semibold ui-action" data-action="提醒我" role="button" feedback="icon" :busy="busy">{{ booked[1] ? '已预约 · 演示' : '提醒我' }}</MotionButton>
</view>
</view>
</view>
</view>

<view class="u-px-5 u-mt-5">
<view class="u-flex u-items-end u-justify-between u-mb-2_5">
<view class="u-text-_16px_ u-font-bold">精彩回放</view>
<MotionButton @click="playReplay" aria-label="更多" class="legacy-action action-link u-text-_11px_ u-text-sage ui-action" data-action="更多" role="button" feedback="icon" :busy="busy">更多 <AppIcon asset="fas-chevron-right-a8c5bd" class="u-text-_9px_"></AppIcon></MotionButton>
</view>
<view id="replay-player-origin"><MotionButton @click="playReplay" aria-label="秋分昼夜等长冥想夜" class="legacy-action u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3 u-flex u-items-center u-gap-3 ui-action" data-action="秋分昼夜等长冥想夜" role="button" feedback="icon" :busy="busy">
<image id="replay-cover" class="u-w-_72px_ u-h-_52px_ u-rounded-lg u-object-cover" mode="aspectFill" src="/static/prototype/ef1a45ed0a4c.jpg"/>
<view class="u-flex-1 u-min-w-0">
<view class="u-text-_12px_ u-font-semibold u-truncate">秋分昼夜等长冥想夜</view>
<view class="u-text-_10px_ u-text-sage u-mt-1">回放 58 分钟 · 观看 3.4 万</view>
</view>
<AppIcon asset="fas-circle-play-3fb3a3" class="u-text-_22px_ u-text-jade"></AppIcon>
</MotionButton></view>
</view>
</view>

</ScreenFrame>
  <PlayerScene v-if="playerTransition.nativeOpen || playerTransition.expansion" :transition-state="playerTransition" @close="closePlayer" />
</template>
