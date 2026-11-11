<script setup lang="ts">
import MotionButton from '../../components/MotionButton.vue';
import ScreenFrame from '../../components/ScreenFrame.vue';
import AppIcon from '../../components/AppIcon.vue';
import { useScreenPreview } from '../../composables/useScreenPreview';
const { busy, action, go } = useScreenPreview(19);
</script>

<template>
<ScreenFrame :screen="19" gradient>

<view class="u-z-20 u-px-5 screen-content relative" style="padding-top:calc(var(--top) + 0px)">
<view class="u-flex u-items-center u-justify-between">
<view class="u-text-_20px_ u-font-bold">我的推广</view>
<MotionButton @click="action('规则说明')" aria-label="规则说明" class="legacy-action action-link u-text-_10px_ u-text-sage ui-action" data-action="规则说明" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-circle-info-a8c5bd" class="u-mr-1"></AppIcon>规则说明</MotionButton>
</view>

<view class="u-mt-3_5 u-rounded-2xl u-bg-gradient-to-br u-from-__1D403C_ u-to-__142B29_ u-border u-border-gold_30 u-p-4 relative u-overflow-hidden">
<view class="u-absolute u--top-10 u--right-10 u-w-32 u-h-32 u-rounded-full u-bg-gold_12 u-blur-2xl"></view>
<MotionButton @click="go(22)" aria-label="累计佣金（元）" class="legacy-action action-link u-text-_11px_ u-text-sage ui-action" data-action="累计佣金（元）" role="button" feedback="icon" :busy="busy">累计佣金（元）</MotionButton>
<view class="u-text-_34px_ u-font-bold u-text-gold u-leading-none u-mt-1_5">1,286<text class="u-text-_18px_">.40</text></view>
<view class="u-flex u-gap-6 u-mt-3_5 u-text-_11px_">
<view><view class="u-text-sage">可提现</view><view class="u-text-mist u-font-bold u-mt-0_5">¥860.20</view></view>
<view><view class="u-text-sage">待结算</view><view class="u-text-mist u-font-bold u-mt-0_5">¥426.20</view></view>
<MotionButton @click="go(22)" aria-label="本月新增" class="legacy-action ui-action" data-action="本月新增" role="button" feedback="icon" :busy="busy"><view class="u-text-sage">本月新增</view><view class="u-text-jade u-font-bold u-mt-0_5">+¥312.00</view></MotionButton>
</view>
<MotionButton @click='action("提现到微信零钱")' aria-label="提现到微信零钱" class="legacy-action action-label u-mt-3_5 u-w-full u-py-2_5 u-rounded-xl u-bg-gradient-to-r u-from-gold u-to-__D9A35C_ u-text-night u-text-_13px_ u-font-bold ui-action" data-action="提现到微信零钱" role="button" feedback="icon" :busy="busy">提现到微信零钱</MotionButton>
</view>

<view class="u-mt-3 u-grid u-grid-cols-3 u-gap-2_5">
<MotionButton @click="go(21, 'level=1')" aria-label="一级好友" class="legacy-action u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3 u-text-center ui-action" data-action="一级好友" role="button" feedback="icon" :busy="busy"><view class="u-text-_18px_ u-font-bold">32</view><view class="u-text-_10px_ u-text-sage u-mt-0_5">一级好友</view></MotionButton>
<MotionButton @click="go(21, 'level=2')" aria-label="二级好友" class="legacy-action u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3 u-text-center ui-action" data-action="二级好友" role="button" feedback="icon" :busy="busy"><view class="u-text-_18px_ u-font-bold">71</view><view class="u-text-_10px_ u-text-sage u-mt-0_5">二级好友</view></MotionButton>
<view class="u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3 u-text-center"><view class="u-text-_18px_ u-font-bold u-text-jade">12</view><view class="u-text-_10px_ u-text-sage u-mt-0_5">本月新增</view></view>
</view>

<view class="u-mt-3 u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3_5">
<view class="u-flex u-items-center u-justify-between">
<view class="u-text-_13px_ u-font-semibold">分佣比例</view>
<text class="u-text-_9px_ u-text-sage_70">示例比例 · 正式规则待确认</text>
</view>
<view class="u-flex u-gap-2_5 u-mt-2_5">
<view class="u-flex-1 u-rounded-xl u-bg-jade_12 u-border u-border-jade_30 u-px-3 u-py-2">
<view class="u-text-_10px_ u-text-sage">一级好友消费</view>
<view class="u-text-_17px_ u-font-bold u-text-jade u-mt-0_5">20%</view>
</view>
<view class="u-flex-1 u-rounded-xl u-bg-white_6 u-border u-border-white_10 u-px-3 u-py-2">
<view class="u-text-_10px_ u-text-sage">二级好友消费</view>
<view class="u-text-_17px_ u-font-bold u-text-mist u-mt-0_5">8%</view>
</view>
<view class="u-flex-1 u-rounded-xl u-bg-white_6 u-border u-border-white_10 u-px-3 u-py-2">
<view class="u-text-_10px_ u-text-sage">结算周期</view>
<view class="u-text-_17px_ u-font-bold u-text-mist u-mt-0_5">T+7</view>
</view>
</view>
<view class="u-text-_9px_ u-text-sage_60 u-mt-2">二级佣金由平台额外承担，不占用一级比例；虚拟商品与积分兑换不计佣</view>
</view>

<view class="u-mt-3 u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3_5 u-flex u-items-center u-justify-between">
<view class="u-text-center u-flex-1"><view class="u-text-_13px_ u-font-bold">46<text class="u-text-_10px_ u-text-sage u-font-normal"> 单</text></view><view class="u-text-_9px_ u-text-sage u-mt-0_5">本月推广订单</view></view>
<text class="u-w-px u-h-8 u-bg-white_10"></text>
<view class="u-text-center u-flex-1"><view class="u-text-_13px_ u-font-bold">¥1,412</view><view class="u-text-_9px_ u-text-sage u-mt-0_5">好友消费总额</view></view>
<text class="u-w-px u-h-8 u-bg-white_10"></text>
<view class="u-text-center u-flex-1"><view class="u-text-_13px_ u-font-bold u-text-gold">22.4%</view><view class="u-text-_9px_ u-text-sage u-mt-0_5">综合佣金率</view></view>
</view>

<view class="u-mt-3 u-grid u-grid-cols-2 u-gap-2_5">
<MotionButton @click="go(20)" aria-label="生成推广海报" class="legacy-action action-label u-rounded-xl u-bg-white_8 u-border u-border-white_10 u-py-3 u-text-_12px_ u-font-semibold ui-action" data-action="生成推广海报" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-qrcode-3fb3a3" class="u-mr-1_5 u-text-jade"></AppIcon>生成推广海报</MotionButton>
<MotionButton @click="go(21)" aria-label="推广人员" class="legacy-action action-label u-rounded-xl u-bg-white_8 u-border u-border-white_10 u-py-3 u-text-_12px_ u-font-semibold ui-action" data-action="推广人员" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-user-group-3fb3a3" class="u-mr-1_5 u-text-jade"></AppIcon>推广人员</MotionButton>
</view>

<view class="u-mt-3 u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3_5">
<view class="u-flex u-items-center u-justify-between">
<view><view class="u-text-_10px_ u-text-sage">我的邀请码</view><view class="u-text-_15px_ u-font-bold u-tracking-_3px_ u-mt-1">JX-8F2K</view></view>
<MotionButton @click='action("复制")' aria-label="复制" class="legacy-action action-label u-px-3_5 u-py-1_5 u-rounded-full u-border u-border-jade u-text-jade u-text-_11px_ ui-action" data-action="复制" role="button" feedback="icon" :busy="busy">复制</MotionButton>
</view>
</view>
<view class="u-text-center u-text-_9px_ u-text-sage_50 u-mt-3">演示关系与金额，不建立真实推广关系</view>
<MotionButton role="button" @click="go(22)" class="legacy-action action-label u-mt-3 u-w-full u-py-3 u-rounded-xl u-border u-border-white_10 u-text-_12px_" feedback="icon" :busy="busy">查看佣金明细 ›</MotionButton></view>

</ScreenFrame>
</template>
