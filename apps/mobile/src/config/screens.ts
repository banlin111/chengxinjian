export const screens = [
  { id: 1, path: 'home', title: '首页推荐', tab: true },
  { id: 2, path: 'meditation', title: '冥想分类', tab: true },
  { id: 3, path: 'courses', title: '课程库', tab: true },
  { id: 4, path: 'course-detail', title: '课程详情' },
  { id: 5, path: 'player', title: '沉浸播放器' },
  { id: 6, path: 'mixer', title: '白噪音混音' },
  { id: 7, path: 'shop', title: '商城首页' },
  { id: 8, path: 'product', title: '商品详情' },
  { id: 9, path: 'membership', title: '会员中心' },
  { id: 10, path: 'practice', title: '打卡练习', tab: true },
  { id: 11, path: 'live', title: '共修直播' },
  { id: 12, path: 'profile', title: '个人中心', tab: true },
  { id: 13, path: 'search', title: '搜索' },
  { id: 14, path: 'report', title: '静心报告' },
  { id: 15, path: 'orders', title: '订单物流' },
  { id: 16, path: 'points', title: '积分中心' },
  { id: 17, path: 'live-room', title: '直播间' },
  { id: 18, path: 'settings', title: '设置' },
  { id: 19, path: 'affiliate', title: '分销中心' },
  { id: 20, path: 'poster', title: '推广海报' },
  { id: 21, path: 'team', title: '推广人员' },
  { id: 22, path: 'commissions', title: '佣金明细' },
] as const;

export function goScreen(id: number) {
  const screen = screens.find(item => item.id === id);
  if (!screen) return;
  const url = `/pages/${screen.path}/index`;
  if ('tab' in screen && screen.tab) uni.switchTab({ url });
  else uni.navigateTo({ url });
}

export function backScreen() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else goScreen(1);
}
