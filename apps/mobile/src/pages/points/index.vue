<script setup lang="ts">
import MotionButton from '../../components/MotionButton.vue';
import ScreenFrame from '../../components/ScreenFrame.vue';
import AppIcon from '../../components/AppIcon.vue';
import { useScreenPreview } from '../../composables/useScreenPreview';
const { busy, notice, action, go } = useScreenPreview(16);
</script>

<template>
<ScreenFrame :screen="16" gradient>

<view class="u-z-20 u-px-5 screen-content relative" style="padding-top:calc(var(--top) + 0px)">
<view class="u-text-_20px_ u-font-bold">积分中心</view>

<view class="u-mt-3_5 u-rounded-2xl u-bg-gradient-to-br u-from-__1D403C_ u-to-__142B29_ u-border u-border-gold_25 u-p-3_5">
<view class="u-flex u-items-center u-justify-between">
<view><view class="u-text-_11px_ u-text-sage">当前积分</view><view class="u-text-_28px_ u-font-bold u-text-gold u-leading-none u-mt-1">3,240</view></view>
<view class="u-text-right u-text-_10px_ u-text-sage"><view>今日已得 <text class="u-text-jade u-font-bold">+120</text></view><view class="u-mt-1">明日打卡可得 <text class="u-text-gold u-font-bold">+200</text></view></view>
</view>
<view class="u-text-_9px_ u-text-sage_60 u-mt-2_5">100 积分 = ¥1 · 结算时可勾选抵现，会员叠加 9 折</view>
</view>

<view class="u-mt-4 u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3">
<view class="u-flex u-items-center u-justify-between u-mb-2"><view class="u-text-_13px_ u-font-semibold">每日签到</view><text class="u-text-_10px_ u-text-sage">本月已签 22 天</text></view>
<view class="u-flex u-justify-between">
<view class="u-flex u-flex-col u-items-center u-gap-1"><view class="u-w-7 u-h-7 u-rounded-full u-bg-jade_25 u-text-jade u-flex u-items-center u-justify-center u-text-_10px_"><AppIcon asset="fas-check-3fb3a3" class=""></AppIcon></view></view>
<view class="u-flex u-flex-col u-items-center u-gap-1"><view class="u-w-7 u-h-7 u-rounded-full u-bg-jade_25 u-text-jade u-flex u-items-center u-justify-center u-text-_10px_"><AppIcon asset="fas-check-3fb3a3" class=""></AppIcon></view></view>
<view class="u-flex u-flex-col u-items-center u-gap-1"><view class="u-w-7 u-h-7 u-rounded-full u-bg-jade_25 u-text-jade u-flex u-items-center u-justify-center u-text-_10px_"><AppIcon asset="fas-check-3fb3a3" class=""></AppIcon></view></view>
<view class="u-flex u-flex-col u-items-center u-gap-1"><MotionButton @click="notice('签到演示', '当前只展示签到样式，没有创建真实签到记录。')" aria-label="今" class="legacy-action u-w-7 u-h-7 u-rounded-full u-bg-gold u-text-night u-flex u-items-center u-justify-center u-text-_10px_ u-font-bold u-shadow-lg u-shadow-gold_30 ui-action" data-action="今" role="button" feedback="icon" :busy="busy">今</MotionButton></view>
<view class="u-flex u-flex-col u-items-center u-gap-1"><view class="u-w-7 u-h-7 u-rounded-full u-bg-white_12 u-text-sage u-flex u-items-center u-justify-center u-text-_10px_">四</view></view>
<view class="u-flex u-flex-col u-items-center u-gap-1"><view class="u-w-7 u-h-7 u-rounded-full u-bg-white_12 u-text-sage u-flex u-items-center u-justify-center u-text-_10px_">五</view></view>
<view class="u-flex u-flex-col u-items-center u-gap-1"><view class="u-w-7 u-h-7 u-rounded-full u-bg-white_12 u-text-sage u-flex u-items-center u-justify-center u-text-_10px_">六</view></view>
</view>
</view>

<view class="u-text-_16px_ u-font-bold u-mt-3_5 u-mb-2">做任务赚积分</view>
<view class="u-space-y-2">
<view class="u-flex u-items-center u-gap-3 u-rounded-xl u-bg-white_8 u-border u-border-white_10 u-px-3_5 u-py-2 u-opacity-60">
<AppIcon asset="fas-headphones-a8c5bd" class="u-text-_16px_ u-text-sage"></AppIcon>
<view class="u-flex-1"><view class="u-text-_12px_ u-font-semibold">完成 1 次冥想</view><view class="u-text-_10px_ u-text-sage_70 u-mt-0_5">+50 积分</view></view>
<text class="u-px-3 u-py-1 u-rounded-full u-bg-white_12 u-text-_10px_ u-text-sage">已领取</text>
</view>
<view class="u-flex u-items-center u-gap-3 u-rounded-xl u-bg-white_8 u-border u-border-gold_40 u-px-3_5 u-py-2">
<AppIcon asset="fas-microphone-e9c08a" class="u-text-_16px_ u-text-gold"></AppIcon>
<view class="u-flex-1"><view class="u-text-_12px_ u-font-semibold">录制上传练习心得</view><view class="u-text-_10px_ u-text-sage u-mt-0_5">+200 积分 · 审核通过后发放</view></view>
<MotionButton @click="notice('积分领取演示', '不会增加真实积分，任务状态将在后端联调时同步。')" aria-label="可领取" class="legacy-action action-label u-px-3 u-py-1 u-rounded-full u-bg-gold u-text-night u-text-_10px_ u-font-bold ui-action" data-action="可领取" role="button" feedback="icon" :busy="busy">可领取</MotionButton>
</view>
<view class="u-flex u-items-center u-gap-3 u-rounded-xl u-bg-white_8 u-border u-border-white_10 u-px-3_5 u-py-2">
<AppIcon asset="fas-share-nodes-3fb3a3" class="u-text-_16px_ u-text-jade"></AppIcon>
<view class="u-flex-1"><view class="u-text-_12px_ u-font-semibold">分享课程给好友</view><view class="u-text-_10px_ u-text-sage u-mt-0_5">+30 积分 · 每日上限 3 次</view></view>
<MotionButton @click="go(3)" aria-label="去完成" class="legacy-action action-label u-px-3 u-py-1 u-rounded-full u-border u-border-jade u-text-jade u-text-_10px_ ui-action" data-action="去完成" role="button" feedback="icon" :busy="busy">去完成</MotionButton>
</view>
</view>

<view class="u-text-_16px_ u-font-bold u-mt-3_5 u-mb-2">积分兑换</view>
<view class="u-flex u-gap-3 u-overflow-x-auto u-no-scrollbar u-pb-1">
<MotionButton @click="notice('积分兑换演示')" aria-label="¥30 课程券" class="legacy-action u-w-_120px_ u-shrink-0 u-rounded-xl u-bg-white_8 u-border u-border-white_10 u-p-2 ui-action" data-action="¥30 课程券" role="button" feedback="icon" :busy="busy">
<view class="u-w-full u-h-_52px_ u-rounded-lg u-bg-gradient-to-br u-from-jade u-to-pine u-flex u-items-center u-justify-center u-text-_20px_"><AppIcon asset="fas-ticket-ffffff" class=""></AppIcon></view>
<view class="u-text-_11px_ u-font-semibold u-mt-1_5">¥30 课程券</view>
<view class="u-text-_11px_ u-text-gold u-font-bold u-mt-0_5">3000 分</view>
</MotionButton>


</view>
</view>

</ScreenFrame>
</template>
