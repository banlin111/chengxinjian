<script setup lang="ts">
import MotionButton from '../../components/MotionButton.vue';
import { onLoad } from '@dcloudio/uni-app';
import ScreenFrame from '../../components/ScreenFrame.vue';
import AppIcon from '../../components/AppIcon.vue';
import { useScreenPreview } from '../../composables/useScreenPreview';
const { selections, busy, choose, selected, action } = useScreenPreview(21);
onLoad(options => { const mapping: Record<string,number> = {'1':0,'2':1,all:2}; if (options?.level && mapping[options.level] !== undefined) selections.team = mapping[options.level]; });
</script>

<template>
<ScreenFrame :screen="21" gradient>

<view class="u-z-20 screen-content relative" style="padding-top:calc(var(--top) + 0px)">
<view class="u-px-6 u-text-_20px_ u-font-bold">我的好友</view>

<view class="u-flex u-gap-1 u-px-5 u-mt-3 u-text-_12px_ u-choice-group">
<MotionButton :class="selected('team', 0) ? 'choice-active' : 'choice-passive'" @click="choose('team', 0)" aria-label="一级 32" class="legacy-action action-tab u-px-3_5 u-py-1_5 u-rounded-full u-bg-jade u-text-night u-font-bold ui-action" data-action="一级 32" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('team', 0) ? 'true' : 'false'">一级 32</MotionButton>
<MotionButton :class="selected('team', 1) ? 'choice-active' : 'choice-passive'" @click="choose('team', 1)" aria-label="二级 71" class="legacy-action action-tab u-px-3_5 u-py-1_5 u-rounded-full u-text-sage u-bg-white_8 ui-action" data-action="二级 71" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('team', 1) ? 'true' : 'false'">二级 71</MotionButton>
<MotionButton :class="selected('team', 2) ? 'choice-active' : 'choice-passive'" @click="choose('team', 2)" aria-label="全部 103" class="legacy-action action-tab u-px-3_5 u-py-1_5 u-rounded-full u-text-sage u-bg-white_8 ui-action" data-action="全部 103" role="button" feedback="icon" :busy="busy" :aria-pressed="selected('team', 2) ? 'true' : 'false'">全部 103</MotionButton>
</view>

<view class="u-mx-5 u-mt-3_5 u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-px-3_5 u-py-2_5 u-flex u-items-center u-justify-between u-text-_10px_">
<text class="u-text-sage">本月新增 <text class="u-text-jade u-font-bold">12 人</text></text>
<text class="u-text-sage">贡献佣金 <text class="u-text-gold u-font-bold">¥312.00</text></text>
<text class="u-text-sage">活跃 <text class="u-text-mist u-font-bold">21 人</text></text>
</view>

<view class="u-px-5 u-mt-3 u-space-y-2_5" v-show="!selected('team',1)">
<view class="u-rounded-2xl u-bg-white_8 u-border u-border-white_10 u-p-3">
<view class="u-flex u-items-center u-gap-3">
<image class="u-w-10 u-h-10 u-rounded-full u-object-cover" mode="aspectFill" src="/static/prototype/3146653f7c81.jpg"/>
<view class="u-flex-1 u-min-w-0">
<view class="u-flex u-items-center u-gap-1_5"><view class="u-text-_13px_ u-font-semibold u-truncate">小满</view><text class="u-px-1_5 u-py-0_5 u-rounded u-bg-jade_20 u-text-jade u-text-_9px_">活跃</text></view>
<view class="u-text-_10px_ u-text-sage u-mt-0_5">2026.09.12 加入 · 一级好友</view>
</view>
<view class="u-text-right"><view class="u-text-_13px_ u-font-bold u-text-gold">¥97.20</view><view class="u-text-_9px_ u-text-sage_60 u-mt-0_5">贡献佣金</view></view>
</view>
<view class="u-flex u-justify-between u-items-center u-mt-2_5 u-pt-2_5 u-border-t u-border-white_8 u-text-_10px_ u-text-sage">
<text>累计消费 ¥486（年卡+颂钵）</text><text>最近下单 10.28</text>
</view>
</view>



</view><view class="u-empty-panel" v-if="selected('team',1)">暂无二级好友的示例明细</view>
<view class="u-text-center u-text-_9px_ u-text-sage_50 u-mt-3">仅展示近 90 天活跃及有消费记录的好友</view>
</view>

</ScreenFrame>
</template>
