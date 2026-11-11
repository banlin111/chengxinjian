<script setup lang="ts">
import { ref } from 'vue';
import { onHide, onLoad, onShow } from '@dcloudio/uni-app';
import type { Course, Home } from '../../../../../contracts/models';
import PageShell from '../../components/PageShell.vue';
import MotionButton from '../../components/MotionButton.vue';
import PlayerScene from '../../components/PlayerScene.vue';
import { config } from '../../config';
import { getHome } from '../../services/home';
import { useCardExpansion } from '../../composables/useCardExpansion';
import { usePlayerPreviewStore } from '../../stores/player-preview';
const { expansion, expanded, frameStyle, coverStyle, backgroundStyle, start: expandCard, reset: resetExpansion } = useCardExpansion();
const playerPreview = usePlayerPreviewStore();
const home = ref<Home | null>(null);
const state = ref<'loading' | 'ready' | 'error'>('loading');
const message = ref('');
const navigating = ref(false);
const nativePlayerOpen = ref(false);
const nativePlayerReady = ref(false);
function closeNativePlayer() {
  if (!nativePlayerOpen.value) return;
  nativePlayerOpen.value = false;
  nativePlayerReady.value = false;
  resetExpansion();
  navigating.value = false;
  uni.showTabBar({ animation: false });
}
const scrollLeft = ref(0);
let savedScrollLeft = 0;
let loadVersion = 0;
function rememberScroll(event: { detail: { scrollLeft: number } }) { savedScrollLeft = event.detail.scrollLeft; }
function navigate(destination: 'player' | 'courses' | 'practice', courseId?: string) {
  const options = {
    url: `/pages/${destination}/index${courseId ? `?courseId=${encodeURIComponent(courseId)}&from=home` : ''}`,
    fail: () => {
      resetExpansion();
      navigating.value = false;
      uni.showToast({ title: '暂时无法打开，请重试', icon: 'none' });
    },
  };
  if (destination === 'player') uni.navigateTo(options);
  else uni.switchTab(options);
}
function open(destination: 'courses' | 'practice') {
  if (navigating.value) return;
  navigating.value = true;
  navigate(destination);
}
function openCourse(course: Course, selector: string) {
  if (navigating.value) return;
  navigating.value = true;
  playerPreview.select(course);
  // The WeChat WebView router adds its own transition. Keep the very same
  // PlayerScene alive after expansion instead of navigating a second time.
  // #ifdef MP-WEIXIN
  nativePlayerOpen.value = true;
  nativePlayerReady.value = false;
  uni.hideTabBar({ animation: false, complete: () => {
    if (!nativePlayerOpen.value) return;
    expandCard(selector, course.coverUrl, () => { nativePlayerReady.value = true; });
  } });
  return;
  // #endif
  // #ifndef MP-WEIXIN
  expandCard(selector, course.coverUrl, () => navigate('player', course.id));
  // #endif
}
onShow(() => {
  if (nativePlayerOpen.value) { uni.hideTabBar({ animation: false }); return; }
  resetExpansion(); navigating.value = false; scrollLeft.value = savedScrollLeft;
});
onHide(() => { if (!nativePlayerOpen.value) navigating.value = false; });
async function reload() {
  const version = ++loadVersion;
  state.value = 'loading';
  try {
    const data = await getHome();
    if (version !== loadVersion) return;
    home.value = data; state.value = 'ready';
  } catch (error) {
    if (version !== loadVersion) return;
    state.value = 'error'; message.value = error instanceof Error ? error.message : '暂时无法加载';
  }
}
function audience(count: number) { return count >= 10000 ? `${(count / 10000).toFixed(1)}万` : `${count}`; }
onLoad(reload);
</script>
<template>
  <!-- #ifdef MP-WEIXIN -->
  <page-meta :page-style="nativePlayerOpen ? 'overflow:hidden;background:#0F1D1F' : 'background:#0F1D1F'" />
  <!-- Native back gestures dismiss the in-page player without leaving home. -->
  <page-container v-if="nativePlayerOpen" :show="nativePlayerOpen" :duration="0" :overlay="false" :z-index="1000"
    custom-style="height:0;background:transparent;" @beforeleave="closeNativePlayer" />
  <!-- #endif -->
  <PageShell home motion>
    <!-- Browser-only system chrome mirrors the PDF. WeChat supplies its own. -->
    <!-- #ifdef H5 -->
    <view class="prototype-status" aria-hidden="true">
      <text>9:41</text>
      <view class="status-icons">
        <image src="/static/ui/signal.svg" />
        <image src="/static/ui/wifi.svg" />
        <image src="/static/ui/battery.svg" />
      </view>
    </view>
    <view class="prototype-capsule" aria-hidden="true">
      <view class="capsule-dots"><view /><view /><view /></view>
      <view class="capsule-divider" /><view class="capsule-ring" />
    </view>
    <!-- #endif -->

    <view class="home-header">
      <text class="greeting">{{ home?.greeting || '你好，涧友' }}</text>
      <text class="greeting-sub">{{ config.useMock ? '今晚适合一段 21 分钟的慈心练习' : '留一点时间，安放此刻的自己' }}</text>
    </view>

    <view v-if="state === 'loading'" class="section loading-content" aria-label="正在加载" aria-busy="true">
      <view class="skeleton skeleton-eyebrow" />
      <view class="skeleton skeleton-cover" />
      <view class="skeleton skeleton-title" />
      <view class="skeleton skeleton-description" />
      <view class="skeleton skeleton-heading" />
      <view class="skeleton-weekly"><view v-for="n in 2" :key="n" class="skeleton skeleton-course" /></view>
      <view class="skeleton skeleton-checkin" />
    </view>
    <view v-else-if="state === 'error'" class="state-card">
      <text class="state-title">暂时无法加载</text>
      <text class="state-description">{{ message }}</text>
      <MotionButton class="primary-button" @click="reload">重新加载</MotionButton>
    </view>
    <view v-else-if="home" class="home-content">
      <view v-if="home.recommendedCourses.length" class="section hero-section">
        <text class="eyebrow">本周上新 · NEW</text>
        <MotionButton class="hero-button" feedback="card" :disabled="navigating"
          :aria-label="home.recommendedCourses[0].title"
          @click="openCourse(home.recommendedCourses[0], '#home-hero-cover')">
          <view id="home-hero-cover" class="hero-image-wrap">
            <image class="hero-image" :src="home.recommendedCourses[0].coverUrl" mode="aspectFill" />
            <view v-if="home.recommendedCourses[0].accessPolicy !== 'free'" class="vip">
              <image class="crown-icon" src="/static/ui/crown.svg" /><text>VIP</text>
            </view>
            <view class="hero-meta">
              <text>{{ Math.round(home.recommendedCourses[0].totalSeconds / 60) }}分钟 ·</text>
              <image class="person-icon" src="/static/ui/person.svg" />
              <text>{{ audience(home.recommendedCourses[0].learnerCount) }}</text>
            </view>
          </view>
          <text class="hero-title">{{ home.recommendedCourses[0].title }}</text>
          <text class="hero-description">{{ home.recommendedCourses[0].summary }}</text>
        </MotionButton>
      </view>

      <view v-if="home.newCourses.length" class="weekly-section">
        <view class="section weekly-heading">
          <text class="section-title">每周上新</text>
          <MotionButton class="more-link" feedback="link" :disabled="navigating" @click="open('courses')">
            <text>查看更多</text><image src="/static/ui/chevron.svg" />
          </MotionButton>
        </view>
        <scroll-view class="weekly-scroll" scroll-x :show-scrollbar="false" :scroll-left="scrollLeft" @scroll="rememberScroll">
          <view class="weekly-row">
            <MotionButton v-for="(course, index) in home.newCourses" :key="course.id"
              class="mini-card" feedback="card" :disabled="navigating"
              @click="openCourse(course, `#home-weekly-${index}`)">
              <view :id="`home-weekly-${index}`" class="mini-image-wrap">
                <image class="mini-image" :src="course.coverUrl" mode="aspectFill" />
                <view v-if="course.accessPolicy !== 'free'" class="vip small-vip">
                  <image class="crown-icon" src="/static/ui/crown.svg" /><text>VIP</text>
                </view>
              </view>
              <text class="mini-title">{{ course.title }}</text>
              <text class="mini-meta">{{ Math.round(course.totalSeconds / 60) }}分钟 · {{ course.accessPolicy === 'free' ? '免费' : `${audience(course.learnerCount)}人在练` }}</text>
            </MotionButton>
          </view>
        </scroll-view>
      </view>

      <view v-if="!home.newCourses.length && !home.recommendedCourses.length" class="state-card">
        <text class="state-title">新内容正在准备中</text>
        <text class="state-description">稍后再来看看，也可以先到练习页逛逛。</text>
        <MotionButton class="outline-button" :disabled="navigating" @click="open('practice')">去练习</MotionButton>
      </view>

      <view v-if="!home.checkedInToday" class="checkin-card">
        <view class="checkin-copy">
          <text class="checkin-title">今日还未打卡 <text v-if="home.streakDays" class="gold">连续 {{ home.streakDays }} 天</text></text>
          <text class="checkin-sub">{{ config.useMock ? '再练 1 天解锁 7 天徽章 + 200 积分' : '从一次安静的练习开始' }}</text>
        </view>
        <MotionButton class="checkin-button" :disabled="navigating" @click="open('practice')">去打卡</MotionButton>
      </view>
    </view>
    <PlayerScene v-if="nativePlayerOpen || expansion" :transition-state="{ expansion, expanded, frameStyle, coverStyle, backgroundStyle, nativeOpen: nativePlayerOpen, ready: nativePlayerReady, course: playerPreview.course, sourceLabel: '首页精选' }" @close="closeNativePlayer" />
  </PageShell>
</template>

<style scoped>
/* Dimensions follow the 375 × 812 phone in PDF S1, excluding the PDF paper. */
.home-header { padding: var(--top) 24px 0; }
.greeting { display: block; max-width: calc(100% - 76px); font-size: 22px; font-weight: 700; line-height: 33px; letter-spacing: -.35px; }
.greeting-sub { display: block; margin-top: 2px; font-size: 12px; line-height: 18px; color: var(--muted); }
.home-content, .loading-content { margin-top: 35px; }
.home-content { display: flex; flex-direction: column; flex: 1; }
.hero-section, .weekly-section, .checkin-card { flex-shrink: 0; }
.eyebrow { display: block; margin-bottom: 8px; font-size: 11px; line-height: 16.5px; letter-spacing: 1.1px; color: var(--muted); }
.hero-button { width: 100%; text-align: left; overflow: visible; }
.hero-image-wrap { position: relative; height: 0; padding-bottom: 56.7164%; overflow: hidden; border-radius: 16px; background: var(--surface); }
.hero-image { position: absolute; inset: 0; width: 100%; height: 100%; }
.hero-image-wrap::after { content: ''; position: absolute; inset: 0; background: linear-gradient(180deg,transparent 38%,rgba(15,29,31,.1) 65%,rgba(15,29,31,.62)); pointer-events: none; }
.hero-image-wrap .vip, .hero-meta { z-index: 1; }
.vip { position: absolute; top: 12px; left: 12px; display: flex; align-items: center; gap: 4px; padding: 4px 10px; border-radius: 24px; background: linear-gradient(90deg,var(--accent),#6FCABB); color: #fff; font-size: 10px; font-weight: 600; line-height: 15px; }
.crown-icon { width: 11px; height: 11px; flex-shrink: 0; }
.hero-meta { position: absolute; right: 12px; bottom: 12px; display: flex; align-items: center; gap: 3px; padding: 4px 10px; border-radius: 24px; color: #fff; background: rgba(0,0,0,.45); font-size: 10px; line-height: 15px; }
.person-icon { width: 9px; height: 10px; }
.hero-title { display: block; margin-top: 10px; font-size: 16px; font-weight: 700; line-height: 24px; }
.hero-description { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; margin-top: 4px; color: var(--muted); font-size: 12px; line-height: 19.5px; }
.weekly-section { --mini-width: 150px; margin-top: 24px; padding-bottom: 20px; }
.weekly-heading { display: flex; align-items: center; justify-content: space-between; min-height: 44px; margin-top: -8px; margin-bottom: 2px; }
.section-title { font-size: 18px; font-weight: 700; line-height: 27px; }
.more-link { display: flex; align-items: center; justify-content: flex-end; min-height: 44px; min-width: 76px; gap: 4px; padding: 8px 0 8px 12px; color: var(--muted); font-size: 11px; line-height: 16.5px; border-radius: 12px; }
.more-link image { width: 9px; height: 9px; }
.weekly-scroll { width: 100%; white-space: nowrap; }
.weekly-row { display: flex; width: max-content; gap: 12px; padding: 3px 20px 4px; }
.mini-card { width: var(--mini-width); flex: 0 0 var(--mini-width); text-align: left; }
.mini-image-wrap { position: relative; width: 100%; height: 0; padding-bottom: 66.6667%; overflow: hidden; border-radius: 12px; background: var(--surface); }
.mini-image { position: absolute; inset: 0; width: 100%; height: 100%; }
.small-vip { top: 8px; left: 8px; gap: 2px; padding: 2px 8px; font-size: 9px; line-height: 13.5px; }
.small-vip .crown-icon { width: 10px; height: 10px; }
.mini-title { display: block; margin-top: 8px; font-size: 12px; font-weight: 600; line-height: 18px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mini-meta { display: block; margin-top: 2px; color: var(--muted); font-size: 10px; line-height: 15px; }
.checkin-card { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: auto 20px 0; padding: 14px; border: 1px solid rgba(255,255,255,.13); border-radius: 18px; background: rgba(255,255,255,.035); box-shadow: inset 0 1px 0 rgba(255,255,255,.025); }
.checkin-copy { min-width: 0; }
.checkin-title { display: block; font-size: 13px; font-weight: 600; line-height: 19.5px; }
.checkin-sub { display: block; margin-top: 4px; color: var(--muted); font-size: 10px; line-height: 16px; }
.checkin-button { display: flex; align-items: center; justify-content: center; flex-shrink: 0; min-height: 44px; padding: 10px 16px; border-radius: 30px; color: #fff; background: linear-gradient(105deg,var(--accent),#6FCABB); box-shadow: 0 6px 18px rgba(63,179,163,.24); font-size: 12px; font-weight: 600; line-height: 18px; }
.checkin-button.is-pressed { box-shadow: 0 2px 8px rgba(63,179,163,.16); }

.skeleton { background: rgba(188,213,207,.13); }
.skeleton-eyebrow { width: 94px; height: 16.5px; margin-bottom: 8px; border-radius: 4px; }
.skeleton-cover { width: 100%; height: 0; padding-bottom: 56.7164%; }
.skeleton-title { width: 70%; height: 24px; margin-top: 10px; border-radius: 5px; }
.skeleton-description { width: 100%; height: 39px; margin-top: 4px; border-radius: 5px; }
.skeleton-heading { width: 96px; height: 27px; margin-top: 24px; border-radius: 5px; }
.skeleton-weekly { display: flex; gap: 12px; margin-top: 10px; }
.skeleton-course { width: 150px; height: 140px; }
.skeleton-checkin { height: 74px; margin-top: 20px; }
@media (min-height: 850px) {
  .hero-image-wrap, .skeleton-cover { padding-bottom: calc(56.7164% + clamp(0px, calc(55vh - 467.5px), 64px)); }
  .weekly-section { margin-top: clamp(24px, calc(12vh - 77px), 36px); }
}
@media (max-width: 350px) {
  .home-header { padding-left: 20px; padding-right: 20px; }
  .greeting { font-size: 21px; }
  .greeting-sub { font-size: 11px; }
  .weekly-section { --mini-width: 140px; }
  .checkin-card { padding: 12px; gap: 6px; }
  .checkin-title { font-size: 12px; }
  .checkin-button { padding: 8px 12px; }
}
@media (min-width: 390px) and (max-width: 599px) {
  .greeting { font-size: 24px; line-height: 36px; }
  .hero-description { font-size: 13px; line-height: 21px; }
  .weekly-section { --mini-width: 160px; }
  .mini-title { font-size: 13px; line-height: 18px; }
  .mini-meta, .checkin-sub { font-size: 11px; line-height: 17px; }
  .checkin-card { padding: 14px 12px; }
}
/* #ifdef H5 */
.prototype-status { position: absolute; top: 0; left: 0; right: 0; height: 48px; padding: 0 28px; display: flex; align-items: center; justify-content: space-between; font-size: 13px; font-weight: 600; pointer-events: none; }
.status-icons { display: flex; align-items: center; gap: 4px; }
.status-icons image { width: 13px; height: 13px; }
.prototype-capsule { position: absolute; top: 56px; right: 16px; height: 32px; width: 72px; display: flex; align-items: center; justify-content: space-evenly; border: 1px solid rgba(255,255,255,.2); border-radius: 22px; background: rgba(0,0,0,.25); pointer-events: none; }
.capsule-dots { display: flex; gap: 1px; }
.capsule-dots view { width: 2px; height: 2px; border-radius: 50%; background: #fff; }
.capsule-divider { width: 1px; height: 16px; background: rgba(255,255,255,.25); }
.capsule-ring { width: 11px; height: 11px; border: 1.5px solid #fff; border-top-color: transparent; border-radius: 50%; transform: rotate(-25deg); }
/* #endif */
</style>

<style>
/* Explicit S1 scope crosses PageShell's native component boundary. */
.page-shell.home-motion { display: flex; flex-direction: column; min-height: calc(100vh - var(--window-bottom, 0px)); padding-bottom: 16px; }
.home-motion .home-content, .home-motion .state-card { animation: none; }
.home-motion:not(.page-inactive) .loading-content .skeleton { animation: home-loading 1400ms ease-in-out infinite alternate; }
.home-motion.motion-reduced .home-content, .home-motion.motion-reduced .state-card, .home-motion.motion-reduced .skeleton { animation: none !important; }
@keyframes home-loading { from { opacity: .45; } to { opacity: .85; } }
@media (prefers-reduced-motion: reduce) {
  .home-motion .home-content, .home-motion .state-card, .home-motion .loading-content .skeleton { animation: none !important; }
}
</style>
