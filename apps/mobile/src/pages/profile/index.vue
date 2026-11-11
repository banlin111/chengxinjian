<script setup lang="ts">
import MotionButton from '../../components/MotionButton.vue';
import ScreenFrame from '../../components/ScreenFrame.vue';
import AppIcon from '../../components/AppIcon.vue';
import { useScreenPreview } from '../../composables/useScreenPreview';
const { busy, notice, action, go } = useScreenPreview(12);
</script>

<template>
<ScreenFrame :screen="12" primary gradient>

<view class="u-z-20 screen-content relative" style="padding-top:calc(var(--top) + 0px)">

<MotionButton class="legacy-action u-w-full u-px-5 u-flex u-items-center u-gap-3_5 profile-header" aria-label="个人资料与安全" feedback="icon" :busy="busy" @click="go(18, 'section=security')">
<image class="u-w-16 u-h-16 u-rounded-full u-object-cover u-border-2 u-border-gold_50" mode="aspectFill" src="/static/prototype/80c4bc773c7d.jpg"/>
<view class="u-flex-1">
<view class="u-text-_18px_ u-font-bold">苏澜（演示） <text class="u-ml-1 u-px-2 u-py-0_5 u-rounded-full u-bg-gradient-to-r u-from-gold u-to-__D9A35C_ u-text-night u-text-_9px_ u-font-bold u-align-middle"><AppIcon asset="fas-crown-0f1d1f" class="u-mr-0_5"></AppIcon>年卡</text></view>
<view class="u-text-_11px_ u-text-sage u-mt-1">涧号：静水流深 · 打卡 128 天</view>
</view>
<AppIcon asset="fas-chevron-right-a8c5bd" class="u-text-sage_60 u-text-_12px_"></AppIcon>
</MotionButton>

<view class="u-mx-5 u-mt-4 u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-flex u-divide-x u-divide-white_10">
<MotionButton @click="go(16)" aria-label="积分" class="legacy-action u-flex-1 u-py-3_5 u-text-center ui-action" data-action="积分" role="button" feedback="icon" :busy="busy"><view class="u-text-_17px_ u-font-bold u-text-gold">3,240</view><view class="u-text-_10px_ u-text-sage u-mt-0_5">积分</view></MotionButton>
<MotionButton @click="notice('我的优惠券', '暂无真实卡券，原型数量仅供演示。')" aria-label="优惠券" class="legacy-action u-flex-1 u-py-3_5 u-text-center ui-action" data-action="优惠券" role="button" feedback="icon" :busy="busy"><view class="u-text-_17px_ u-font-bold">5</view><view class="u-text-_10px_ u-text-sage u-mt-0_5">优惠券</view></MotionButton>
<MotionButton @click="notice('我的收藏', '收藏列表入口已保留，当前尚未接入账户收藏记录。')" aria-label="收藏" class="legacy-action u-flex-1 u-py-3_5 u-text-center ui-action" data-action="收藏" role="button" feedback="icon" :busy="busy"><view class="u-text-_17px_ u-font-bold">36</view><view class="u-text-_10px_ u-text-sage u-mt-0_5">收藏</view></MotionButton>
</view>

<view class="u-mx-5 u-mt-4 u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3_5">
<view class="u-flex u-items-center u-justify-between u-mb-3">
<view class="u-text-_13px_ u-font-semibold">我的订单</view>
<MotionButton @click="go(15)" aria-label="全部订单" class="legacy-action action-link u-text-_10px_ u-text-sage ui-action" data-action="全部订单" role="button" feedback="icon" :busy="busy">全部订单 <AppIcon asset="fas-chevron-right-a8c5bd" class="u-text-_8px_"></AppIcon></MotionButton>
</view>
<view class="u-grid u-grid-cols-4 u-text-center">
<MotionButton @click="go(15, 'status=unpaid')" aria-label="待付款" class="legacy-action relative ui-action" data-action="待付款" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-wallet-f2eee4" class="u-text-_17px_ u-text-mist_85"></AppIcon><view class="u-absolute u-top-0 u-right-4 u-w-1_5 u-h-1_5 u-rounded-full u-bg-__FF4D6A_"></view><view class="u-text-_10px_ u-text-sage u-mt-1_5">待付款</view></MotionButton>
<MotionButton @click="go(15, 'status=unshipped')" aria-label="待发货" class="legacy-action ui-action" data-action="待发货" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-box-f2eee4" class="u-text-_17px_ u-text-mist_85"></AppIcon><view class="u-text-_10px_ u-text-sage u-mt-1_5">待发货</view></MotionButton>
<MotionButton @click="go(15, 'status=shipping')" aria-label="待收货" class="legacy-action relative ui-action" data-action="待收货" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-truck-f2eee4" class="u-text-_17px_ u-text-mist_85"></AppIcon><view class="u-absolute u-top-0 u-right-4 u-w-1_5 u-h-1_5 u-rounded-full u-bg-__FF4D6A_"></view><view class="u-text-_10px_ u-text-sage u-mt-1_5">待收货</view></MotionButton>
<MotionButton @click="go(15, 'status=after_sale')" aria-label="退换/售后" class="legacy-action ui-action" data-action="退换/售后" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-rotate-left-f2eee4" class="u-text-_17px_ u-text-mist_85"></AppIcon><view class="u-text-_10px_ u-text-sage u-mt-1_5">退换/售后</view></MotionButton>
</view>
</view>

<view class="u-mx-5 u-mt-4 u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3_5 u-flex u-items-center u-justify-between">
<MotionButton @click="go(14)" aria-label="本月静心 14.2 小时" class="legacy-action u-flex u-items-center u-gap-2_5 ui-action" data-action="本月静心 14.2 小时" role="button" feedback="icon" :busy="busy">
<AppIcon asset="fas-chart-simple-3fb3a3" class="u-text-jade u-text-_16px_"></AppIcon>
<view><view class="u-text-_12px_ u-font-semibold">本月静心 14.2 小时</view><view class="u-text-_10px_ u-text-sage u-mt-0_5">比上月多 3.5 小时 · 查看(静心报告)</view></view>
</MotionButton>
<AppIcon asset="fas-chevron-right-a8c5bd" class="u-text-_11px_ u-text-sage_60"></AppIcon>
</view>

<view class="u-mx-5 u-mt-4 u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-divide-y u-divide-white_8">
<MotionButton @click="go(19)" aria-label="我的推广" class="legacy-action u-flex u-items-center u-gap-3 u-px-3_5 u-py-2_5 u-bg-gold_8 ui-action" data-action="我的推广" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-share-nodes-e9c08a" class="u-text-gold u-w-5"></AppIcon><view class="u-flex-1 u-text-_13px_">我的推广</view><text class="u-text-_10px_ u-text-gold">已赚 ¥1,286</text><AppIcon asset="fas-chevron-right-a8c5bd" class="u-text-_10px_ u-text-sage_50"></AppIcon></MotionButton>
<MotionButton @click="go(9)" aria-label="会员中心" class="legacy-action u-flex u-items-center u-gap-3 u-px-3_5 u-py-2_5 ui-action" data-action="会员中心" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-crown-e9c08a" class="u-text-gold u-w-5"></AppIcon><view class="u-flex-1 u-text-_13px_">会员中心</view><text class="u-text-_10px_ u-text-gold">11.18 到期</text><AppIcon asset="fas-chevron-right-a8c5bd" class="u-text-_10px_ u-text-sage_50"></AppIcon></MotionButton>
<MotionButton @click="notice('收货地址', '地址表单将在订单联调时接入，当前没有读取或保存个人地址。')" aria-label="收货地址" class="legacy-action u-flex u-items-center u-gap-3 u-px-3_5 u-py-2_5 ui-action" data-action="收货地址" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-address-card-f2eee4" class="u-text-mist_85 u-w-5"></AppIcon><view class="u-flex-1 u-text-_13px_">收货地址</view><AppIcon asset="fas-chevron-right-a8c5bd" class="u-text-_10px_ u-text-sage_50"></AppIcon></MotionButton>
<MotionButton @click="go(18, 'section=security')" aria-label="账号与安全" class="legacy-action u-flex u-items-center u-gap-3 u-px-3_5 u-py-2_5 ui-action" data-action="账号与安全" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-shield-halved-f2eee4" class="u-text-mist_85 u-w-5"></AppIcon><view class="u-flex-1 u-text-_13px_">账号与安全</view><AppIcon asset="fas-chevron-right-a8c5bd" class="u-text-_10px_ u-text-sage_50"></AppIcon></MotionButton>
<MotionButton @click="go(18)" aria-label="设置" class="legacy-action u-flex u-items-center u-gap-3 u-px-3_5 u-py-2_5 ui-action" data-action="设置" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-gear-f2eee4" class="u-text-mist_85 u-w-5"></AppIcon><view class="u-flex-1 u-text-_13px_">设置</view><AppIcon asset="fas-chevron-right-a8c5bd" class="u-text-_10px_ u-text-sage_50"></AppIcon></MotionButton>
</view>
<view class="u-mx-5 u-mt-4 u-grid u-grid-cols-2 u-gap-3"><MotionButton role="button" @click="go(7)" class="legacy-action action-label u-rounded-xl u-border u-border-white_10 u-py-3 u-text-_12px_" feedback="icon" :busy="busy">澄心商城</MotionButton><MotionButton role="button" @click="go(11)" class="legacy-action action-label u-rounded-xl u-border u-border-white_10 u-py-3 u-text-_12px_" feedback="icon" :busy="busy">共修直播</MotionButton></view></view>

</ScreenFrame>
</template>
