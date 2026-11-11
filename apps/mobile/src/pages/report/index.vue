<script setup lang="ts">
import MotionButton from '../../components/MotionButton.vue';
import ScreenFrame from '../../components/ScreenFrame.vue';
import AppIcon from '../../components/AppIcon.vue';
import { useScreenPreview } from '../../composables/useScreenPreview';
const { busy, notice, action } = useScreenPreview(14);
</script>

<template>
<ScreenFrame :screen="14" gradient>

<view class="u-z-20 u-px-5 screen-content relative" style="padding-top:calc(var(--top) + 0px)">
<view class="u-text-_20px_ u-font-bold">静心报告</view>
<view class="u-text-_11px_ u-text-sage u-mt-1">2026.10.20 — 10.26 · 每周一生成</view>

<view class="u-mt-4 u-rounded-2xl u-bg-gradient-to-br u-from-pine_40 u-to-ocean_30 u-border u-border-jade_30 u-p-4">
<view class="u-flex u-items-end u-justify-between">
<view><view class="u-text-_11px_ u-text-sage">本周总静心</view><view class="u-mt-1"><text class="u-text-_32px_ u-font-bold u-leading-none">5</text><text class="u-text-_14px_"> 小时 </text><text class="u-text-_32px_ u-font-bold u-leading-none">42</text><text class="u-text-_14px_"> 分</text></view></view>
<text class="u-px-2_5 u-py-1 u-rounded-full u-bg-jade_20 u-text-jade u-text-_11px_ u-font-semibold"><AppIcon asset="fas-arrow-trend-up-3fb3a3" class="u-mr-1"></AppIcon>较上周 +18%</text>
</view>

<view class="u-mt-4 u-flex u-items-end u-justify-between u-h-_92px_ u-px-1">
<view class="u-flex u-flex-col u-items-center u-gap-1_5 u-w-8"><view class="u-w-5 u-rounded-t-md u-bg-gradient-to-t u-from-pine u-to-jade" style="height:52px"></view><text class="u-text-_9px_ u-text-sage">一</text></view>
<view class="u-flex u-flex-col u-items-center u-gap-1_5 u-w-8"><view class="u-w-5 u-rounded-t-md u-bg-gradient-to-t u-from-pine u-to-jade" style="height:30px"></view><text class="u-text-_9px_ u-text-sage">二</text></view>
<view class="u-flex u-flex-col u-items-center u-gap-1_5 u-w-8"><view class="u-w-5 u-rounded-t-md u-bg-gradient-to-t u-from-pine u-to-jade" style="height:64px"></view><text class="u-text-_9px_ u-text-sage">三</text></view>
<view class="u-flex u-flex-col u-items-center u-gap-1_5 u-w-8"><view class="u-w-5 u-rounded-t-md u-bg-gradient-to-t u-from-pine u-to-jade" style="height:22px"></view><text class="u-text-_9px_ u-text-sage">四</text></view>
<view class="u-flex u-flex-col u-items-center u-gap-1_5 u-w-8"><view class="u-w-5 u-rounded-t-md u-bg-gradient-to-t u-from-pine u-to-jade" style="height:46px"></view><text class="u-text-_9px_ u-text-sage">五</text></view>
<view class="u-flex u-flex-col u-items-center u-gap-1_5 u-w-8"><view class="u-w-5 u-rounded-t-md u-bg-gradient-to-t u-from-gold u-to-__D9A35C_ relative" style="height:88px"><view class="u-absolute u--top-4 u-left-1_2 u--translate-x-1_2 u-text-_9px_ u-text-gold u-font-bold u-whitespace-nowrap">78m</view></view><text class="u-text-_9px_ u-text-gold">六</text></view>
<view class="u-flex u-flex-col u-items-center u-gap-1_5 u-w-8"><view class="u-w-5 u-rounded-t-md u-bg-white_12" style="height:14px"></view><text class="u-text-_9px_ u-text-sage_60">今</text></view>
</view>
</view>

<view class="u-grid u-grid-cols-3 u-gap-2_5 u-mt-4">
<view class="u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3 u-text-center"><view class="u-text-_16px_ u-font-bold">49<text class="u-text-_10px_ u-text-sage u-font-normal"> 分钟</text></view><view class="u-text-_10px_ u-text-sage u-mt-0_5">日均静心</view></view>
<view class="u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3 u-text-center"><view class="u-text-_16px_ u-font-bold u-text-gold">6<text class="u-text-_10px_ u-text-sage u-font-normal">/7 天</text></view><view class="u-text-_10px_ u-text-sage u-mt-0_5">本周打卡</view></view>
<view class="u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3 u-text-center"><view class="u-text-_16px_ u-font-bold">21:00</view><view class="u-text-_10px_ u-text-sage u-mt-0_5">常练时段</view></view>
</view>

<view class="u-mt-4 u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-4">
<view class="u-flex u-items-center u-gap-2"><AppIcon asset="fas-lightbulb-e9c08a" class="u-text-gold u-text-_14px_"></AppIcon><view class="u-text-_13px_ u-font-semibold">本周洞察</view></view>
<view class="u-text-_12px_ u-text-sage u-mt-2 u-leading-relaxed">你在周六晚间（21:00–22:00）的完成率最高、放弃率最低。建议把「身体扫描」固定安排在这个时段，连续 4 周可解锁<text class="u-text-gold">「静夜者」</text>隐藏徽章。</view>
</view>

<MotionButton @click="notice('静心报告预览', '当前展示示例统计，不会生成或分享真实学习报告。')" aria-label="生成长图 · 分享给涧友" class="legacy-action action-label u-mt-5 u-w-full u-py-3_5 u-rounded-full u-bg-gradient-to-r u-from-jade u-to-pine u-text-_14px_ u-font-bold u-shadow-xl u-shadow-jade_25 ui-action" data-action="生成长图 · 分享给涧友" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-share-nodes-ffffff" class="u-mr-1_5"></AppIcon>生成长图 · 分享给涧友</MotionButton>
</view>

</ScreenFrame>
</template>
