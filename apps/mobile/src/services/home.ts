import type { Course, Home } from '../../../../contracts/models';
import { config } from '../config';
import { get } from './http';
function mockCourse(id: string, title: string, coverUrl: string, seconds: number, learners: number, free = false): Course {
  return {
    id, version: 1, createdAt: '2026-11-09T00:00:00Z', updatedAt: '2026-11-09T00:00:00Z', title,
    summary: '唤醒心中温柔的力量，让内在空间越来越舒展，适合睡前的深度放松……', description: '',
    coverUrl, categoryId: 'demo_mindfulness', teacherId: 'demo_teacher', difficulty: 'beginner',
    totalSeconds: seconds, lessonCount: 1, priceFen: free ? 0 : 6800, originalPriceFen: null,
    accessPolicy: free ? 'free' : 'member_or_purchase',
    access: { allowed: free, reason: free ? 'free' : 'locked', trialSeconds: 0, expiresAt: null },
    favorite: false, liked: false, likeCount: 0, learnerCount: learners, progressPercent: 0, status: 'published',
  };
}
export const sampleCourses = [
  mockCourse('demo_kindness', '绿度母｜慈心唱诵冥想', '/static/reference/mountain.jpg', 1260, 79000),
  mockCourse('demo_morning', '晨间正念 10 分钟', '/static/reference/morning.jpg', 600, 32000),
  mockCourse('demo_body', '星空身体扫描', '/static/reference/ocean.jpg', 1800, 51000),
  mockCourse('demo_stream', '溪流白噪音', '/static/reference/lake.jpg', 3600, 0, true),
];
const sampleHome: Home = {
  greeting: '晚上好，涧友', continueLearning: null, recommendedCourses: [sampleCourses[0]],
  recommendedMeditations: [], newCourses: sampleCourses.slice(1), notices: [], banners: [], checkedInToday: false, streakDays: 6,
};
export async function getHome(): Promise<Home> {
  if (!config.useMock) return get<Home>('/home');
  await new Promise(resolve => setTimeout(resolve, 250));
  // Testable preview states; intentionally never used in live API mode.
  const state = uni.getStorageSync('cxj.mock.homeState');
  if (state === 'error') throw new Error('示例加载失败，请重试');
  if (state === 'empty') return { ...sampleHome, recommendedCourses: [], newCourses: [], streakDays: 0 };
  return JSON.parse(JSON.stringify(sampleHome)) as Home;
}
