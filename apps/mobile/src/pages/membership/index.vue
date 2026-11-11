<script setup lang="ts">
import MotionButton from '../../components/MotionButton.vue';
import ScreenFrame from '../../components/ScreenFrame.vue';
import AppIcon from '../../components/AppIcon.vue';
import { useScreenPreview } from '../../composables/useScreenPreview';
const { busy, selections, choose, selected, notice, action } = useScreenPreview(9);
</script>

<template>
<ScreenFrame :screen="9" gradient>

<view class="u-z-20 u-px-5 screen-content relative" style="padding-top:calc(var(--top) + 0px)">
<view class="u-text-_20px_ u-font-bold">涧心会员</view>

<view class="u-mt-3_5 u-rounded-2xl u-bg-gradient-to-br u-from-__1D403C_ u-to-__142B29_ u-border u-border-gold_30 u-p-4 relative u-overflow-hidden">
<view class="u-absolute u--top-8 u--right-8 u-w-28 u-h-28 u-rounded-full u-bg-gold_15 u-blur-xl"></view>
<view class="u-flex u-items-center u-gap-3">
<image class="u-w-12 u-h-12 u-rounded-full u-object-cover u-border-2 u-border-gold_50" mode="aspectFill" src="/static/prototype/80c4bc773c7d.jpg"/>
<view class="u-flex-1">
<view class="u-text-_14px_ u-font-bold">苏澜 <text class="u-ml-1 u-px-2 u-py-0_5 u-rounded-full u-bg-gradient-to-r u-from-gold u-to-__D9A35C_ u-text-night u-text-_9px_ u-font-bold"><AppIcon asset="fas-crown-0f1d1f" class="u-mr-0_5"></AppIcon>年卡会员</text></view>
<view class="u-text-_10px_ u-text-sage u-mt-1">示例：2026.11.18 到期 · 已省 ¥486</view>
</view>
</view>
<view class="u-mt-3 u-h-1 u-rounded-full u-bg-white_15"><view class="u-h-1 u-rounded-full u-bg-gradient-to-r u-from-gold u-to-__D9A35C_ u-w-_72__"></view></view>
<view class="u-text-_9px_ u-text-sage u-mt-1_5">会员期剩余 72%，续费享 8 折</view>
</view>

<view class="u-text-_15px_ u-font-bold u-mt-5 u-mb-2_5">续费套餐</view>
<view class="u-grid u-grid-cols-4 u-gap-2 u-choice-group">
<MotionButton :class="selected('plans', 0) ? 'choice-active' : 'choice-passive'" @click="choose('plans', 0)" aria-label="月卡 ¥25 ¥0.83/天" class="legacy-action u-rounded-xl u-bg-white_8 u-border u-border-white_15 u-py-3 u-text-center ui-action" data-action="月卡 ¥25 ¥0.83/天" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('plans', 0) ? 'true' : 'false'">
<view class="u-text-_12px_ u-font-semibold">月卡</view>
<view class="u-text-_14px_ u-font-bold u-mt-1">¥25</view>
<view class="u-text-_9px_ u-text-sage u-mt-0_5">¥0.83/天</view>
</MotionButton>
<MotionButton :class="selected('plans', 1) ? 'choice-active' : 'choice-passive'" @click="choose('plans', 1)" aria-label="季卡 ¥68 ¥0.75/天" class="legacy-action u-rounded-xl u-bg-white_8 u-border u-border-white_15 u-py-3 u-text-center ui-action" data-action="季卡 ¥68 ¥0.75/天" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('plans', 1) ? 'true' : 'false'">
<view class="u-text-_12px_ u-font-semibold">季卡</view>
<view class="u-text-_14px_ u-font-bold u-mt-1">¥68</view>
<view class="u-text-_9px_ u-text-sage u-mt-0_5">¥0.75/天</view>
</MotionButton>
<MotionButton :class="selected('plans', 2) ? 'choice-active' : 'choice-passive'" @click="choose('plans', 2)" aria-label="最受欢迎 年卡 ¥198 ¥0.54/天" class="legacy-action u-rounded-xl u-bg-jade u-border u-border-jade u-py-3 u-text-center relative u-shadow-lg u-shadow-jade_30 ui-action" data-action="最受欢迎 年卡 ¥198 ¥0.54/天" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('plans', 2) ? 'true' : 'false'">
<view class="u-absolute u--top-2 u-left-1_2 u--translate-x-1_2 u-px-2 u-py-0_5 u-rounded-full u-bg-gold u-text-night u-text-_8px_ u-font-bold u-whitespace-nowrap">最受欢迎</view>
<view class="u-text-_12px_ u-font-semibold">年卡</view>
<view class="u-text-_14px_ u-font-bold u-mt-1">¥198</view>
<view class="u-text-_9px_ u-text-mist_80 u-mt-0_5">¥0.54/天</view>
</MotionButton>
<MotionButton :class="selected('plans', 3) ? 'choice-active' : 'choice-passive'" @click="choose('plans', 3)" aria-label="连续包年 ¥168 仅演示" class="legacy-action u-rounded-xl u-bg-white_8 u-border u-border-white_15 u-py-3 u-text-center ui-action" data-action="连续包年 ¥168 仅演示" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('plans', 3) ? 'true' : 'false'">
<view class="u-text-_12px_ u-font-semibold">连续包年</view>
<view class="u-text-_14px_ u-font-bold u-mt-1">¥168</view>
<view class="u-text-_9px_ u-text-sage u-mt-0_5">仅演示</view>
</MotionButton>
</view>
<MotionButton @click="notice('会员续费演示', ['月卡','季卡','年卡','连续包年（占位）'][selections.plans] + ' · 示例 ¥' + [25,68,198,168][selections.plans] + '。当前仅选择套餐，不会发起支付或开通会员。')" :aria-label="`立即续费 ¥${[25,68,198,168][selections.plans]}`" class="legacy-action action-label u-mt-4 u-w-full u-py-3_5 u-rounded-full u-bg-gradient-to-r u-from-gold u-to-__D9A35C_ u-text-night u-text-_14px_ u-font-bold u-shadow-xl u-shadow-gold_20 ui-action" data-action="立即续费 ¥198" role="button" feedback="icon" :busy="busy">立即续费 ¥{{ [25,68,198,168][selections.plans] }}</MotionButton>

<view class="u-text-_15px_ u-font-bold u-mt-6 u-mb-2_5">专属权益</view>
<view class="u-grid u-grid-cols-4 u-gap-y-4 u-text-center">
<view><AppIcon asset="fas-infinity-e9c08a" class="u-text-_19px_ u-text-gold"></AppIcon><view class="u-text-_10px_ u-mt-1_5 u-text-sage">全场课程</view></view>
<view><AppIcon asset="fas-tags-e9c08a" class="u-text-_19px_ u-text-gold"></AppIcon><view class="u-text-_10px_ u-mt-1_5 u-text-sage">商城 9 折</view></view>
<view><AppIcon asset="fas-coins-e9c08a" class="u-text-_19px_ u-text-gold"></AppIcon><view class="u-text-_10px_ u-mt-1_5 u-text-sage">积分 1.5x</view></view>
<view><AppIcon asset="fas-bell-concierge-e9c08a" class="u-text-_19px_ u-text-gold"></AppIcon><view class="u-text-_10px_ u-mt-1_5 u-text-sage">直播优先连麦</view></view>
<view><AppIcon asset="fas-download-e9c08a" class="u-text-_19px_ u-text-gold"></AppIcon><view class="u-text-_10px_ u-mt-1_5 u-text-sage">离线下载</view></view>
<view><AppIcon asset="fas-gift-e9c08a" class="u-text-_19px_ u-text-gold"></AppIcon><view class="u-text-_10px_ u-mt-1_5 u-text-sage">生日礼盒</view></view>
<view><AppIcon asset="fas-user-plus-e9c08a" class="u-text-_19px_ u-text-gold"></AppIcon><view class="u-text-_10px_ u-mt-1_5 u-text-sage">新人体验券</view></view>
<view><AppIcon asset="fas-circle-question-a8c5bd" class="u-text-_19px_ u-text-sage_50"></AppIcon><view class="u-text-_10px_ u-mt-1_5 u-text-sage_60">更多</view></view>
</view>
</view>

</ScreenFrame>
</template>
