<script setup lang="ts">
import MotionButton from '../../components/MotionButton.vue';
import ScreenFrame from '../../components/ScreenFrame.vue';
import AppIcon from '../../components/AppIcon.vue';
import { useScreenPreview } from '../../composables/useScreenPreview';
const { busy, choose, selected, action } = useScreenPreview(22);
</script>

<template>
<ScreenFrame :screen="22" gradient>

<view class="u-z-20 u-px-5 screen-content relative" style="padding-top:calc(var(--top) + 0px)">
<view class="u-text-_20px_ u-font-bold">佣金明细</view>

<view class="u-mt-3_5 u-rounded-2xl u-bg-white_8 u-border u-border-gold_25 u-p-3_5 u-flex u-items-center u-justify-between">
<view><view class="u-text-_10px_ u-text-sage">可提现金额</view><view class="u-text-_26px_ u-font-bold u-text-gold u-leading-none u-mt-1">860<text class="u-text-_14px_">.20</text></view></view>
<MotionButton @click='action("去提现")' aria-label="去提现" class="legacy-action action-label u-px-5 u-py-2_5 u-rounded-full u-bg-gradient-to-r u-from-gold u-to-__D9A35C_ u-text-night u-text-_12px_ u-font-bold ui-action" data-action="去提现" role="button" feedback="icon" :busy="busy">去提现</MotionButton>
</view>

<view class="u-mt-2_5 u-rounded-xl u-bg-white_6 u-border u-border-white_10 u-px-3_5 u-py-2_5 u-text-_9px_ u-text-sage u-leading-relaxed">
<AppIcon asset="fas-circle-info-3fb3a3" class="u-text-jade u-mr-1"></AppIcon>订单完成 T+7 自动结算为可提现；满 ¥100 可提现，单笔上限 ¥5,000；退款订单佣金同步冲回。
      </view>

<view class="u-flex u-gap-1 u-mt-3_5 u-text-_12px_ u-choice-group">
<MotionButton :class="selected('commission', 0) ? 'choice-active' : 'choice-passive'" @click="choose('commission', 0)" aria-label="全部" class="legacy-action action-tab u-px-3_5 u-py-1_5 u-rounded-full u-bg-jade u-text-night u-font-bold ui-action" data-action="全部" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('commission', 0) ? 'true' : 'false'">全部</MotionButton>
<MotionButton :class="selected('commission', 1) ? 'choice-active' : 'choice-passive'" @click="choose('commission', 1)" aria-label="待结算 3" class="legacy-action action-tab u-px-3_5 u-py-1_5 u-rounded-full u-text-sage u-bg-white_8 ui-action" data-action="待结算 3" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('commission', 1) ? 'true' : 'false'">待结算 3</MotionButton>
<MotionButton :class="selected('commission', 2) ? 'choice-active' : 'choice-passive'" @click="choose('commission', 2)" aria-label="已提现" class="legacy-action action-tab u-px-3_5 u-py-1_5 u-rounded-full u-text-sage u-bg-white_8 ui-action" data-action="已提现" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('commission', 2) ? 'true' : 'false'">已提现</MotionButton>
</view>

<view class="u-mt-3 u-space-y-2_5">
<view class="u-rounded-xl u-bg-white_8 u-border u-border-white_10 u-p-3 u-flex u-items-center u-gap-3" v-show="selected('commission',0)">
<view class="u-w-9 u-h-9 u-rounded-full u-bg-jade_15 u-flex u-items-center u-justify-center"><AppIcon asset="fas-arrow-down-3fb3a3" class="u-text-jade u-text-_13px_"></AppIcon></view>
<view class="u-flex-1 u-min-w-0">
<view class="u-text-_12px_ u-font-semibold u-truncate">一级好友 小满 购买年卡</view>
<view class="u-text-_9px_ u-text-sage u-mt-0_5">10.28 21:14 · 订单 ¥198 × 20% · 已结算</view>
</view>
<view class="u-text-_14px_ u-font-bold u-text-gold">+39.60</view>
</view>
<view class="u-rounded-xl u-bg-white_8 u-border u-border-white_10 u-p-3 u-flex u-items-center u-gap-3" v-show="selected('commission',0)">
<view class="u-w-9 u-h-9 u-rounded-full u-bg-jade_15 u-flex u-items-center u-justify-center"><AppIcon asset="fas-arrow-down-3fb3a3" class="u-text-jade u-text-_13px_"></AppIcon></view>
<view class="u-flex-1 u-min-w-0">
<view class="u-text-_12px_ u-font-semibold u-truncate">二级好友 林深 购买冥想垫</view>
<view class="u-text-_9px_ u-text-sage u-mt-0_5">10.27 09:02 · 订单 ¥151 × 8% · 平台承担</view>
</view>
<view class="u-text-_14px_ u-font-bold u-text-gold">+12.08</view>
</view>
<view class="u-rounded-xl u-bg-white_8 u-border u-border-white_10 u-p-3 u-flex u-items-center u-gap-3 u-opacity-75" v-show="selected('commission',0) || selected('commission',1)">
<view class="u-w-9 u-h-9 u-rounded-full u-bg-white_10 u-flex u-items-center u-justify-center"><AppIcon asset="fas-hourglass-half-a8c5bd" class="u-text-sage u-text-_12px_"></AppIcon></view>
<view class="u-flex-1 u-min-w-0">
<view class="u-text-_12px_ u-font-semibold u-truncate">一级好友 阿泽 购买季卡</view>
<view class="u-text-_9px_ u-text-sage u-mt-0_5">10.30 07:40 · ¥68 × 20% · 11.6 可提现</view>
</view>
<view class="u-text-_14px_ u-font-bold u-text-sage">+13.60</view>
</view>
<view class="u-rounded-xl u-bg-white_8 u-border u-border-__FF4D6A__25 u-p-3 u-flex u-items-center u-gap-3" v-show="selected('commission',0)">
<view class="u-w-9 u-h-9 u-rounded-full u-bg-__FF4D6A__12 u-flex u-items-center u-justify-center"><AppIcon asset="fas-arrow-up-ff4d6a" class="u-text-__FF4D6A_ u-text-_13px_"></AppIcon></view>
<view class="u-flex-1 u-min-w-0">
<view class="u-text-_12px_ u-font-semibold u-truncate">提现到微信零钱</view>
<view class="u-text-_9px_ u-text-sage u-mt-0_5">10.21 15:33 · 处理中 · 预计 24 小时内到账</view>
</view>
<view class="u-text-_14px_ u-font-bold u-text-mist">-500.00</view>
</view>
<view class="u-rounded-xl u-bg-white_8 u-border u-border-white_10 u-p-3 u-flex u-items-center u-gap-3" v-show="selected('commission',0)">
<view class="u-w-9 u-h-9 u-rounded-full u-bg-white_10 u-flex u-items-center u-justify-center"><AppIcon asset="fas-rotate-left-a8c5bd" class="u-text-sage u-text-_12px_"></AppIcon></view>
<view class="u-flex-1 u-min-w-0">
<view class="u-text-_12px_ u-font-semibold u-truncate">订单退款冲回（小满 月卡）</view>
<view class="u-text-_9px_ u-text-sage u-mt-0_5">10.18 11:20 · 原佣金 ¥5.00 已冲回</view>
</view>
<view class="u-text-_14px_ u-font-bold u-text-__FF4D6A_">-5.00</view>
</view>
</view>
<view class="u-text-center u-text-_9px_ u-text-sage_50 u-mt-3">明细保留 12 个月 · 提现需实名认证一致</view>
<view class="u-empty-panel" v-if="selected('commission',2)">暂无已完成提现的示例流水</view></view>

</ScreenFrame>
</template>
