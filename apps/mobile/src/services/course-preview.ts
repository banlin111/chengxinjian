// PDF-derived display fixtures only. Null prices must not become invented offers.
export type CoursePreview = {
  id: string; title: string; summary: string; coverUrl: string; detailCoverUrl: string;
  lessonCount: number; minutes: number | null; learners: string;
  priceYuan: number | null; vip: boolean; hot?: boolean; firstLesson: string;
};
export const coursePreviews: Record<string, CoursePreview> = {
  camp: { id: 'demo-course-camp', title: '21 天正念入门训练营', summary: '用 21 节练习，逐步认识正念，并把觉察带进日常生活。', coverUrl: '/static/prototype/d274344e01bb.jpg', detailCoverUrl: '/static/prototype/d274344e01bb.jpg', lessonCount: 21, minutes: null, learners: '3.8万', priceYuan: 199, vip: false, firstLesson: '认识正念，从当下开始' },
  body: { id: 'demo-course-body', title: '身体扫描完全指南', summary: '从脚到头的 12 段式扫描，缓解久坐紧张', coverUrl: '/static/prototype/ef1a45ed0a4c.jpg', detailCoverUrl: '/static/prototype/f4587931c496.jpg', lessonCount: 12, minutes: 168, learners: '6.2万', priceYuan: 68, vip: true, firstLesson: '什么是身体扫描' },
  walking: { id: 'demo-course-walking', title: '行走冥想 · 动中禅', summary: '把练习带进日常步态，适合无法久坐的你', coverUrl: '/static/prototype/4983b2266c9d.jpg', detailCoverUrl: '/static/prototype/4983b2266c9d.jpg', lessonCount: 8, minutes: 96, learners: '2.8万', priceYuan: null, vip: false, firstLesson: '在行走中觉察步伐' },
  emotions: { id: 'demo-course-emotions', title: '情绪急救 · 焦虑应对 8 课', summary: '焦虑来袭时的即时工具箱，随取随用', coverUrl: '/static/prototype/40c4403f07ce.jpg', detailCoverUrl: '/static/prototype/40c4403f07ce.jpg', lessonCount: 8, minutes: 120, learners: '9.1万', priceYuan: null, vip: false, hot: true, firstLesson: '认识当下的情绪' },
};
export const courseFilters = [
  { id: 'all', label: '全部', preview: 'body' },
  { id: 'beginner', label: '入门 7 天', preview: 'body' },
  { id: 'advanced', label: '进阶', preview: 'walking' },
  { id: 'master', label: '大师课', preview: 'emotions' },
] as const;

export function coursePlayerPreview(course: CoursePreview) {
  return { id: course.id, title: course.title, coverUrl: course.detailCoverUrl,
    totalSeconds: course.id === coursePreviews.body.id ? 750 : 0 };
}
