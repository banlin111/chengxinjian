export function preview(title: string, demo = false, id?: string) {
  uni.navigateTo({ url: `/pages/preview/index?title=${encodeURIComponent(title)}&demo=${demo ? '1' : '0'}${id ? `&id=${encodeURIComponent(id)}` : ''}` });
}
export function tab(name: 'home' | 'meditation' | 'courses' | 'practice' | 'profile') { uni.switchTab({ url: `/pages/${name}/index` }); }
