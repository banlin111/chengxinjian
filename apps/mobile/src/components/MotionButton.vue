<script setup lang="ts">
import { computed, ref } from 'vue';
import { onHide } from '@dcloudio/uni-app';
import { useThemeStore } from '../stores/theme';

// Remove the native component wrapper so flex sizing and parent classes reach the button.
defineOptions({ options: { virtualHost: true, styleIsolation: 'apply-shared' } });
const props = defineProps<{ disabled?: boolean; busy?: boolean; feedback?: 'card' | 'button' | 'link' | 'icon'; ariaLabel?: string }>();
// A temporary navigation lock must not invoke WeChat's disabled appearance.
const blocked = computed(() => Boolean(props.disabled || props.busy));
const emit = defineEmits<{ (event: 'click'): void }>();
const theme = useThemeStore();
const pressed = ref(false);
let origin: { x: number; y: number } | null = null;
let cancelled = false;
let lastTouch = 0;
type TouchLike = { touches: ArrayLike<{ clientX: number; clientY: number }> };

function touchStart(event: TouchLike) {
  if (blocked.value) return;
  lastTouch = Date.now();
  cancelled = event.touches.length !== 1;
  const point = event.touches[0];
  origin = point ? { x: point.clientX, y: point.clientY } : null;
  pressed.value = !cancelled;
}
function touchMove(event: TouchLike) {
  const point = event.touches[0];
  if (!origin || !point || Math.hypot(point.clientX - origin.x, point.clientY - origin.y) > 8) cancel();
}
function release() { pressed.value = false; origin = null; }
function cancel() { cancelled = true; release(); }
function mouseStart() {
  if (blocked.value) return;
  // Browsers may synthesize a mouse event after touch; it must not undo a cancelled swipe.
  if (Date.now() - lastTouch < 700) return;
  cancelled = false; pressed.value = true;
}
type KeyLike = { key?: string; repeat?: boolean; preventDefault?: () => void };
function keyStart(event: KeyLike) {
  if (blocked.value) return;
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault?.();
    if (!event.repeat) { cancelled = false; pressed.value = true; }
  }
}
function keyEnd(event: KeyLike) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault?.();
    if (pressed.value) activate();
  }
}
function activate() {
  release();
  if (!cancelled && !blocked.value) emit('click');
}
onHide(() => { release(); cancelled = false; });
</script>

<template>
  <button role="button" hover-class="none" :disabled="disabled" :aria-label="ariaLabel" :tabindex="blocked ? -1 : 0" :aria-disabled="blocked ? 'true' : 'false'"
    class="motion-button" :class="{ 'is-pressed': pressed && !blocked, 'feedback-card': feedback === 'card', 'feedback-link': feedback === 'link', 'feedback-icon': feedback === 'icon', 'reduce-motion': theme.reducedMotion }"
    @touchstart="touchStart" @touchmove="touchMove" @touchend="release" @touchcancel="cancel"
    @mousedown="mouseStart" @mouseup="release" @mouseleave="cancel" @blur="release"
    @keydown="keyStart" @keyup="keyEnd" @click="activate">
    <slot />
  </button>
</template>

<style scoped>
.motion-button { transform: scale(1); transform-origin: center; opacity: 1; transition: transform 320ms cubic-bezier(.22,1,.36,1), opacity 180ms ease-out, box-shadow 240ms ease-out; -webkit-tap-highlight-color: transparent; }
/* WeChat paints disabled buttons grey by default. Navigation locks must keep
   the card/link surface unchanged underneath the expanding player. Preserve
   background-image so filled action buttons retain their own gradients. */
.motion-button[disabled] { background-color: transparent !important; color: inherit !important; opacity: 1; }
.motion-button.is-pressed { transform: scale(.97); opacity: .84; transition-duration: 90ms; transition-timing-function: cubic-bezier(.2,.7,.3,1); }
.motion-button.feedback-card.is-pressed { transform: scale(.985); opacity: .9; }
.motion-button.feedback-link.is-pressed { transform: scale(.98); opacity: .6; }
.motion-button.feedback-icon { opacity: 1; transition-property: transform; }
.motion-button.feedback-icon.is-pressed { transform: scale(.97); opacity: 1; }
.motion-button.reduce-motion { transition: none; transform: none; }
.motion-button.reduce-motion.is-pressed { transform: none; }
/* #ifdef H5 */
.motion-button:focus-visible { outline: 2px solid #A0E2D7; outline-offset: 4px; }
/* #endif */
@media (prefers-reduced-motion: reduce) {
  .motion-button, .motion-button.is-pressed { transition: none !important; transform: none !important; }
}
</style>
