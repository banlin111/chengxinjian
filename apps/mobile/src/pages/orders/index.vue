<script setup lang="ts">
import MotionButton from '../../components/MotionButton.vue';
import { onLoad } from '@dcloudio/uni-app';
import ScreenFrame from '../../components/ScreenFrame.vue';
import AppIcon from '../../components/AppIcon.vue';
import { useScreenPreview } from '../../composables/useScreenPreview';
const { selections, busy, choose, selected, action } = useScreenPreview(15);
onLoad(options => { const mapping: Record<string,number> = {unpaid:1,unshipped:2,shipping:3,after_sale:4}; if (options?.status && mapping[options.status] !== undefined) selections.orders = mapping[options.status]; });
</script>

<template>
<ScreenFrame :screen="15" gradient>

<view class="u-z-20 screen-content relative" style="padding-top:calc(var(--top) + 0px)">
<view class="u-px-6 u-text-_20px_ u-font-bold">我的订单</view>

<view class="u-flex u-gap-1 u-px-5 u-mt-3_5 u-text-_12px_ u-choice-group">
<MotionButton :class="selected('orders', 0) ? 'choice-active' : 'choice-passive'" @click="choose('orders', 0)" aria-label="全部" class="legacy-action action-tab u-px-3 u-py-1_5 u-rounded-full u-text-sage ui-action" data-action="全部" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('orders', 0) ? 'true' : 'false'">全部</MotionButton>
<MotionButton :class="selected('orders', 1) ? 'choice-active' : 'choice-passive'" @click="choose('orders', 1)" aria-label="待付款" class="legacy-action action-tab u-px-3 u-py-1_5 u-rounded-full u-text-sage ui-action" data-action="待付款" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('orders', 1) ? 'true' : 'false'">待付款</MotionButton>
<MotionButton :class="selected('orders', 2) ? 'choice-active' : 'choice-passive'" @click="choose('orders', 2)" aria-label="待发货" class="legacy-action action-tab u-px-3 u-py-1_5 u-rounded-full u-text-sage ui-action" data-action="待发货" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('orders', 2) ? 'true' : 'false'">待发货</MotionButton>
<MotionButton :class="selected('orders', 3) ? 'choice-active' : 'choice-passive'" @click="choose('orders', 3)" aria-label="待收货 2" class="legacy-action action-tab u-px-3_5 u-py-1_5 u-rounded-full u-bg-jade u-font-bold u-text-night ui-action" data-action="待收货 2" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('orders', 3) ? 'true' : 'false'">待收货 2</MotionButton>
<MotionButton :class="selected('orders', 4) ? 'choice-active' : 'choice-passive'" @click="choose('orders', 4)" aria-label="售后" class="legacy-action action-tab u-px-3 u-py-1_5 u-rounded-full u-text-sage ui-action" data-action="售后" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('orders', 4) ? 'true' : 'false'">售后</MotionButton>
</view>

<view class="u-mx-5 u-mt-4 u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3_5" v-show="selected('orders',0) || selected('orders',3)">
<view class="u-flex u-items-center u-justify-between u-text-_10px_ u-text-sage_70"><text>订单号 CD20261030…8412</text><text class="u-text-jade u-font-semibold">运输中</text></view>
<view class="u-flex u-items-center u-gap-3 u-mt-2_5">
<image class="u-w-14 u-h-14 u-rounded-xl u-object-cover" mode="aspectFill" src="/static/prototype/a74a626fe876.jpg"/>
<view class="u-flex-1"><view class="u-text-_13px_ u-font-semibold">天然亚麻冥想垫 · 鼠尾草绿</view><view class="u-text-_10px_ u-text-sage u-mt-0_5">10cm 加厚款</view></view>
<view class="u-text-right"><view class="u-text-_13px_ u-font-bold">¥151</view><view class="u-text-_9px_ u-text-sage_60 u-mt-0_5">会员9折 · 共1件</view></view>
</view>

<view class="u-mt-3 u-rounded-xl u-bg-white_6 u-p-3 u-text-_11px_">
<view class="u-flex u-gap-2_5"><AppIcon asset="fas-circle-3fb3a3" class="u-text-_6px_ u-text-jade u-mt-1_5"></AppIcon><view class="u-flex-1"><view class="u-text-mist">包裹已到达杭州转运中心</view><view class="u-text-_9px_ u-text-sage_60 u-mt-0_5">10.30 08:12</view></view></view>
<view class="u-flex u-gap-2_5 u-mt-2 u-opacity-60"><AppIcon asset="far-circle-a8c5bd" class="u-text-_6px_ u-text-sage u-mt-1_5"></AppIcon><view class="u-flex-1"><view class="u-text-sage">已从上海发出，下一站杭州</view><view class="u-text-_9px_ u-text-sage_60 u-mt-0_5">10.29 21:40</view></view></view>
</view>
<view class="u-flex u-justify-end u-gap-2 u-mt-3">
<MotionButton @click='action("查物流")' aria-label="查物流" class="legacy-action action-label u-px-4 u-py-1_5 u-rounded-full u-border u-border-white_20 u-text-_11px_ u-text-sage ui-action" data-action="查物流" role="button" feedback="icon" :busy="busy">查物流</MotionButton>
<MotionButton @click='action("确认收货")' aria-label="确认收货" class="legacy-action action-label u-px-4 u-py-1_5 u-rounded-full u-bg-jade u-text-night u-text-_11px_ u-font-bold ui-action" data-action="确认收货" role="button" feedback="icon" :busy="busy">确认收货</MotionButton>
</view>
</view>

<view class="u-mx-5 u-mt-3 u-rounded-2xl u-bg-white_8 u-border u-border-gold_30 u-p-3_5" v-show="selected('orders',0) || selected('orders',3)">
<view class="u-flex u-items-center u-justify-between u-text-_10px_"><text class="u-text-sage_70">订单号 CD20261028…2210</text><text class="u-px-2 u-py-0_5 u-rounded-full u-bg-gold_20 u-text-gold u-font-semibold"><AppIcon asset="fas-gift-e9c08a" class="u-mr-1"></AppIcon>打卡奖励</text></view>
<view class="u-flex u-items-center u-gap-3 u-mt-2_5">
<image class="u-w-14 u-h-14 u-rounded-xl u-object-cover" mode="aspectFill" src="/static/prototype/8a960cbf7f31.jpg"/>
<view class="u-flex-1"><view class="u-text-_13px_ u-font-semibold">静心礼盒 · 颂钵+线香</view><view class="u-text-_10px_ u-text-sage u-mt-0_5">21 天打卡达成奖励</view></view>
<view class="u-text-right"><view class="u-text-_13px_ u-font-bold u-text-gold">¥0</view><view class="u-text-_9px_ u-text-sage_60 u-mt-0_5">积分兑付</view></view>
</view>
<view class="u-flex u-justify-end u-gap-2 u-mt-3">
<MotionButton @click='action("查看物流")' aria-label="查看物流" class="legacy-action action-label u-px-4 u-py-1_5 u-rounded-full u-bg-gold u-text-night u-text-_11px_ u-font-bold ui-action" data-action="查看物流" role="button" feedback="icon" :busy="busy">查看物流</MotionButton>
</view>
</view>

<view class="u-mx-5 u-mt-3 u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3_5" v-show="selected('orders',0)">
<view class="u-flex u-items-center u-justify-between u-text-_10px_ u-text-sage_70"><text>订单号 CD20261025…0933</text><text class="u-text-sage">已完成</text></view>
<view class="u-flex u-items-center u-gap-3 u-mt-2_5">
<view class="u-w-14 u-h-14 u-rounded-xl u-bg-gradient-to-br u-from-jade u-to-pine u-flex u-items-center u-justify-center u-text-_18px_"><AppIcon asset="fas-ticket-ffffff" class=""></AppIcon></view>
<view class="u-flex-1"><view class="u-text-_13px_ u-font-semibold">课程通用券 · ¥30</view><view class="u-text-_10px_ u-text-sage u-mt-0_5">虚拟商品 · 无需物流</view></view>
<view class="u-text-right"><view class="u-text-_13px_ u-font-bold">3000 积分</view></view>
</view>
</view>
<view class="u-empty-panel" v-if="selected('orders',1) || selected('orders',2) || selected('orders',4)">暂无该状态的示例订单</view></view>

</ScreenFrame>
</template>
