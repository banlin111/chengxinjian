import type { PlayerPreviewCourse } from '../stores/player-preview';

// Local presentation fixtures: one preview per mood, no audio or entitlement API.
export type MeditationPreview = PlayerPreviewCourse & { teacher: string; listeners: string; vip: boolean };
const forest = '/static/prototype/fe1597795aaf.jpg';
const mist = '/static/prototype/10420af33bf1.jpg';
const woods = '/static/prototype/535714df2219.jpg';
export const meditationPreviews: Record<string, MeditationPreview> = {
  sleep: { id: 'demo-meditation-sleep', title: '森林雨夜 · 深度睡眠引导', coverUrl: forest, totalSeconds: 2700, teacher: '林涧', listeners: '12.6万', vip: true },
  calm: { id: 'demo-meditation-calm', title: '呼吸之间 · 平复焦虑', coverUrl: mist, totalSeconds: 900, teacher: '一苇', listeners: '8.4万', vip: false },
  morning: { id: 'demo-meditation-morning', title: '山雾晨光 · 呼吸觉察', coverUrl: mist, totalSeconds: 900, teacher: '一苇', listeners: '8.4万', vip: false },
  bowl: { id: 'demo-meditation-bowl', title: '溪涧入定 · 颂钵长音', coverUrl: woods, totalSeconds: 3600, teacher: '明一', listeners: '5.9万', vip: true },
  nature: { id: 'demo-meditation-nature', title: '林间细雨 · 自然之声', coverUrl: forest, totalSeconds: 2700, teacher: '林涧', listeners: '12.6万', vip: false },
  kindness: { id: 'demo-meditation-kindness', title: '慈心冥想 · 温柔相待', coverUrl: woods, totalSeconds: 1200, teacher: '明一', listeners: '5.9万', vip: true },
  focus: { id: 'demo-meditation-focus', title: '一息一念 · 专注练习', coverUrl: mist, totalSeconds: 900, teacher: '一苇', listeners: '8.4万', vip: false },
};
export const meditationMoods = [
  { id: 'sleep', label: '缓解失眠', icon: 'fas-cloud-moon-ffffff', colors: '#3FB3A3,#2C8C82' },
  { id: 'calm', label: '平复焦虑', icon: 'fas-heart-pulse-ffffff', colors: '#2E9BB5,#16302F' },
  { id: 'morning', label: '晨间唤醒', icon: 'fas-sun-ffffff', colors: '#E9C08A,#8F6B3A' },
  { id: 'bowl', label: '颂钵音疗', icon: 'fas-bell-ffffff', colors: '#8A6BD1,#16302F' },
  { id: 'nature', label: '自然之声', icon: 'fas-leaf-ffffff', colors: '#4FA97F,#16302F' },
  { id: 'kindness', label: '慈心冥想', icon: 'fas-hands-praying-ffffff', colors: '#D16B8A,#16302F' },
  { id: 'focus', label: '专注力', icon: 'fas-brain-ffffff', colors: '#5B8FD1,#16302F' },
  { id: 'all', label: '全部', icon: 'fas-grip-ffffff', colors: '' },
] as const;
