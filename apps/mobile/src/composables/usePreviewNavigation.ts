import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';

export function usePreviewNavigation() {
  const navigating = ref(false);
  onShow(() => { navigating.value = false; });
  function navigate(url: string) {
    if (navigating.value) return;
    navigating.value = true;
    uni.navigateTo({ url, fail: () => {
      navigating.value = false;
      uni.showToast({ title: '暂时无法打开，请重试', icon: 'none' });
    } });
  }
  return { navigating, navigate };
}
