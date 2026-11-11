<script setup lang="ts">
import PlayerScene from '../../components/PlayerScene.vue';
import { usePlayerTransition } from '../../composables/usePlayerTransition';

import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import ScreenFrame from '../../components/ScreenFrame.vue';
import MotionButton from '../../components/MotionButton.vue';
import AppIcon from '../../components/AppIcon.vue';
import { useScreenPreview } from '../../composables/useScreenPreview';
import { coursePreviews, coursePlayerPreview } from '../../services/course-preview';
import { usePreviewNavigation } from '../../composables/usePreviewNavigation';
const { liked, followed, notice, action } = useScreenPreview(4);
const course = ref(coursePreviews.body);
const valid = ref(true);
const isBody = computed(() => course.value.id === coursePreviews.body.id);
const summary = computed(() => isBody.value ? '从脚到头的 12 段式扫描，逐节放松肌肉与神经，缓解久坐紧张与入睡困难。' : course.value.summary);
const purchaseLabel = computed(() => course.value.priceYuan === null ? '价格待确认' : `¥${course.value.priceYuan} 买断`);
const lessons = computed(() => [
  { number: '01', title: course.value.firstLesson, duration: isBody.value ? '12:30 · 试听' : '试听演示 · 时长待配置', free: true },
  ...(isBody.value ? [
    { number: '02', title: '脚与小腿的觉察', duration: '15:00', free: false },
    { number: '03', title: '腹腔与呼吸的空间', duration: '14:20', free: false },
  ] : []),
]);
const { state: playerTransition, busy: playerBusy, open: openPlayer, close: closePlayer } = usePlayerTransition(false);
const { navigating: routeNavigating, navigate } = usePreviewNavigation();
const navigating = computed(() => routeNavigating.value || playerBusy.value);
onLoad(options => {
  if (!options?.courseId) return;
  const found = Object.values(coursePreviews).find(item => item.id === options.courseId);
  valid.value = Boolean(found);
  if (found) course.value = found;
});
function playPreview() {
  if (navigating.value) return;
  openPlayer(coursePlayerPreview(course.value), { from: 'course', label: '课程试听', selector: '.course-hero-image', fallbackSelector: '#course-player-origin' });
}
function membershipPreview() {
  if (navigating.value) return;
  if (course.value.vip) navigate('/pages/membership/index');
  else notice('课程权益', '当前示例未确认会员权益，具体以后端配置为准。');
}
function purchasePreview() {
  if (navigating.value) return;
  if (course.value.priceYuan === null) notice('价格待确认', '当前示例没有确定售价，暂不提供购买。');
  else action(purchaseLabel.value);
}
</script>

<template>
  <!-- #ifdef MP-WEIXIN -->
  <page-meta :page-style="playerTransition.nativeOpen ? 'overflow:hidden' : ''" />
  <page-container v-if="playerTransition.nativeOpen" :show="playerTransition.nativeOpen" :duration="0" :overlay="false" :z-index="1000"
    custom-style="height:0;background:transparent;" @beforeleave="closePlayer" />
  <!-- #endif -->

  <ScreenFrame v-if="valid" :screen="4" footer>
    <view class="course-hero" aria-hidden="true">
      <image class="course-hero-image" mode="aspectFill" :src="course.detailCoverUrl" />
      <view class="course-hero-shade" />
    </view>
    <view class="course-detail-content screen-content">
      <view class="course-badges">
        <view class="course-badge course-access"><AppIcon v-if="course.vip" asset="fas-crown-ffffff" /><text>{{ course.vip ? 'VIP 专享' : '课程预览' }}</text></view>
        <text class="course-badge course-length">{{ course.lessonCount }} 节{{ course.minutes ? ` · ${course.minutes} 分钟` : '' }}</text>
      </view>
      <text class="course-detail-title">{{ course.title }}</text>
      <text class="course-summary">{{ summary }}</text>
      <view v-if="isBody" class="course-teacher">
        <image class="teacher-avatar" mode="aspectFill" src="/static/prototype/b3732e014658.jpg" />
        <view class="teacher-copy">
          <view class="teacher-heading"><text class="teacher-name">林涧</text><view class="teacher-verified"><AppIcon asset="fas-circle-check-e9c08a" /><text>认证导师</text></view></view>
          <text class="teacher-description">正念减压 MBSR 认证 · 从业 9 年</text>
        </view>
        <MotionButton class="teacher-follow" :class="{ 'is-followed': followed }" feedback="icon" :busy="navigating" :aria-label="followed ? '取消关注林涧' : '关注林涧'" :aria-pressed="followed ? 'true' : 'false'" data-action="+ 关注" @click="followed = !followed"><text>{{ followed ? '已关注' : '+ 关注' }}</text></MotionButton>
      </view>
      <view class="course-directory">
        <view class="directory-heading"><text class="directory-title">课程目录</text><text class="directory-count">共 {{ course.lessonCount }} 节</text></view>
        <view class="lesson-list">
          <view v-for="lesson in lessons" :key="lesson.number" :id="lesson.free ? 'course-player-origin' : undefined"><MotionButton class="course-lesson" feedback="icon" :busy="navigating" :aria-label="`${lesson.number} ${lesson.title}`" :data-action="`${lesson.number} ${lesson.title}`" @click="lesson.free ? playPreview() : membershipPreview()">
            <view class="lesson-number" :class="{ 'lesson-play': lesson.free }"><AppIcon v-if="lesson.free" asset="fas-play-ffffff" /><text v-else>{{ lesson.number }}</text></view>
            <view class="lesson-copy"><text class="lesson-title">{{ lesson.number }} {{ lesson.title }}</text><text class="lesson-duration">{{ lesson.duration }}</text></view>
            <text v-if="lesson.free" class="lesson-free">免费</text><view v-else class="lesson-lock"><AppIcon asset="fas-lock-a8c5bd" /></view>
          </MotionButton></view>
        </view>
        <MotionButton class="directory-more" feedback="icon" :busy="navigating" :aria-label="`展开全部 ${course.lessonCount} 节`" :data-action="`展开全部 ${course.lessonCount} 节`" @click="notice('课程目录', '当前为示例目录，完整课节将在接入后端后展示。')"><text>展开全部 {{ course.lessonCount }} 节</text><AppIcon asset="fas-chevron-down-a8c5bd" /></MotionButton>
      </view>
    </view>
    <view class="course-footer screen-footer">
      <view class="course-purchase-bar">
        <MotionButton class="course-save" :class="{ 'is-saved': liked }" feedback="icon" :busy="navigating" :aria-label="liked ? '取消收藏' : '收藏'" :aria-pressed="liked ? 'true' : 'false'" data-action="收藏" @click="liked = !liked"><view class="save-heart"><AppIcon :asset="liked ? 'fas-heart-3fb3a3' : 'far-heart-a8c5bd'" /></view><text>{{ liked ? '已收藏' : '收藏' }}</text></MotionButton>
        <MotionButton class="course-buy" feedback="icon" :busy="navigating" :aria-label="purchaseLabel" :data-action="purchaseLabel" @click="purchasePreview">{{ purchaseLabel }}</MotionButton>
        <MotionButton class="course-membership" feedback="icon" :busy="navigating" :aria-label="course.vip ? '开通会员 0 元学' : '权益待确认'" :data-action="course.vip ? '开通会员 0 元学' : '权益待确认'" @click="membershipPreview">{{ course.vip ? '开通会员 0 元学' : '权益待确认' }}</MotionButton>
      </view>
    </view>
  </ScreenFrame>
  <ScreenFrame v-else :screen="4"><view class="page-header"><text class="page-title">暂时找不到这门课程</text><text class="page-subtitle">请返回课程库重新选择。</text></view></ScreenFrame>
  <PlayerScene v-if="playerTransition.nativeOpen || playerTransition.expansion" :transition-state="playerTransition" @close="closePlayer" />
</template>

<style scoped>
.course-hero { position: absolute; top: 0; left: 0; right: 0; height: calc(var(--top) + 216px); overflow: hidden; }
.course-hero-image { width: 100%; height: 100%; }
.course-hero-shade { position: absolute; inset: 0; background: linear-gradient(to bottom,rgba(15,29,31,.32),rgba(15,29,31,.18) 40%,var(--bg) 100%); }
.course-detail-content { padding: calc(var(--top) + 164px) 20px 0; color: var(--text); }
.course-badges { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.course-badge { display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; border-radius: 20px; font-size: 10px; line-height: 18px; }
.course-access { color: #fff; font-weight: 600; background-image: linear-gradient(110deg,#3FB3A3,#6FCABB); }
.course-length { color: var(--muted); background: rgba(255,255,255,.12); }
.course-detail-title { display: block; margin-top: 10px; font-size: 22px; font-weight: 700; line-height: 31px; }
.course-summary { display: block; margin-top: 8px; color: var(--muted); font-size: 12px; line-height: 21px; }
.course-teacher { display: flex; align-items: center; gap: 10px; margin-top: 18px; padding: 12px; border: 1px solid var(--line); border-radius: 16px; background: var(--surface); }
.teacher-avatar { width: 44px; height: 44px; border-radius: 50%; flex-shrink: 0; }
.teacher-copy { flex: 1; min-width: 0; }
.teacher-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.teacher-name { font-size: 14px; font-weight: 600; line-height: 22px; }
.teacher-verified { display: flex; align-items: center; gap: 3px; color: var(--gold); font-size: 10px; }
.teacher-description { display: block; margin-top: 3px; color: var(--muted); font-size: 10px; line-height: 17px; }
.teacher-follow { display: flex; align-items: center; justify-content: center; min-width: 62px; min-height: 44px; flex-shrink: 0; color: var(--accent); font-size: 11px; }
.teacher-follow text { display: block; padding: 6px 10px; border: 1px solid var(--accent); border-radius: 20px; }
.teacher-follow.is-followed text { background: var(--surface); border-color: var(--line); color: var(--muted); }
.course-directory { margin-top: 22px; }
.directory-heading { display: flex; align-items: baseline; gap: 8px; margin-bottom: 12px; }
.directory-title { font-size: 16px; font-weight: 700; line-height: 24px; }
.directory-count { font-size: 11px; color: var(--muted); }
.lesson-list { display: flex; flex-direction: column; gap: 8px; }
.course-lesson { display: flex; align-items: center; gap: 12px; width: 100%; min-height: 62px; padding: 10px 12px; border-radius: 13px; background-image: linear-gradient(var(--surface),var(--surface)); text-align: left; }
.lesson-number { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 28px; height: 28px; border-radius: 50%; background: rgba(255,255,255,.12); color: var(--muted); font-size: 10px; }
.lesson-play { background: var(--accent); font-size: 9px; }
.lesson-copy { flex: 1; min-width: 0; }
.lesson-title { display: block; color: var(--text); font-size: 12px; font-weight: 600; line-height: 20px; }
.lesson-duration { display: block; color: var(--muted); font-size: 10px; line-height: 18px; }
.lesson-free { color: var(--gold); font-size: 10px; flex-shrink: 0; }.lesson-lock { font-size: 12px; }
.directory-more { display: flex; align-items: center; justify-content: center; gap: 7px; min-height: 44px; width: 100%; margin-top: 4px; color: var(--muted); font-size: 11px; }
.course-footer.screen-footer { padding-left: 20px; padding-right: 20px; background: linear-gradient(transparent,var(--bg) 30%); color: var(--text); }
.course-purchase-bar { display: flex; align-items: center; gap: 8px; padding: 10px; border: 1px solid var(--line); border-radius: 16px; background: var(--bg); }
.course-save { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px; flex-shrink: 0; width: 44px; min-height: 46px; font-size: 10px; color: var(--muted); }
.save-heart { display: flex; font-size: 19px; transform: scale(1); transition: transform 320ms cubic-bezier(.22,1.4,.36,1); }
.is-saved { color: var(--accent); }.is-saved .save-heart { transform: scale(1.12); }
.course-buy, .course-membership { display: flex; align-items: center; justify-content: center; flex: 1; min-width: 0; min-height: 46px; padding: 8px 5px; border-radius: 11px; font-size: 12px; font-weight: 600; line-height: 18px; text-align: center; }
.course-buy { color: #0F1D1F; background-image: linear-gradient(110deg,#E9C08A,#D9A35C); }
.course-membership { flex: 1.25; color: #fff; background-image: linear-gradient(110deg,#3FB3A3,#6FCABB); }
@media (max-width: 350px) { .course-detail-content { padding-left: 16px; padding-right: 16px; }.course-footer.screen-footer { padding-left: 12px; padding-right: 12px; }.course-teacher { gap: 8px; padding: 10px; }.teacher-avatar { width: 38px; height: 38px; }.course-detail-title { font-size: 21px; } }
@media (prefers-reduced-motion: reduce) { .save-heart { transition: none; transform: none; } }
</style>
<style>
.pdf-screen[data-screen="4"].screen-with-footer { padding-bottom: calc(112px + env(safe-area-inset-bottom)); }
</style>
