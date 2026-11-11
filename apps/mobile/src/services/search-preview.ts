import { coursePreviews } from './course-preview';
import { meditationPreviews, meditationMoods } from './meditation-preview';

export type SearchPreviewItem = {
  id: string; kind: 'course' | 'meditation'; title: string; subtitle: string; coverUrl: string; url: string;
};
const normalize = (value: string) => value.toLowerCase().replace(/[\s·|｜，。/]/g, '');
export const hotSearches = ['颂钵音疗','入睡引导','缓解焦虑','晨间正念','身体扫描','雨声白噪音','行走冥想','呼吸练习'];
export const suggestedSearches = [
  { label: '3 分钟快速入睡', icon: 'fas-cloud-moon-3fb3a3' },
  { label: '上班前的定心', icon: 'fas-briefcase-e9c08a' },
  { label: '森林里的 20 分钟', icon: 'fas-leaf-3fb3a3' },
  { label: '情绪急救包', icon: 'fas-heart-crack-e9c08a' },
];
const aliases: Record<string,string> = {
  sleep: '入睡引导 睡眠 助眠', calm: '缓解焦虑 呼吸练习', morning: '晨间正念 呼吸练习',
  nature: '雨声白噪音', focus: '上班前的定心 专注',
};
// This is deterministic local fixture matching, never a fallback for a failed API.
const catalog = [
  ...Object.entries(coursePreviews).map(([key, course]) => ({
    id: course.id, kind: 'course' as const, title: course.title, coverUrl: course.coverUrl,
    subtitle: `${course.lessonCount} 节${course.minutes ? ` · ${course.minutes} 分钟` : ''}`,
    url: `/pages/course-detail/index?courseId=${encodeURIComponent(course.id)}`,
    keywords: normalize(`${course.title} ${course.summary} ${course.lessonCount} 节 ${key === 'body' ? '林涧' : ''} ${key === 'emotions' ? '情绪急救包' : ''}`),
  })),
  ...Object.entries(meditationPreviews).map(([key, item]) => ({
    id: item.id, kind: 'meditation' as const, title: item.title, coverUrl: item.coverUrl,
    subtitle: `${Math.round(item.totalSeconds / 60)} 分钟 · 导师 ${item.teacher}`,
    url: `/pages/meditation-detail/index?id=${encodeURIComponent(item.id)}`,
    keywords: normalize(`${item.title} ${item.teacher} ${meditationMoods.find(mood => mood.id === key)?.label || ''} ${aliases[key] || ''}`),
  })),
];
export function searchPreview(query: string): SearchPreviewItem[] {
  const terms = query.trim().split(/\s+/).map(normalize).filter(Boolean);
  if (!terms.length) return [];
  return catalog.filter(item => terms.every(term => item.keywords.includes(term)));
}

const HISTORY_KEY = 'cxj.search.history';
export function readSearchHistory(): string[] {
  try {
    const value: unknown = uni.getStorageSync(HISTORY_KEY);
    return Array.isArray(value) ? [...new Set(value.filter((item): item is string => typeof item === 'string' && Boolean(item.trim())).map(item => item.trim().slice(0,80)))].slice(0,8) : [];
  } catch { return []; }
}
export function writeSearchHistory(items: string[]) {
  try { uni.setStorageSync(HISTORY_KEY, items.slice(0,8)); }
  catch { uni.showToast({ title: '历史记录暂时无法保存', icon: 'none' }); }
}
export function addSearchHistory(items: string[], query: string) {
  return [query, ...items.filter(item => normalize(item) !== normalize(query))].slice(0,8);
}
