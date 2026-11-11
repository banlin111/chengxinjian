<script setup lang="ts">
import PlayerScene from '../../components/PlayerScene.vue';
import { usePlayerTransition } from '../../composables/usePlayerTransition';
import { computed } from 'vue';
import { sampleCourses } from '../../services/home';

import MotionButton from '../../components/MotionButton.vue';
import ScreenFrame from '../../components/ScreenFrame.vue';
import AppIcon from '../../components/AppIcon.vue';
import { useScreenPreview } from '../../composables/useScreenPreview';
const { busy: previewBusy, notice, action } = useScreenPreview(10);
const { state: playerTransition, busy: playerBusy, open: openPlayer, close: closePlayer } = usePlayerTransition(true);
const busy = computed(() => previewBusy.value || playerBusy.value);
function startPractice() {
  if (!busy.value) openPlayer(sampleCourses[0], { from: 'practice', label: '练习打卡', selector: '#practice-player-origin', artwork: false });
}
</script>

<template>
  <!-- #ifdef MP-WEIXIN -->
  <page-meta :page-style="playerTransition.nativeOpen ? 'overflow:hidden' : ''" />
  <page-container v-if="playerTransition.nativeOpen" :show="playerTransition.nativeOpen" :duration="0" :overlay="false" :z-index="1000"
    custom-style="height:0;background:transparent;" @beforeleave="closePlayer" />
  <!-- #endif -->

<ScreenFrame :screen="10" primary gradient>

<view class="u-z-20 u-px-5 screen-content relative" style="padding-top:calc(var(--top) + 0px)">
<view class="u-text-_20px_ u-font-bold">练习 · 打卡</view>

<view class="u-mt-3_5 u-rounded-2xl u-bg-gradient-to-br u-from-jade_40 u-to-pine_30 u-border u-border-jade_40 u-p-5 u-text-center relative u-overflow-hidden">
<view class="u-absolute u--bottom-10 u--left-10 u-w-32 u-h-32 u-rounded-full u-bg-jade_20 u-blur-2xl"></view>
<view class="u-text-_11px_ u-text-sage">连续打卡</view>
<view class="u-mt-1"><text class="u-text-_44px_ u-font-bold u-leading-none">6</text><text class="u-text-_14px_ u-text-sage u-ml-1">天</text></view>
<view class="u-text-_11px_ u-text-gold u-mt-2"><AppIcon asset="fas-fire-e9c08a" class=""></AppIcon> 再坚持 1 天解锁 7 天徽章</view>

<view class="u-flex u-justify-between u-mt-4 u-px-1">
<view class="u-flex u-flex-col u-items-center u-gap-1"><view class="u-w-8 u-h-8 u-rounded-full u-bg-gold u-text-night u-flex u-items-center u-justify-center u-text-_11px_"><AppIcon asset="fas-check-0f1d1f" class=""></AppIcon></view><text class="u-text-_9px_ u-text-sage">一</text></view>
<view class="u-flex u-flex-col u-items-center u-gap-1"><view class="u-w-8 u-h-8 u-rounded-full u-bg-gold u-text-night u-flex u-items-center u-justify-center u-text-_11px_"><AppIcon asset="fas-check-0f1d1f" class=""></AppIcon></view><text class="u-text-_9px_ u-text-sage">二</text></view>
<view class="u-flex u-flex-col u-items-center u-gap-1"><view class="u-w-8 u-h-8 u-rounded-full u-bg-gold u-text-night u-flex u-items-center u-justify-center u-text-_11px_"><AppIcon asset="fas-check-0f1d1f" class=""></AppIcon></view><text class="u-text-_9px_ u-text-sage">三</text></view>
<view class="u-flex u-flex-col u-items-center u-gap-1"><view class="u-w-8 u-h-8 u-rounded-full u-bg-gold u-text-night u-flex u-items-center u-justify-center u-text-_11px_"><AppIcon asset="fas-check-0f1d1f" class=""></AppIcon></view><text class="u-text-_9px_ u-text-sage">四</text></view>
<view class="u-flex u-flex-col u-items-center u-gap-1"><view class="u-w-8 u-h-8 u-rounded-full u-bg-gold u-text-night u-flex u-items-center u-justify-center u-text-_11px_"><AppIcon asset="fas-check-0f1d1f" class=""></AppIcon></view><text class="u-text-_9px_ u-text-sage">五</text></view>
<view class="u-flex u-flex-col u-items-center u-gap-1"><view class="u-w-8 u-h-8 u-rounded-full u-bg-gold u-text-night u-flex u-items-center u-justify-center u-text-_11px_"><AppIcon asset="fas-check-0f1d1f" class=""></AppIcon></view><text class="u-text-_9px_ u-text-sage">六</text></view>
<view class="u-flex u-flex-col u-items-center u-gap-1"><view class="u-w-8 u-h-8 u-rounded-full u-border-2 u-border-dashed u-border-jade u-flex u-items-center u-justify-center u-text-_10px_ u-text-jade">今</view><text class="u-text-_9px_ u-text-jade">日</text></view>
</view>
</view>

<view class="u-mt-5">
<view class="u-flex u-items-end u-justify-between u-mb-2_5">
<view class="u-text-_16px_ u-font-bold">打卡里程碑</view>
<MotionButton @click="notice('打卡奖励', '奖励目录与领取入口已保留，正式资格以后端结果为准。')" aria-label="全部奖励" class="legacy-action action-link u-text-_11px_ u-text-sage ui-action" data-action="全部奖励" role="button" feedback="icon" :busy="busy">全部奖励 <AppIcon asset="fas-chevron-right-a8c5bd" class="u-text-_9px_"></AppIcon></MotionButton>
</view>
<view class="u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-4">
<view class="u-flex u-items-center u-justify-between u-text-_11px_">
<text class="u-text-sage">21 天实物礼盒</text><text class="u-text-gold u-font-semibold">21/21 · 待领取</text>
</view>
<view class="u-mt-2 u-h-2 u-rounded-full u-bg-white_15"><view class="u-h-2 u-rounded-full u-bg-gradient-to-r u-from-gold u-to-__D9A35C_ u-w-full"></view></view>
<view class="u-mt-3 u-flex u-items-center u-gap-3">
<image class="u-w-14 u-h-14 u-rounded-xl u-object-cover" mode="aspectFill" src="/static/prototype/8a960cbf7f31.jpg"/>
<view class="u-flex-1">
<view class="u-text-_12px_ u-font-semibold">静心礼盒 · 颂钵+线香</view>
<view class="u-text-_10px_ u-text-sage u-mt-0_5">领取前需确认收货地址 · 演示</view>
</view>
<MotionButton @click='action("领取")' aria-label="领取" class="legacy-action action-label u-px-3_5 u-py-2 u-rounded-full u-bg-gradient-to-r u-from-gold u-to-__D9A35C_ u-text-night u-text-_11px_ u-font-bold ui-action" data-action="领取" role="button" feedback="icon" :busy="busy">领取</MotionButton>
</view>
</view>

</view>

<view class="u-mt-4 u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3_5 u-flex u-items-center u-justify-between">
<MotionButton @click="notice('练习心得', '录制入口已预留，尚未申请麦克风权限或上传文件。')" aria-label="录制上传练习心得" class="legacy-action u-flex u-items-center u-gap-2_5 ui-action" data-action="录制上传练习心得" role="button" feedback="icon" :busy="busy">
<AppIcon asset="fas-coins-e9c08a" class="u-text-gold u-text-_17px_"></AppIcon>
<view><view class="u-text-_12px_ u-font-semibold">录制上传练习心得</view><view class="u-text-_10px_ u-text-sage u-mt-0_5">审核通过得 200 积分</view></view>
</MotionButton>
<AppIcon asset="fas-chevron-right-a8c5bd" class="u-text-_11px_ u-text-sage_60"></AppIcon>
</view>

<view id="practice-player-origin"><MotionButton @click="startPractice" aria-label="今日打卡 · 完成 21 分钟冥想" class="legacy-action action-label u-mt-4 u-w-full u-py-4 u-rounded-full u-bg-gradient-to-r u-from-jade u-to-__6FCABB_ u-text-_15px_ u-font-bold u-shadow-xl u-shadow-jade_40 ui-action" data-action="今日打卡 · 完成 21 分钟冥想" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-spa-ffffff" class="u-mr-1_5"></AppIcon>今日打卡 · 完成 21 分钟冥想</MotionButton></view>
</view>

</ScreenFrame>
  <PlayerScene v-if="playerTransition.nativeOpen || playerTransition.expansion" :transition-state="playerTransition" @close="closePlayer" />
</template>
