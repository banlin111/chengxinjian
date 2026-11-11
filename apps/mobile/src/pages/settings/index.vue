<script setup lang="ts">
import { storeToRefs } from 'pinia';
import { nextTick, ref } from 'vue';
import { onReady, onLoad } from '@dcloudio/uni-app';
import ScreenFrame from '../../components/ScreenFrame.vue';
import MotionButton from '../../components/MotionButton.vue';
import AppIcon from '../../components/AppIcon.vue';
import { useScreenPreview } from '../../composables/useScreenPreview';
import { usePreviewPreferences } from '../../stores/preview-preferences';
import { useThemeStore, palettes } from '../../stores/theme';
import { PREVIEW_BUILD, previewDiagnosticsEnabled, copyPreviewDiagnostics } from '../../services/preview-diagnostics';
const { busy, action, notice } = useScreenPreview(18);
const preferences = usePreviewPreferences();
const { speed, switches } = storeToRefs(preferences);
const theme = useThemeStore();
const themeOptions = Object.entries(palettes).map(([id,item])=>({id:id as keyof typeof palettes,name:item.name}));
let security = false;
onLoad(options=>{security=options?.section==='security';});
onReady(async()=>{if(security){await nextTick();uni.pageScrollTo({selector:'#account-security',duration:0});}});
function setSwitch(key:string,event:Event) { switches.value[key]=(event as unknown as {detail:{value:boolean}}).detail.value;preferences.save(); }
function reduceMotion(event:Event) { theme.setReducedMotion((event as unknown as {detail:{value:boolean}}).detail.value); }
const speedMenuOpen = ref(false);
function chooseSpeed() {
  if (busy.value || speedMenuOpen.value) return;
  speedMenuOpen.value = true;
  const values=[.75,1,1.25,1.5,2];
  uni.showActionSheet({complete:()=>{speedMenuOpen.value=false;},itemList:values.map(v=>`${v}x${v===speed.value?' · 当前':''}`),success:result=>{if(values[result.tapIndex]!==undefined){speed.value=values[result.tapIndex];preferences.save();}}});
}
</script>

<template>
<ScreenFrame :screen="18" gradient>

<view class="u-z-20 u-px-5 screen-content relative" style="padding-top:calc(var(--top) + 0px)">
<view class="u-text-_20px_ u-font-bold">设置</view>

<view class="u-text-_11px_ u-text-sage u-mt-5 u-mb-2 u-tracking-wider">通知</view>
<view class="u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-divide-y u-divide-white_8">
<view class="u-flex u-items-center u-gap-3 u-px-4 u-py-3_5"><AppIcon asset="fas-bell-3fb3a3" class="u-text-jade u-w-5"></AppIcon><view class="u-flex-1"><view class="u-text-_13px_">每日练习提醒</view><view class="u-text-_10px_ u-text-sage_70 u-mt-0_5">本机偏好 · 尚未授权通知</view></view><switch aria-label="每日练习提醒" :disabled="busy" :checked="switches.reminder" @change="setSwitch('reminder', $event)" color="#3FB3A3"></switch></view>
<view class="u-flex u-items-center u-gap-3 u-px-4 u-py-3_5"><AppIcon asset="fas-tower-broadcast-3fb3a3" class="u-text-jade u-w-5"></AppIcon><view class="u-flex-1 u-text-_13px_">直播开播提醒</view><switch aria-label="直播开播提醒" :disabled="busy" :checked="switches.live" @change="setSwitch('live', $event)" color="#3FB3A3"></switch></view>
<view class="u-flex u-items-center u-gap-3 u-px-4 u-py-3_5"><AppIcon asset="fas-truck-3fb3a3" class="u-text-jade u-w-5"></AppIcon><view class="u-flex-1 u-text-_13px_">商城物流通知</view><switch aria-label="商城物流通知" :disabled="busy" :checked="switches.logistics" @change="setSwitch('logistics', $event)" color="#3FB3A3"></switch></view>
</view>

<view class="u-text-_11px_ u-text-sage u-mt-5 u-mb-2 u-tracking-wider">播放</view>
<view class="u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-divide-y u-divide-white_8">
<view class="u-flex u-items-center u-gap-3 u-px-4 u-py-3_5"><AppIcon asset="fas-music-3fb3a3" class="u-text-jade u-w-5"></AppIcon><view class="u-flex-1"><view class="u-text-_13px_">结束提示音</view><view class="u-text-_10px_ u-text-sage_70 u-mt-0_5">本机偏好 · 音频能力待接入</view></view><switch aria-label="结束提示音" :disabled="busy" :checked="switches.bell" @change="setSwitch('bell', $event)" color="#3FB3A3"></switch></view>
<MotionButton @click="chooseSpeed" aria-label="默认倍速" class="legacy-action u-flex u-items-center u-gap-3 u-px-4 u-py-3_5 ui-action" data-action="默认倍速" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-gauge-high-3fb3a3" class="u-text-jade u-w-5"></AppIcon><view class="u-flex-1 u-text-_13px_">默认倍速</view><text class="u-text-_11px_ u-text-sage">{{ speed.toFixed(2) }}x</text><AppIcon asset="fas-chevron-right-a8c5bd" class="u-text-_10px_ u-text-sage_50"></AppIcon></MotionButton>
<view class="u-flex u-items-center u-gap-3 u-px-4 u-py-3_5"><AppIcon asset="fas-mobile-screen-3fb3a3" class="u-text-jade u-w-5"></AppIcon><view class="u-flex-1 u-text-_13px_">允许后台播放</view><switch aria-label="允许后台播放" :disabled="busy" :checked="switches.background" @change="setSwitch('background', $event)" color="#3FB3A3"></switch></view>
</view>

<view class="u-text-_11px_ u-text-sage u-mt-5 u-mb-2 u-tracking-wider">通用</view>
<view class="u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-divide-y u-divide-white_8">
<MotionButton @click='action("清除缓存")' aria-label="清除缓存" class="legacy-action u-flex u-items-center u-gap-3 u-px-4 u-py-3_5 ui-action" data-action="清除缓存" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-broom-3fb3a3" class="u-text-jade u-w-5"></AppIcon><view class="u-flex-1 u-text-_13px_">清除缓存</view><text class="u-text-_11px_ u-text-sage">示例缓存</text><AppIcon asset="fas-chevron-right-a8c5bd" class="u-text-_10px_ u-text-sage_50"></AppIcon></MotionButton>
<MotionButton @click='action("给澄心涧评分")' aria-label="给澄心涧评分" class="legacy-action u-flex u-items-center u-gap-3 u-px-4 u-py-3_5 ui-action" data-action="给澄心涧评分" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-star-half-stroke-3fb3a3" class="u-text-jade u-w-5"></AppIcon><view class="u-flex-1 u-text-_13px_">给澄心涧评分</view><AppIcon asset="fas-chevron-right-a8c5bd" class="u-text-_10px_ u-text-sage_50"></AppIcon></MotionButton>
<MotionButton @click='action("关于澄心涧")' aria-label="关于澄心涧" class="legacy-action u-flex u-items-center u-gap-3 u-px-4 u-py-3_5 ui-action" data-action="关于澄心涧" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-circle-info-3fb3a3" class="u-text-jade u-w-5"></AppIcon><view class="u-flex-1 u-text-_13px_">关于澄心涧</view><text class="u-text-_11px_ u-text-sage">v0.1.0</text><AppIcon asset="fas-chevron-right-a8c5bd" class="u-text-_10px_ u-text-sage_50"></AppIcon></MotionButton>
</view>


<view class="settings-extra">
  <text class="settings-section-title">外观与动效</text>
  <view class="theme-options"><MotionButton v-for="item in themeOptions" :key="item.id" class="theme-option" feedback="icon" :aria-label="item.name" :aria-pressed="theme.current === item.id ? 'true' : 'false'" :class="{'theme-selected':theme.current===item.id}" @click="theme.setTheme(item.id)">{{ item.name }}</MotionButton></view>
  <view class="settings-switch-row"><text>减少动态效果</text><switch aria-label="减少动态效果" :checked="theme.reducedMotion" @change="reduceMotion" color="#3FB3A3" /></view>
  <text id="account-security" class="settings-section-title">账号与安全</text>
  <MotionButton class="security-row" feedback="icon" :busy="busy" @click="notice('账号与安全','手机号、密码、隐私与注销入口等待账号服务联调，当前不读取或修改账号信息。')">账号资料与隐私 <AppIcon asset="fas-chevron-right-a8c5bd" /></MotionButton>
</view>
<MotionButton @click='action("退出登录")' aria-label="退出登录" class="legacy-action action-label u-mt-6 u-w-full u-py-3_5 u-rounded-full u-border u-border-__FF4D6A__50 u-text-__FF4D6A_ u-text-_13px_ u-font-semibold ui-action" data-action="退出登录" role="button" feedback="icon" :busy="busy">退出登录</MotionButton>
<view class="u-text-center u-text-_10px_ u-text-sage_50 u-mt-4">退出不会执行删除学习记录或注销账户</view>
<view v-if="previewDiagnosticsEnabled" class="u-mt-4 u-text-center u-text-_10px_ u-text-sage">
  <text>预览版本 {{ PREVIEW_BUILD }}</text>
  <MotionButton class="security-row" feedback="icon" aria-label="复制本机诊断" @click="copyPreviewDiagnostics">复制本机诊断</MotionButton>
</view>
</view>

</ScreenFrame>
</template>

<style scoped>
.settings-extra { margin-top: 22px; }.settings-section-title { display: block; margin: 18px 0 10px; font-size: 13px; color: var(--muted); }
.theme-options { display: flex; gap: 8px; }.theme-option { flex: 1; min-height: 44px; border: 1px solid var(--line); border-radius: 12px; background-image: linear-gradient(var(--surface),var(--surface)); font-size: 12px; }.theme-selected { border-color: var(--accent); color: var(--accent); }
.settings-switch-row,.security-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; min-height: 60px; padding: 12px; margin-top: 10px; border: 1px solid var(--line); border-radius: 14px; background-image: linear-gradient(var(--surface),var(--surface)); font-size: 13px; }.security-row { width: 100%; text-align: left; }
</style>
