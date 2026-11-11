<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { onHide, onShow } from '@dcloudio/uni-app';
import ScreenFrame from './ScreenFrame.vue';
import MotionButton from './MotionButton.vue';
import AppIcon from './AppIcon.vue';
import { backScreen } from '../config/screens';
import { useScreenPreview } from '../composables/useScreenPreview';
import { usePreviewNavigation } from '../composables/usePreviewNavigation';
import { useMixerPreviewStore } from '../stores/mixer-preview';
import { usePreviewPreferences } from '../stores/preview-preferences';
import type { Course } from '../../../../contracts/models';
import type { PlayerTransitionState } from '../composables/usePlayerTransition';
const props = defineProps<{ course?: Pick<Course, 'id' | 'title' | 'coverUrl' | 'totalSeconds'> | null; fromHome?: boolean; sourceLabel?: string; transitionState?: PlayerTransitionState }>();
const emit = defineEmits<{ (event: 'close'): void }>();
const { notice } = useScreenPreview(5);
const { navigating, navigate } = usePreviewNavigation();
const mix = useMixerPreviewStore();
const playing = ref(false);
const liked = ref(true);
const shuffled = ref(false);
const preferences = usePreviewPreferences();
const speed = ref(preferences.speed);
const timer = ref(30);
const progress = ref(38);
const visible = ref(true);
const choosing = ref(false);
const animating = computed(() => Boolean(props.transitionState?.expansion));
const transitionPreview = computed(() => Boolean(props.transitionState && !props.transitionState.ready));
const blocked = computed(() => navigating.value || choosing.value || transitionPreview.value);
const selectedCourse = computed(() => props.transitionState?.course ?? props.course);
const fromSelection = computed(() => Boolean(props.transitionState) || props.fromHome);
const selectionLabel = computed(() => props.transitionState?.sourceLabel || props.sourceLabel || '首页精选');
const duration = computed(() => Math.max(0, selectedCourse.value?.totalSeconds ?? 1800));
const elapsed = computed(() => Math.floor(progress.value * duration.value / 100));
const artwork = computed(() => selectedCourse.value?.coverUrl || '/static/prototype/4ddf17d53d3d.jpg');
const timerLabel = computed(() => timer.value ? `定时 ${timer.value} 分` : '定时关闭');
watch(() => [selectedCourse.value?.id, fromSelection.value], () => {
  progress.value = fromSelection.value ? 0 : 38; playing.value = false; liked.value = !fromSelection.value;
}, { immediate: true });
onShow(() => { visible.value = true; });
onHide(() => { visible.value = false; playing.value = false; });
function timeLabel(seconds: number) { return `${Math.floor(seconds / 60).toString().padStart(2, '0')}:${Math.floor(seconds % 60).toString().padStart(2, '0')}`; }
function seek(event: { detail: { value: number } }) {
  if (!duration.value || blocked.value) return;
  progress.value = Math.min(100, Math.max(0, event.detail.value));
}
function selectOption(kind: 'speed' | 'timer') {
  if (blocked.value) return;
  choosing.value = true;
  const values = kind === 'speed' ? [.75,1,1.25,1.5,2] : [0,15,30,45,60];
  const current = kind === 'speed' ? speed.value : timer.value;
  uni.showActionSheet({
    itemList: values.map(value => `${kind === 'speed' ? `${value} 倍速` : value ? `${value} 分钟后关闭` : '关闭定时'}${value === current ? ' · 当前' : ''}`),
    success: result => { const value = values[result.tapIndex]; if (value === undefined) return; if (kind === 'speed') speed.value = value; else timer.value = value; },
    complete: () => { choosing.value = false; },
  });
}
function stepPreview() { notice('播放预览', '当前只提供一条试听占位，其他音频将在接入后展示。'); }
function back() { playing.value = false; if (props.transitionState?.nativeOpen) emit('close'); else backScreen(); }
</script>

<template>
  <view v-if="!transitionState || animating || transitionState.ready"
    :class="{ 'card-expansion': animating, 'is-expanded': animating && transitionState?.expanded, 'is-interactive': animating && transitionState?.ready, 'native-player-fallback': transitionState?.ready && !animating }"
    :aria-hidden="animating && transitionPreview ? true : undefined">
    <view v-if="animating" class="expansion-scrim" />
    <view :class="animating ? 'expansion-frame' : undefined" :style="animating ? transitionState?.frameStyle : undefined">
      <view :class="animating ? 'expansion-destination' : undefined" :style="animating ? transitionState?.backgroundStyle : undefined">
  <ScreenFrame :screen="5" :on-back="back">
    <image class="player-background" mode="aspectFill" :src="selectedCourse?.coverUrl || '/static/prototype/6907df7f1eed.jpg'" aria-hidden="true" />
    <view class="player-shade" />
    <view class="player-content screen-content">
      <!-- Keep 240/168px geometry aligned with useCardExpansion's destination. -->
      <view class="player-disc">
        <view class="player-halo" :class="{ 'is-breathing': playing && visible && !transitionPreview }" />
        <view class="player-ring outer-ring" /><view class="player-ring inner-ring" />
        <image class="player-artwork" :style="transitionPreview ? 'visibility:hidden' : ''" mode="aspectFill" :src="artwork" />
      </view>
      <text class="player-title">{{ selectedCourse?.title || '星空身体扫描' }}</text>
      <text class="player-subtitle">{{ fromSelection ? selectionLabel : '林涧 · 系列《深度放松》 · 第 5 集' }}</text>
      <view class="player-progress">
        <slider class="player-slider" aria-label="播放进度" :disabled="!duration || blocked" :block-size="14" :value="progress" @changing="seek" @change="seek" active-color="#3FB3A3" background-color="rgba(255,255,255,.2)" block-color="#ffffff" />
        <view class="player-times"><text>{{ duration ? timeLabel(elapsed) : '--:--' }}</text><text>{{ duration ? `-${timeLabel(duration - elapsed)}` : '时长待配置' }}</text></view>
      </view>
      <view class="player-controls">
        <MotionButton class="player-control shuffle-control" :class="{ 'control-active': shuffled }" feedback="icon" aria-label="随机播放" :aria-pressed="shuffled ? 'true' : 'false'" data-action="shuffle" :busy="blocked" @click="shuffled = !shuffled"><AppIcon asset="fas-shuffle-f2eee4" /><view class="mode-dot" :class="{ 'mode-enabled': shuffled }" /></MotionButton>
        <MotionButton class="player-control step-control" feedback="icon" aria-label="上一首" data-action="backward-step" :busy="blocked" @click="stepPreview"><AppIcon asset="fas-backward-step-ffffff" /></MotionButton>
        <MotionButton class="player-toggle" feedback="icon" aria-label="播放状态切换" :aria-pressed="playing ? 'true' : 'false'" data-action="播放状态切换" :busy="blocked" @click="playing = !playing"><AppIcon :asset="playing ? 'fas-pause-ffffff' : 'fas-play-ffffff'" /></MotionButton>
        <MotionButton class="player-control step-control" feedback="icon" aria-label="下一首" data-action="forward-step" :busy="blocked" @click="stepPreview"><AppIcon asset="fas-forward-step-ffffff" /></MotionButton>
        <MotionButton class="player-control speed-control" feedback="icon" aria-label="切换倍速" data-action="切换倍速" :busy="blocked" @click="selectOption('speed')"><text>{{ speed }}x</text></MotionButton>
      </view>
      <view class="player-tools">
        <MotionButton class="player-tool" feedback="icon" aria-label="调整定时" data-action="调整定时" :busy="blocked" @click="selectOption('timer')"><AppIcon asset="fas-stopwatch-a8c5bd" /><text>{{ timerLabel }}</text></MotionButton>
        <MotionButton class="player-tool" feedback="icon" :aria-label="mix.rainLabel" data-action="打开助眠混音" :busy="blocked" @click="navigate('/pages/mixer/index')"><AppIcon asset="fas-music-a8c5bd" /><text>{{ mix.rainLabel }}</text></MotionButton>
        <MotionButton class="player-tool" feedback="icon" aria-label="后台播放" data-action="后台播放" :busy="blocked" @click="notice('后台播放', '后台音频能力尚未接入，当前仅展示入口。')"><AppIcon asset="fas-mobile-screen-a8c5bd" /><text>后台播放</text></MotionButton>
        <MotionButton class="player-tool player-like" feedback="icon" :aria-label="liked ? '取消喜欢' : '喜欢'" :aria-pressed="liked ? 'true' : 'false'" data-action="已喜欢" :busy="blocked" @click="liked = !liked"><AppIcon :asset="liked ? 'fas-heart-3fb3a3' : 'far-heart-a8c5bd'" /><text>{{ liked ? '已喜欢' : '喜欢' }}</text></MotionButton>
      </view>
      <text class="player-demo-note">界面演示 · 暂无音频{{ shuffled ? ' · 随机模式' : '' }}</text>
    </view>
  </ScreenFrame>
      </view>
      <view v-if="transitionState?.expansion && !transitionState.ready" class="expansion-cover" :style="transitionState.coverStyle">
        <image class="expansion-image" :src="transitionState.expansion.image" mode="aspectFill" />
      </view>
    </view>
  </view>
</template>

<style scoped>
.card-expansion { position: fixed; inset: 0; z-index: 1005; touch-action: none; }
.expansion-scrim { position: absolute; inset: 0; background: #0F1D1F; opacity: 0; transition: opacity 340ms cubic-bezier(.22,1,.36,1); }
.is-expanded .expansion-scrim { opacity: 1; }
.expansion-frame { position: fixed; overflow: hidden; background: #16302F; transition: left 340ms cubic-bezier(.22,1,.36,1), top 340ms cubic-bezier(.22,1,.36,1), width 340ms cubic-bezier(.22,1,.36,1), height 340ms cubic-bezier(.22,1,.36,1), border-radius 340ms cubic-bezier(.22,1,.36,1); }
.expansion-cover { position: absolute; overflow: hidden; opacity: 1; transition: left 340ms cubic-bezier(.22,1,.36,1), top 340ms cubic-bezier(.22,1,.36,1), width 340ms cubic-bezier(.22,1,.36,1), height 340ms cubic-bezier(.22,1,.36,1), border-radius 340ms cubic-bezier(.22,1,.36,1), opacity 60ms ease-out 280ms; }
.expansion-image { width: 100%; height: 100%; }
.expansion-destination { position: absolute; opacity: 0; pointer-events: none; transition: left 340ms cubic-bezier(.22,1,.36,1), top 340ms cubic-bezier(.22,1,.36,1), opacity 200ms ease-out 40ms; }
.is-expanded .expansion-destination { opacity: 1; }
.is-interactive { touch-action: auto; }
.is-interactive .expansion-frame { overflow-y: auto; }
.is-interactive .expansion-destination { pointer-events: auto; }
.native-player-fallback { position: fixed; inset: 0; z-index: 1005; overflow-y: auto; background: #0F1D1F; }
.player-background { position: absolute; inset: 0; width: 100%; height: 100%; }
.player-shade { position: absolute; inset: 0; background: linear-gradient(180deg,rgba(15,29,31,.62),rgba(15,29,31,.5) 45%,#0F1D1F 100%); }
.player-content { display: flex; flex-direction: column; align-items: center; padding: calc(var(--top) + 36px) 20px max(30px,env(safe-area-inset-bottom)); color: #fff; }
.player-disc { position: relative; display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 240px; height: 240px; }
.player-halo { position: absolute; inset: -12px; border-radius: 50%; background: radial-gradient(circle,rgba(63,179,163,.32) 15%,rgba(63,179,163,.14) 48%,transparent 71%); opacity: .65; }
.player-halo.is-breathing { animation: player-breathe 5s ease-in-out infinite; }
.player-ring { position: absolute; border-radius: 50%; border: 1px solid rgba(255,255,255,.2); }.outer-ring { inset: 12px; }.inner-ring { inset: 24px; border-color: rgba(255,255,255,.1); }
.player-artwork { position: relative; width: 168px; height: 168px; border-radius: 50%; box-shadow: 0 12px 32px rgba(0,0,0,.25); }
.player-title { display: block; max-width: 100%; margin-top: 28px; font-size: 22px; line-height: 31px; font-weight: 700; text-align: center; }
.player-subtitle { display: block; margin-top: 7px; color: #BCD5CF; font-size: 12px; line-height: 20px; text-align: center; }
.player-progress { width: 100%; max-width: 335px; margin-top: 22px; }
.player-slider { height: 32px; margin: 0; padding: 0 7px; }
.player-times { display: flex; justify-content: space-between; color: #BCD5CF; font-size: 11px; line-height: 18px; font-variant-numeric: tabular-nums; }
.player-controls { display: flex; align-items: center; justify-content: space-between; width: 100%; max-width: 335px; gap: 8px; margin-top: 20px; }
.player-control { display: flex; align-items: center; justify-content: center; position: relative; flex-shrink: 0; width: 44px; height: 48px; color: #fff; font-size: 20px; }
.step-control { font-size: 25px; }
.player-toggle { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 72px; height: 72px; border-radius: 50%; background-image: linear-gradient(135deg,#3FB3A3,#6FCABB); font-size: 26px; box-shadow: 0 8px 28px rgba(63,179,163,.25); }
.speed-control text { display: flex; align-items: center; justify-content: center; min-width: 40px; min-height: 26px; padding: 2px 5px; border-radius: 7px; background: rgba(255,255,255,.12); font-size: 12px; font-weight: 600; }
.mode-dot { position: absolute; width: 4px; height: 4px; bottom: 3px; border-radius: 50%; background: #6FCABB; opacity: 0; }.mode-enabled { opacity: 1; }
.player-tools { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 8px; width: 100%; max-width: 335px; margin-top: 26px; }
.player-tool { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; min-width: 0; min-height: 54px; color: #BCD5CF; font-size: 18px; }
.player-tool text { font-size: 11px; line-height: 18px; white-space: nowrap; }
.player-demo-note { margin-top: 20px; color: #A8C5BD; font-size: 10px; line-height: 18px; text-align: center; }
@keyframes player-breathe { 0%,100% { opacity: .5; transform: scale(.97); } 50% { opacity: .85; transform: scale(1.03); } }
@media (max-width:350px) { .player-content { padding-left: 16px; padding-right: 16px; }.player-controls { gap: 4px; }.player-title { font-size: 21px; }.player-tool text { font-size: 10px; } }
@media (prefers-reduced-motion: reduce) { .player-halo.is-breathing { animation: none; } }
</style>
<style>
.pdf-screen[data-screen="5"] { min-height: calc(100vh - 28px); padding-bottom: 0; background: #0F1D1F; }
</style>
