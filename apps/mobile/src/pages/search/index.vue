<script setup lang="ts">
import { computed, ref } from 'vue';
import { onHide, onLoad, onReady } from '@dcloudio/uni-app';
import ScreenFrame from '../../components/ScreenFrame.vue';
import MotionButton from '../../components/MotionButton.vue';
import AppIcon from '../../components/AppIcon.vue';
import { backScreen } from '../../config/screens';
import { usePreviewNavigation } from '../../composables/usePreviewNavigation';
import { hotSearches, suggestedSearches, searchPreview, readSearchHistory, writeSearchHistory, addSearchHistory } from '../../services/search-preview';

const { navigating, navigate } = usePreviewNavigation();
const query = ref('');
const submitted = ref('');
const focused = ref(false);
const searchInput = ref<{ $el?: HTMLElement } | null>(null);
const history = ref<string[]>([]);
const results = computed(() => searchPreview(submitted.value));
const groups = computed(() => [
  { key: 'course', label: '课程', item: results.value.find(item => item.kind === 'course') },
  { key: 'meditation', label: '冥想', item: results.value.find(item => item.kind === 'meditation') },
].filter(group => group.item));
onLoad(() => { history.value = readSearchHistory(); });
onReady(() => {
  // uni-input keeps aria-label on its H5 wrapper; name the actual editable field.
  // #ifdef H5
  searchInput.value?.$el?.querySelector('input')?.setAttribute('aria-label', '搜索关键词');
  // #endif
});
onHide(() => { focused.value = false; });
function runSearch(term?: string) {
  if (navigating.value) return;
  let current = term ?? query.value;
  // H5 uni-input throttles v-model updates; submit the latest visible value.
  // #ifdef H5
  if (term === undefined) current = searchInput.value?.$el?.querySelector('input')?.value ?? current;
  // #endif
  const value = current.replace(/\s+/g, ' ').trim().slice(0,80);
  query.value = value;
  submitted.value = value;
  if (!value) return;
  history.value = addSearchHistory(history.value, value);
  writeSearchHistory(history.value);
  focused.value = false;
  uni.hideKeyboard();
}
function clearQuery() { query.value = ''; submitted.value = ''; focused.value = true; }
function clearHistory() { history.value = []; writeSearchHistory(history.value); }
function removeHistory(item: string) { history.value = history.value.filter(value => value !== item); writeSearchHistory(history.value); }
function cancel() { focused.value = false; uni.hideKeyboard(); backScreen(); }
</script>

<template>
  <ScreenFrame :screen="13" gradient>
    <view class="search-content screen-content">
      <view class="search-toolbar">
        <view class="search-field" :class="{ 'search-focused': focused }">
          <view class="search-symbol"><AppIcon asset="fas-magnifying-glass-a8c5bd" /></view>
          <input ref="searchInput" v-model="query" class="search-input" :focus="focused" :maxlength="80" :disabled="navigating"
            placeholder="搜索课程 / 导师 / 专题" placeholder-style="color:#A8C5BD" aria-label="搜索关键词"
            confirm-type="search" :cursor-spacing="24" @focus="focused = true" @blur="focused = false" @confirm="runSearch($event.detail.value)" />
          <MotionButton v-if="query" class="search-clear" feedback="icon" aria-label="清除输入" :busy="navigating" @click="clearQuery">×</MotionButton>
          <MotionButton class="search-submit" feedback="icon" aria-label="搜索" :busy="navigating" @click="runSearch()">搜索</MotionButton>
        </view>
        <MotionButton class="search-cancel" feedback="icon" aria-label="取消" @click="cancel">取消</MotionButton>
      </view>

      <view v-if="submitted" class="search-results" aria-live="polite">
        <text class="search-results-title">“{{ submitted }}”的示例结果</text>
        <text class="search-note">仅搜索本地示例，每类展示一条；真实搜索待接入。</text>
        <view v-if="!groups.length" class="search-empty-state">
          <view class="empty-symbol"><AppIcon asset="fas-magnifying-glass-a8c5bd" /></view>
          <text class="empty-title">暂无匹配的示例内容</text>
          <text class="empty-description">试试“身体扫描”或“颂钵音疗”。</text>
          <MotionButton class="search-reset" feedback="icon" @click="clearQuery">换个关键词</MotionButton>
        </view>
        <view v-for="group in groups" :key="group.key" class="result-group">
          <text class="search-section-title">{{ group.label }}</text>
          <MotionButton v-if="group.item" class="search-result-card" feedback="icon" :busy="navigating"
            :aria-label="group.item.title" @click="navigate(group.item.url)">
            <image class="result-cover" :src="group.item.coverUrl" mode="aspectFill" />
            <view class="result-copy"><text class="result-title">{{ group.item.title }}</text><text class="result-meta">{{ group.item.subtitle }}</text></view>
            <image class="result-chevron" src="/static/ui/chevron.svg" />
          </MotionButton>
        </view>
      </view>

      <view v-else class="search-discover">
        <view class="search-section">
          <view class="search-section-heading"><text class="search-section-title">热搜榜</text><view class="search-section-note"><AppIcon asset="fas-arrow-trend-up-e9c08a" /><text>示例热词</text></view></view>
          <view class="hot-searches">
            <MotionButton v-for="(term, index) in hotSearches" :key="term" class="hot-search" feedback="icon" :aria-label="term" :busy="navigating" @click="runSearch(term)">
              <view class="hot-pill"><text v-if="index < 3" class="hot-rank" :class="{ 'rank-third': index === 2 }">{{ index + 1 }}</text><text>{{ term }}</text></view>
            </MotionButton>
          </view>
        </view>
        <view class="search-section history-section">
          <view class="search-section-heading">
            <text class="search-section-title">搜索历史</text>
            <MotionButton class="history-clear" feedback="icon" aria-label="清空搜索历史" data-action="清空搜索历史" :busy="navigating || !history.length" @click="clearHistory"><AppIcon asset="fas-trash-can-a8c5bd" /></MotionButton>
          </view>
          <view v-if="history.length" class="search-history-list">
            <view v-for="item in history" :key="item" class="search-history-row">
              <MotionButton class="history-term" feedback="icon" :busy="navigating" :aria-label="`搜索历史：${item}`" @click="runSearch(item)"><view class="history-clock"><AppIcon asset="far-clock-a8c5bd" /></view><text class="history-label">{{ item }}</text></MotionButton>
              <MotionButton class="history-delete" feedback="icon" :busy="navigating" :aria-label="`删除历史：${item}`" @click="removeHistory(item)">×</MotionButton>
            </view>
          </view>
          <text v-else class="history-empty">暂无搜索历史</text>
        </view>
        <view class="search-section suggestion-section">
          <view class="search-section-heading"><text class="search-section-title">猜你想搜</text></view>
          <view class="search-suggestions">
            <MotionButton v-for="item in suggestedSearches" :key="item.label" class="search-suggestion" feedback="icon" :aria-label="item.label" :busy="navigating" @click="runSearch(item.label)"><AppIcon :asset="item.icon" /><text>{{ item.label }}</text></MotionButton>
          </view>
        </view>
      </view>
    </view>
  </ScreenFrame>
</template>

<style scoped>
.search-content { padding: var(--top) 20px 28px; color: var(--text); }
.search-toolbar { display: flex; align-items: center; gap: 8px; }
.search-field { display: flex; align-items: center; flex: 1; min-width: 0; height: 46px; padding-left: 13px; border: 1px solid rgba(63,179,163,.5); border-radius: 25px; background: rgba(255,255,255,.08); transition: border-color 160ms ease; }
.search-focused { border-color: var(--accent); }
.search-symbol { display: flex; align-items: center; margin-right: 7px; color: var(--accent); font-size: 14px; }
.search-input { display: block; flex: 1; min-width: 0; width: 0; height: 44px; padding: 0; color: var(--text); font-size: 12px; line-height: 44px; background: transparent; }
.search-clear { display: flex; align-items: center; justify-content: center; width: 32px; height: 44px; flex-shrink: 0; color: var(--muted); font-size: 22px; }
.search-submit { display: flex; align-items: center; justify-content: center; width: 44px; height: 44px; margin-right: 3px; flex-shrink: 0; color: var(--accent); font-size: 12px; font-weight: 600; }
.search-cancel { display: flex; align-items: center; justify-content: flex-end; min-width: 40px; min-height: 44px; color: var(--muted); font-size: 13px; }
.search-section { margin-top: 20px; }
.search-section-heading { display: flex; align-items: center; justify-content: space-between; min-height: 44px; gap: 12px; }
.search-section-title { display: block; color: var(--text); font-size: 17px; font-weight: 700; line-height: 26px; }
.search-section-note { display: flex; align-items: center; gap: 5px; color: var(--muted); font-size: 10px; line-height: 16px; }
.hot-searches { display: flex; flex-wrap: wrap; gap: 2px 8px; margin-top: 2px; }
.hot-search { display: flex; align-items: center; min-height: 44px; }
.hot-pill { display: flex; align-items: center; gap: 5px; padding: 7px 13px; border: 1px solid rgba(255,255,255,.09); border-radius: 22px; background: rgba(255,255,255,.08); color: var(--text); font-size: 12px; line-height: 18px; }
.hot-rank { color: #E9C08A; font-weight: 700; }.rank-third { color: #3FB3A3; }
.history-clear { display: flex; align-items: center; justify-content: flex-end; min-width: 44px; min-height: 44px; color: var(--muted); font-size: 13px; }
.search-history-list { display: flex; flex-direction: column; gap: 8px; margin-top: 4px; }
.search-history-row { display: flex; align-items: center; min-height: 46px; border: 1px solid rgba(255,255,255,.1); border-radius: 13px; background: rgba(255,255,255,.05); }
.history-term { display: flex; align-items: center; gap: 9px; flex: 1; min-width: 0; min-height: 44px; padding-left: 13px; color: var(--muted); font-size: 12px; text-align: left; }
.history-clock { font-size: 12px; flex-shrink: 0; }
.history-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.history-delete { display: flex; align-items: center; justify-content: center; width: 44px; height: 44px; flex-shrink: 0; color: var(--muted); font-size: 19px; }
.history-empty { display: block; padding: 13px 0; color: var(--muted); font-size: 12px; line-height: 20px; }
.search-suggestions { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10px; margin-top: 4px; }
.search-suggestion { display: flex; align-items: center; gap: 9px; min-height: 54px; padding: 10px 12px; border: 1px solid rgba(255,255,255,.12); border-radius: 13px; background-image: linear-gradient(rgba(255,255,255,.06),rgba(255,255,255,.06)); color: var(--text); font-size: 12px; line-height: 19px; text-align: left; }
.search-suggestion text { min-width: 0; }
.search-results { margin-top: 26px; }
.search-results-title { display: block; overflow-wrap: anywhere; font-size: 18px; font-weight: 600; line-height: 29px; }
.search-note { display: block; margin-top: 7px; color: var(--muted); font-size: 11px; line-height: 19px; }
.result-group { margin-top: 24px; }
.search-result-card { display: flex; align-items: center; gap: 12px; width: 100%; min-height: 96px; margin-top: 12px; padding: 12px; text-align: left; border: 1px solid rgba(255,255,255,.12); border-radius: 16px; background-image: linear-gradient(rgba(255,255,255,.06),rgba(255,255,255,.06)); }
.result-cover { flex-shrink: 0; width: 64px; height: 68px; border-radius: 11px; }.result-copy { min-width: 0; flex: 1; }
.result-title { display: block; font-size: 14px; font-weight: 600; line-height: 22px; }.result-meta { display: block; margin-top: 6px; color: var(--muted); font-size: 11px; line-height: 18px; }
.result-chevron { flex-shrink: 0; width: 10px; height: 10px; }
.search-empty-state { padding: 50px 12px; text-align: center; }.empty-symbol { margin-bottom: 20px; color: var(--muted); font-size: 30px; }
.empty-title { display: block; font-size: 16px; font-weight: 600; line-height: 26px; }.empty-description { display: block; margin-top: 8px; color: var(--muted); font-size: 12px; line-height: 20px; }
.search-reset { display: inline-flex; align-items: center; justify-content: center; min-height: 44px; margin-top: 20px; padding: 10px 18px; border: 1px solid var(--accent); border-radius: 24px; color: var(--accent); font-size: 13px; }
@media (max-width: 350px) { .search-content { padding-left: 16px; padding-right: 16px; }.search-field { padding-left: 10px; }.search-symbol { margin-right: 5px; }.hot-searches { column-gap: 6px; }.hot-pill { padding: 7px 11px; }.search-suggestion { gap: 7px; padding: 10px; font-size: 11px; } }
@media (prefers-reduced-motion: reduce) { .search-field { transition: none; } }
</style>
<style>
.pdf-screen[data-screen="13"] { min-height: 0; padding-bottom: 0; }
</style>
