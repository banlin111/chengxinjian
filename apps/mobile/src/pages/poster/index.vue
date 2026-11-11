<script setup lang="ts">
import MotionButton from '../../components/MotionButton.vue';
import ScreenFrame from '../../components/ScreenFrame.vue';
import AppIcon from '../../components/AppIcon.vue';
import { useScreenPreview } from '../../composables/useScreenPreview';
const { busy, choose, selected, notice, action } = useScreenPreview(20);
</script>

<template>
<ScreenFrame :screen="20" gradient>

<view class="u-z-20 u-px-5 screen-content relative" style="padding-top:calc(var(--top) + 0px)">
<view class="u-text-_20px_ u-font-bold">生成推广海报</view>
<view class="u-text-_11px_ u-text-sage u-mt-1">以下为海报样式预览，不会绑定<text class="u-text-jade">一级好友</text></view>

<view class="u-mt-4 u-rounded-2xl u-overflow-hidden u-bg-gradient-to-b u-from-__1A3B36_ u-to-__0E1F1E_ u-border u-border-white_12">
<view class="preserve-dark relative u-h-_200px_">
<image class="u-w-full u-h-full u-object-cover u-opacity-85" mode="aspectFill" src="/static/prototype/fd88956af286.jpg"/>
<view class="u-absolute u-inset-0 u-bg-gradient-to-t u-from-__0E1F1E_ u-via-transparent"></view>
<view class="u-absolute u-bottom-3 u-left-4">
<view class="u-text-_15px_ u-font-bold u-leading-snug">{{ selected('copy',0) ? '我在澄心涧静心了 128 天' : '留给自己一段安静的时间' }}</view>
<view class="u-text-_11px_ u-text-sage u-mt-1">{{ selected('copy',0) ? '送你 7 天会员，一起把心放回身体里（示例）' : '从一次呼吸开始，与安静相遇' }}</view>
</view>
</view>
<view class="u-px-4 u-py-3_5 u-flex u-items-center u-gap-3_5">

<view class="u-w-_86px_ u-h-_86px_ u-rounded-lg u-bg-mist u-p-2 u-flex u-items-center u-justify-center relative">
<AppIcon asset="fas-qrcode-0f1d1f" class="u-text-_56px_ u-text-night"></AppIcon>
<view class="u-absolute u-inset-0 u-m-auto u-w-6 u-h-6 u-rounded-md u-bg-mist u-flex u-items-center u-justify-center"><AppIcon asset="fas-water-2c8c82" class="u-text-_11px_ u-text-pine"></AppIcon></view>
</view>
<view class="u-flex-1">
<view class="u-flex u-items-center u-gap-2">
<image class="u-w-7 u-h-7 u-rounded-full u-object-cover" mode="aspectFill" src="/static/prototype/b3732e014658.jpg"/>
<view><view class="u-text-_11px_ u-font-semibold u-leading-tight">苏澜 的专属邀请</view><view class="u-text-_9px_ u-text-sage u-mt-0_5">邀请码 JX-8F2K</view></view>
</view>
<MotionButton @click="notice('示例二维码', '此处是原型二维码图案，不包含真实推广链接。')" aria-label="长按识别二维码" class="legacy-action u-text-_9px_ u-text-sage_70 u-mt-2 u-leading-relaxed ui-action" data-action="长按识别二维码" role="button" feedback="icon" :busy="busy">长按识别二维码<text>
</text>示例图案，不含可访问推广链接</MotionButton>
</view>
</view>
</view>

<view class="u-mt-4">
<view class="u-flex u-items-center u-justify-between u-mb-2"><view class="u-text-_13px_ u-font-semibold">海报文案</view><MotionButton @click="choose('copy', selected('copy',0) ? 1 : 0)" aria-label="换一换" class="legacy-action action-link u-text-_10px_ u-text-sage ui-action" data-action="换一换" role="button" feedback="icon" :busy="busy">换一换 <AppIcon asset="fas-rotate-right-a8c5bd" class="u-text-_9px_"></AppIcon></MotionButton></view>
<view class="u-space-y-2 u-choice-group">
<MotionButton :class="selected('copy', 0) ? 'choice-active' : 'choice-passive'" @click="choose('copy', 0)" aria-label="「我在澄心涧静心了 128 天，送你 7 天会员」" class="legacy-action u-flex u-items-center u-gap-2_5 u-rounded-xl u-bg-jade_12 u-border u-border-jade_40 u-px-3_5 u-py-2_5 ui-action" data-action="「我在澄心涧静心了 128 天，送你 7 天会员」" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('copy', 0) ? 'true' : 'false'">
<AppIcon asset="fas-check-circle-3fb3a3" class="u-text-jade u-text-_12px_"></AppIcon>
<view class="u-text-_11px_ u-text-mist u-flex-1">「我在澄心涧静心了 128 天，送你 7 天会员」</view>
</MotionButton>
<MotionButton :class="selected('copy', 1) ? 'choice-active' : 'choice-passive'" @click="choose('copy', 1)" aria-label="「留给自己一段安静的时间」" class="legacy-action u-flex u-items-center u-gap-2_5 u-rounded-xl u-bg-white_6 u-border u-border-white_10 u-px-3_5 u-py-2_5 ui-action" data-action="「留给自己一段安静的时间」" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('copy', 1) ? 'true' : 'false'">
<AppIcon asset="far-circle-a8c5bd" class="u-text-sage_50 u-text-_12px_"></AppIcon>
<view class="u-text-_11px_ u-text-sage u-flex-1">「留给自己一段安静的时间」</view>
</MotionButton>
</view>
</view>

<view class="u-mt-4 u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3_5">
<view class="u-flex u-justify-around u-text-center">
<MotionButton @click="action('分享预览')" aria-label="微信好友" class="legacy-action ui-action" data-action="微信好友" role="button" feedback="icon" :busy="busy"><view class="u-w-11 u-h-11 u-rounded-full u-bg-__2BB673__15 u-border u-border-__2BB673__30 u-flex u-items-center u-justify-center u-text-__4ED39A_"><AppIcon asset="fab-weixin-4ed39a" class="u-text-_18px_"></AppIcon></view><view class="u-text-_9px_ u-text-sage u-mt-1_5">微信好友</view></MotionButton>
<MotionButton @click="action('分享预览')" aria-label="朋友圈" class="legacy-action ui-action" data-action="朋友圈" role="button" feedback="icon" :busy="busy"><view class="u-w-11 u-h-11 u-rounded-full u-bg-__2BB673__15 u-border u-border-__2BB673__30 u-flex u-items-center u-justify-center u-text-__4ED39A_"><AppIcon asset="fas-circle-nodes-4ed39a" class="u-text-_17px_"></AppIcon></view><view class="u-text-_9px_ u-text-sage u-mt-1_5">朋友圈</view></MotionButton>
<MotionButton @click="action('分享预览')" aria-label="复制链接" class="legacy-action ui-action" data-action="复制链接" role="button" feedback="icon" :busy="busy"><view class="u-w-11 u-h-11 u-rounded-full u-bg-white_10 u-border u-border-white_15 u-flex u-items-center u-justify-center u-text-mist"><AppIcon asset="fas-link-f2eee4" class="u-text-_16px_"></AppIcon></view><view class="u-text-_9px_ u-text-sage u-mt-1_5">复制链接</view></MotionButton>
<MotionButton @click="action('分享预览')" aria-label="保存海报" class="legacy-action ui-action" data-action="保存海报" role="button" feedback="icon" :busy="busy"><view class="u-w-11 u-h-11 u-rounded-full u-bg-white_10 u-border u-border-white_15 u-flex u-items-center u-justify-center u-text-mist"><AppIcon asset="fas-download-f2eee4" class="u-text-_16px_"></AppIcon></view><view class="u-text-_9px_ u-text-sage u-mt-1_5">保存海报</view></MotionButton>
</view>
</view>
<MotionButton @click='action("分享给好友")' aria-label="分享给好友" class="legacy-action action-label u-mt-4 u-w-full u-py-3_5 u-rounded-full u-bg-gradient-to-r u-from-jade u-to-pine u-text-_14px_ u-font-bold u-shadow-xl u-shadow-jade_25 ui-action" data-action="分享给好友" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-share-nodes-ffffff" class="u-mr-1_5"></AppIcon>分享给好友</MotionButton>
<view class="u-text-center u-text-_9px_ u-text-sage_50 u-mt-2_5">分享与保存为演示入口，不会发出邀请或授予会员</view>
</view>

</ScreenFrame>
</template>
