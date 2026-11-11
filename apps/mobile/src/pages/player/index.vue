<script setup lang="ts">
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import PlayerScene from '../../components/PlayerScene.vue';
import ScreenFrame from '../../components/ScreenFrame.vue';
import { usePlayerPreviewStore } from '../../stores/player-preview';
import type { PlayerPreviewCourse } from '../../stores/player-preview';
import { sampleCourses } from '../../services/home';
import { config } from '../../config';
import { meditationPreviews } from '../../services/meditation-preview';
import { replayPreview } from '../../services/replay-preview';
import { coursePreviews, coursePlayerPreview } from '../../services/course-preview';
const preview = usePlayerPreviewStore();
const selectedCourse = ref<typeof preview.course>(null);
const fromHome = ref(false);
const sourceLabel = ref('首页精选');
const invalid = ref(false);
onLoad(options => {
  if (!options || !['home','meditation','course','replay','practice'].includes(options.from || '')) return;
  const fromMeditation = options.from === 'meditation';
  const fromCourse = options.from === 'course';
  const fromReplay = options.from === 'replay';
  sourceLabel.value = options.from === 'practice' ? '练习打卡' : fromReplay ? '回放演示' : fromCourse ? '课程试听' : fromMeditation ? '冥想精选' : '首页精选';
  const fixtures: PlayerPreviewCourse[] = fromReplay ? [replayPreview] : fromCourse ? Object.values(coursePreviews).map(coursePlayerPreview) : fromMeditation ? Object.values(meditationPreviews) : sampleCourses;
  selectedCourse.value = preview.course?.id === options.courseId ? preview.course
    : (config.useMock || fromMeditation || fromCourse || fromReplay) ? fixtures.find(course => course.id === options.courseId) ?? null : null;
  fromHome.value = Boolean(selectedCourse.value);
  invalid.value = !selectedCourse.value;
});
</script>
<template>
  <ScreenFrame v-if="invalid" :screen="5"><view class="page-header"><text class="page-title">暂时找不到这段音频</text><text class="page-subtitle">请返回重新选择。</text></view></ScreenFrame>
  <PlayerScene v-else :course="selectedCourse" :from-home="fromHome" :source-label="sourceLabel" />
</template>
