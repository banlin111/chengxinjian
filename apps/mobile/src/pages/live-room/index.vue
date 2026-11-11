<script setup lang="ts">
import { ref } from 'vue';
import MotionButton from '../../components/MotionButton.vue';
import ScreenFrame from '../../components/ScreenFrame.vue';
import AppIcon from '../../components/AppIcon.vue';
import { useScreenPreview } from '../../composables/useScreenPreview';
const { busy, liked, followed, message, localMessages, notice, sendLocalMessage, action } = useScreenPreview(17);
const commentInput = ref<{ $el?: HTMLElement } | null>(null);
function submitComment() {
  // #ifdef H5
  message.value = commentInput.value?.$el?.querySelector('input')?.value ?? message.value;
  // #endif
  sendLocalMessage();
}
</script>

<template>
<ScreenFrame :screen="17">
<view class="live-canvas"><text class="live-demo-status">直播画面占位 · 尚未接入直播流</text>

<image class="u-absolute u-inset-0 u-w-full u-h-full u-object-cover" mode="aspectFill" src="/static/prototype/6907df7f1eed.jpg"/>

<view class="u-absolute u-inset-0 u-bg-gradient-to-b u-from-night_55 u-via-transparent u-to-__0F1D1F_"></view>

<view class="u-absolute u-top-_58px_ u-left-5 u-z-20 u-flex u-items-center u-gap-2_5 u-rounded-full u-bg-black_35 u-backdrop-blur u-border u-border-white_15 u-p-1_5 u-pr-4">
<image class="u-w-9 u-h-9 u-rounded-full u-object-cover" mode="aspectFill" src="/static/prototype/b3732e014658.jpg"/>
<view class="u-text-_11px_"><view class="u-font-semibold u-leading-tight">明一 · 颂钵音疗</view><view class="u-text-sage u-text-_9px_ u-mt-0_5"><AppIcon asset="fas-eye-a8c5bd" class="u-mr-0_5"></AppIcon>2,341 人 · 示例</view></view>
<MotionButton @click="followed = !followed" :aria-label="followed ? '取消关注主播' : '关注主播'" :aria-pressed="followed ? 'true' : 'false'" class="u-ml-1 u-px-3 u-py-1 u-rounded-full u-bg-jade u-text-night u-text-_10px_ u-font-bold ui-action" data-action="已关注" role="button" feedback="icon" :busy="busy">{{ followed ? '已关注' : '+ 关注' }}</MotionButton>
</view>

<view class="u-absolute u-top-_70px_ u-right-5 u-z-20 u-px-2_5 u-py-1 u-rounded-md u-bg-__FF4D6A_ u-text-_10px_ u-font-bold"><AppIcon asset="fas-tower-broadcast-ffffff" class="u-mr-1"></AppIcon>演示</view>

<view class="u-absolute u-top-_128px_ u-left-5 u-z-20 u-px-3 u-py-1_5 u-rounded-full u-bg-black_30 u-backdrop-blur u-text-_10px_ u-text-sage u-max-w-_260px_ u-truncate"><AppIcon asset="fas-bullhorn-e9c08a" class="u-text-gold u-mr-1_5"></AppIcon>今晚共修主题：月圆之夜 · 慈心回向，请提前静音</view>

<view class="u-absolute u-right-5 u-top-_150px_ u-z-20 u-flex u-flex-col u-items-center u-gap-3 u-text-_18px_">
<AppIcon asset="fas-heart-e9c08a" class="u-text-gold_90"></AppIcon>
<AppIcon asset="fas-heart-3fb3a3" class="u-text-jade_80" style=""></AppIcon>
<AppIcon asset="fas-heart-e9c08a" class="u-text-gold_60" style=""></AppIcon>
</view>

<view class="u-absolute u-right-5 u-bottom-_150px_ u-z-20 u-flex u-flex-col u-gap-4 u-items-center">
<MotionButton @click="liked = !liked" :aria-label="liked ? '取消点赞' : '点赞'" :aria-pressed="liked ? 'true' : 'false'" class="u-flex u-flex-col u-items-center ui-action" data-action="8.2万" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-heart-e9c08a" class="u-text-_20px_ u-text-gold"></AppIcon><text class="u-text-_9px_ u-text-sage u-mt-0_5">{{ liked ? '8.2万 + 1' : '8.2万' }}</text></MotionButton>
<MotionButton @click="notice('礼物演示', '赠礼入口只展示界面，不产生付费或发送礼物。')" aria-label="礼物" class="u-flex u-flex-col u-items-center ui-action" data-action="礼物" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-gift-f2eee4" class="u-text-_20px_ u-text-mist_90"></AppIcon><text class="u-text-_9px_ u-text-sage u-mt-0_5">礼物</text></MotionButton>
<MotionButton @click="action('分享')" aria-label="分享" class="u-flex u-flex-col u-items-center ui-action" data-action="分享" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-share-nodes-f2eee4" class="u-text-_20px_ u-text-mist_90"></AppIcon><text class="u-text-_9px_ u-text-sage u-mt-0_5">分享</text></MotionButton>
</view>

<view class="u-absolute u-bottom-_104px_ u-left-5 u-right-_70px_ u-z-20 u-space-y-2">
<view v-for="(item,index) in localMessages.slice(-2)" :key="index" class="local-comment"><text>我（本机演示）</text>{{ item }}</view>
<view class="u-rounded-2xl u-rounded-bl-sm u-bg-black_35 u-backdrop-blur u-px-3_5 u-py-2 u-w-fit u-max-w-_280px_">
<view class="u-text-_11px_"><text class="u-text-jade u-font-semibold">涧友·小满</text> <text class="u-px-1_5 u-py-0_5 u-rounded u-bg-gold_25 u-text-gold u-text-_9px_ u-mr-1">打卡128天</text><text class="u-text-mist_90">第一次跟完全场，今晚很放松</text></view>
</view>

</view>

<view class="u-absolute u-bottom-_34px_ u-inset-x-0 u-px-5 u-z-30">
<view class="u-flex u-items-center u-gap-3">
<input ref="commentInput" :maxlength="100" @confirm="sendLocalMessage" class="u-live-input" placeholder="说点什么…（本机演示）" v-model="message"/>
<MotionButton @click="submitComment()" aria-label="发送本机演示评论" class="u-w-10 u-h-10 u-rounded-full u-bg-gradient-to-r u-from-jade u-to-pine u-flex u-items-center u-justify-center u-text-_14px_ ui-action" data-action="发送本机演示评论" role="button" feedback="icon" :busy="busy"><AppIcon asset="fas-paper-plane-ffffff" class=""></AppIcon></MotionButton>
</view>
</view>

</view>
</ScreenFrame>
</template>

<style scoped>
.local-comment { padding: 8px 12px; background: #00000066; border: 1px solid #3fb3a366; border-radius: 14px; font-size: 11px; }.local-comment text { color: #3FB3A3; margin-right: 6px; }
</style>

<style>
.pdf-screen[data-screen="17"] { padding-bottom: 0; }
.pdf-screen[data-screen="17"] .live-canvas { min-height: max(568px,100vh); min-height: max(568px,100dvh); }
.pdf-screen[data-screen="17"] .motion-button { min-width: 44px; min-height: 44px; }
.pdf-screen[data-screen="17"] .u-top-_58px_ { right: 70px; left: 16px; }
.pdf-screen[data-screen="17"] .u-top-_58px_ > .u-text-_11px_ { flex: 1; min-width: 0; }
.pdf-screen[data-screen="17"] .u-top-_58px_ .motion-button { padding-left: 7px; padding-right: 7px; font-size: 10px; }
.pdf-screen[data-screen="17"] .u-top-_70px_ { right: 16px; }
.pdf-screen[data-screen="17"] .u-top-_128px_ { top: calc(var(--top) + 72px); }
.pdf-screen[data-screen="17"] .u-top-_150px_ { display: none; }
.pdf-screen[data-screen="17"] .u-bottom-_104px_ { max-height: 245px; overflow-y: auto; }
.pdf-screen[data-screen="17"] .u-bottom-_34px_ { bottom: max(24px,env(safe-area-inset-bottom)); }
.pdf-screen[data-screen="17"] .local-comment { overflow-wrap: anywhere; }
.pdf-screen[data-screen="17"] .u-live-input { height: 44px; }
.live-demo-status { position: absolute; z-index: 10; padding: 10px; border-radius: 10px; background: #0F1D1F88; top: 36%; left: 20px; right: 70px; text-align: center; color:#BCD5CF; font-size:11px; }
@media(max-width:350px) { .pdf-screen[data-screen="17"] .u-top-_58px_ { right:16px; gap:8px; }.pdf-screen[data-screen="17"] .u-top-_70px_ { display:none; } }
</style>
