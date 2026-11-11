import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

export const mixerTracks = [
  { id: 'rain', name: '细雨', icon: 'fas-cloud-rain-7fb2e5', color: '#7FB2E5' },
  { id: 'fire', name: '篝火', icon: 'fas-fire-e5a76f', color: '#E5A76F' },
  { id: 'insects', name: '虫鸣', icon: 'fas-cricket-9bd18a', color: '#9BD18A' },
  { id: 'wind', name: '晚风', icon: 'fas-wind-a8c5bd', color: '#A8C5BD' },
] as const;
export const mixerTimers = [0,15,30,45,60];
type Recipe = { volumes: number[]; enabled: boolean[]; timer: number };
const recipeKey = 'cxj.mixer.recipe.v1';
// UI configuration only: no audio objects, background tasks or remote writes.
export const useMixerPreviewStore = defineStore('mixer-preview', () => {
  const volumes = ref([60,35,0,0]);
  const enabled = ref([true,true,false,false]);
  const timer = ref(45);
  const saved = ref<Recipe | null>(null);
  const rainLabel = computed(() => enabled.value[0] ? `雨声 ${volumes.value[0]}%` : '雨声关闭');
  const audible = computed(() => volumes.value.some((value,index) => enabled.value[index] && value > 0));
  function setVolume(index: number, value: number) {
    if (index < 0 || index >= mixerTracks.length || !Number.isFinite(value)) return;
    volumes.value[index] = Math.max(0,Math.min(100,Math.round(value)));
  }
  function add(index: number) { enabled.value[index] = true; if (!volumes.value[index]) volumes.value[index] = 30; }
  function remove(index: number) { enabled.value[index] = false; }
  function apply(recipe: Recipe) { volumes.value = [...recipe.volumes]; enabled.value = [...recipe.enabled]; timer.value = recipe.timer; }
  function applyExample() { apply({volumes:[60,35,0,0],enabled:[true,true,false,false],timer:45}); }
  function restoreSaved() {
    try {
      const value = uni.getStorageSync(recipeKey);
      saved.value = value && typeof value === 'object' && Array.isArray(value.volumes) && value.volumes.length === 4 && value.volumes.every((v: unknown) => typeof v === 'number' && Number.isInteger(v) && v >= 0 && v <= 100)
        && Array.isArray(value.enabled) && value.enabled.length === 4 && value.enabled.every((v: unknown) => typeof v === 'boolean') && mixerTimers.includes(value.timer)
        ? {volumes:[...value.volumes],enabled:[...value.enabled],timer:value.timer} : null;
    } catch { saved.value = null; }
  }
  function save() {
    const value = {volumes:[...volumes.value],enabled:[...enabled.value],timer:timer.value};
    try { uni.setStorageSync(recipeKey,value); saved.value = value; return true; } catch { return false; }
  }
  function applySaved() { if (saved.value) apply(saved.value); }
  return { volumes, enabled, timer, saved, rainLabel, audible, setVolume, add, remove, applyExample, restoreSaved, save, applySaved };
});
