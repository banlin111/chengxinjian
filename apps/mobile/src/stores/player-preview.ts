import { ref } from 'vue';
import { defineStore } from 'pinia';
import type { Course } from '../../../../contracts/models';

// In-memory presentation data only; this does not grant playback access.
export type PlayerPreviewCourse = Pick<Course, 'id' | 'title' | 'coverUrl' | 'totalSeconds'>;
export const usePlayerPreviewStore = defineStore('player-preview', () => {
  const course = ref<PlayerPreviewCourse | null>(null);
  function select(value: PlayerPreviewCourse) {
    course.value = { id: value.id, title: value.title, coverUrl: value.coverUrl, totalSeconds: value.totalSeconds };
  }
  return { course, select };
});
