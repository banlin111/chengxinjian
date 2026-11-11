<script setup lang="ts">
import { computed, ref } from 'vue';
import ScreenFrame from '../../components/ScreenFrame.vue';
import MotionButton from '../../components/MotionButton.vue';
import AppIcon from '../../components/AppIcon.vue';
import { courseFilters, coursePreviews } from '../../services/course-preview';
import type { CoursePreview } from '../../services/course-preview';
import { usePreviewNavigation } from '../../composables/usePreviewNavigation';

const { navigating, navigate } = usePreviewNavigation();
const selectedFilter = ref<(typeof courseFilters)[number]['id']>('all');
const featured = coursePreviews.camp;
const course = computed(() => coursePreviews[courseFilters.find(item => item.id === selectedFilter.value)!.preview]);
function selectFilter(id: typeof selectedFilter.value) { if (!navigating.value) selectedFilter.value = id; }
function openCourse(item: CoursePreview) { navigate(`/pages/course-detail/index?courseId=${encodeURIComponent(item.id)}`); }
</script>

<template>
  <ScreenFrame :screen="3" primary gradient>
    <view class="courses-content screen-content">
      <text class="courses-title">课程</text>
      <MotionButton class="courses-search" feedback="icon" :busy="navigating" aria-label="搜索课程 / 导师 / 专题"
        @click="navigate('/pages/search/index')">
        <AppIcon asset="fas-magnifying-glass-a8c5bd" /><text>搜索课程 / 导师 / 专题</text>
      </MotionButton>

      <view class="course-filters" role="group" aria-label="课程分类筛选">
        <MotionButton v-for="item in courseFilters" :key="item.id" class="course-filter" feedback="icon"
          :busy="navigating" :aria-label="item.label" :aria-pressed="selectedFilter === item.id ? 'true' : 'false'"
          :data-action="item.label" @click="selectFilter(item.id)">
          <view class="filter-pill" :class="{ 'filter-active': selectedFilter === item.id }">{{ item.label }}</view>
        </MotionButton>
      </view>

      <MotionButton class="featured-course" feedback="icon" :busy="navigating" :aria-label="featured.title" :data-action="featured.title" @click="openCourse(featured)">
        <image class="featured-cover" :src="featured.coverUrl" mode="aspectFill" />
        <view class="featured-shade" />
        <view class="featured-info">
          <view class="featured-copy"><text class="featured-title">{{ featured.title }}</text><text class="featured-meta">{{ featured.lessonCount }} 节 · 已 {{ featured.learners }}人加入</text></view>
          <text class="featured-price">¥{{ featured.priceYuan }}</text>
        </view>
      </MotionButton>

      <view class="course-list" aria-live="polite">
        <MotionButton class="course-row" feedback="icon" :busy="navigating" :aria-label="course.title" :data-action="course.title" @click="openCourse(course)">
          <image class="course-cover" :src="course.coverUrl" mode="aspectFill" />
          <view class="course-copy">
            <view class="course-title-line">
              <text class="course-row-title">{{ course.title }}</text>
              <view v-if="course.vip" class="course-badge" aria-label="会员课程"><AppIcon asset="fas-crown-3fb3a3" /></view>
              <text v-else-if="course.hot" class="course-badge course-hot">热</text>
            </view>
            <text class="course-summary">{{ course.summary }}</text>
            <view class="course-meta"><AppIcon asset="fas-user-a8c5bd" /><text>{{ course.learners }} · 共 {{ course.lessonCount }} 节 · {{ course.minutes }} 分钟</text></view>
          </view>
        </MotionButton>
      </view>
    </view>
  </ScreenFrame>
</template>

<style scoped>
.courses-content { padding: var(--top) 20px 0; }
.courses-title { display: block; padding-left: 4px; color: var(--text); font-size: 22px; font-weight: 700; line-height: 33px; letter-spacing: -.35px; }
.courses-search { display: flex; align-items: center; gap: 8px; width: 100%; min-height: 44px; margin-top: 12px; padding: 10px 16px; border: 1px solid rgba(255,255,255,.14); border-radius: 24px; background-image: linear-gradient(rgba(255,255,255,.1),rgba(255,255,255,.1)); color: var(--muted); font-size: 12px; line-height: 20px; text-align: left; }
.course-filters { display: flex; gap: 8px; margin-top: 10px; }
.course-filter { display: flex; align-items: center; justify-content: center; min-height: 44px; min-width: 44px; border-radius: 24px; }
.filter-pill { padding: 7px 15px; border: 1px solid rgba(255,255,255,.08); border-radius: 24px; background: rgba(255,255,255,.08); color: var(--muted); font-size: 12px; line-height: 18px; white-space: nowrap; transition: background-color 160ms ease, color 160ms ease, border-color 160ms ease; }
.filter-active { background: #3FB3A3; color: #0F1D1F; border-color: #3FB3A3; font-weight: 600; }
.featured-course { position: relative; display: block; width: 100%; height: 176px; margin-top: 10px; overflow: hidden; border-radius: 18px; text-align: left; background: #16302F; }
.featured-cover { position: absolute; inset: 0; width: 100%; height: 100%; }
.featured-shade { position: absolute; inset: 0; background: linear-gradient(180deg,rgba(15,29,31,0) 20%,rgba(15,29,31,.12) 45%,rgba(15,29,31,.92) 100%); }
.featured-info { position: absolute; left: 14px; right: 14px; bottom: 14px; display: flex; align-items: flex-end; justify-content: space-between; gap: 10px; }
.featured-copy { min-width: 0; flex: 1; }
.featured-title { display: block; color: #fff; font-size: 15px; font-weight: 700; line-height: 23px; }
.featured-meta { display: block; margin-top: 3px; color: #C4D7D1; font-size: 10px; line-height: 17px; }
.featured-price { flex-shrink: 0; padding: 7px 12px; border-radius: 24px; background: linear-gradient(100deg,#E9C08A,#D9A35C); color: #172522; font-size: 12px; font-weight: 700; line-height: 18px; }
.course-list { margin-top: 16px; min-height: 116px; }
.course-row { display: flex; align-items: center; gap: 12px; width: 100%; min-height: 116px; padding: 12px; border: 1px solid rgba(255,255,255,.12); border-radius: 18px; background-image: linear-gradient(rgba(255,255,255,.055),rgba(255,255,255,.055)); text-align: left; }
.course-cover { flex-shrink: 0; width: 88px; height: 84px; border-radius: 12px; }
.course-copy { min-width: 0; flex: 1; }
.course-title-line { display: flex; align-items: center; gap: 6px; }
.course-row-title { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; color: var(--text); font-size: 14px; font-weight: 600; line-height: 21px; }
.course-badge { display: flex; align-items: center; justify-content: center; flex-shrink: 0; min-width: 20px; height: 20px; border-radius: 5px; color: var(--accent); background: rgba(63,179,163,.2); font-size: 10px; }
.course-hot { color: #E9C08A; background: rgba(233,192,138,.18); }
.course-summary { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; margin-top: 5px; color: var(--muted); font-size: 11px; line-height: 18px; }
.course-meta { display: flex; align-items: center; gap: 4px; margin-top: 7px; color: var(--muted); font-size: 10px; line-height: 16px; }
@media (min-width: 390px) and (max-width: 599px) {
  .courses-title { font-size: 24px; line-height: 36px; }
  .featured-course { height: 188px; }
}
@media (max-width: 350px) {
  .courses-content { padding-left: 16px; padding-right: 16px; }
  .course-filters { gap: 5px; }
  .filter-pill { padding: 7px 12px; }
  .featured-title { font-size: 14px; line-height: 21px; }
  .course-row { gap: 10px; padding: 11px; }
  .course-cover { width: 68px; height: 82px; }
  .course-row-title { font-size: 13px; line-height: 20px; }
}
@media (prefers-reduced-motion: reduce) { .filter-pill { transition: none; } }
</style>
<style>
.pdf-screen[data-screen="3"] { min-height: 0; padding-bottom: 0; }
</style>
