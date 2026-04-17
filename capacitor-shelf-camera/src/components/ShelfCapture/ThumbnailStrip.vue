<template>
  <div class="thumb-strip">
    <div class="thumb-track" ref="trackEl">
      <TransitionGroup name="thumb-pop">
        <div
          v-for="(uri, idx) in convertedUris"
          :key="idx"
          class="thumb-cell"
          :class="{ 'thumb-cell--latest': idx === convertedUris.length - 1 }"
        >
          <img :src="uri" class="thumb-img" loading="lazy" />
          <div class="thumb-index">{{ idx + 1 }}</div>
        </div>
      </TransitionGroup>

      <!-- Ghost next-shot indicator -->
      <div class="thumb-cell thumb-cell--ghost" v-if="convertedUris.length > 0">
        <LucidePlus :size="16" class="ghost-icon" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue';
import { LucidePlus } from 'lucide-vue-next';
import { toWebSrc } from '../../utils/webSrc';

const props = defineProps<{ uris: string[] }>();

const trackEl = ref<HTMLElement | null>(null);

const convertedUris = computed(() => props.uris.map(toWebSrc));

watch(() => props.uris.length, async () => {
    await nextTick();
    if (trackEl.value) {
        trackEl.value.scrollLeft = trackEl.value.scrollWidth;
    }
});
</script>

<style scoped>
.thumb-strip {
  position: fixed;
  bottom: 140px;
  left: 0;
  right: 0;
  height: 84px;
  z-index: 40;
  pointer-events: auto;
  padding: 0 16px;
}

.thumb-track {
  display: flex;
  gap: 8px;
  align-items: center;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-snap-type: x mandatory;
  scrollbar-width: none;
  height: 100%;
  padding: 4px 0;
}
.thumb-track::-webkit-scrollbar { display: none; }

.thumb-cell {
  flex-shrink: 0;
  width: 52px;
  height: 68px;
  border-radius: 10px;
  overflow: hidden;
  position: relative;
  scroll-snap-align: end;
  border: 2px solid rgba(255,255,255,0.15);
  background: rgba(255,255,255,0.05);
  transition: border-color var(--t-short) var(--ease-out),
              transform var(--t-short) var(--ease-spring);
}

.thumb-cell--latest {
  border-color: var(--prism-5);
  box-shadow: 0 0 12px rgba(126,240,198,0.3);
}

.thumb-cell--ghost {
  border: 2px dashed rgba(255,255,255,0.2);
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ghost-icon {
  color: var(--text-2);
}

.thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-index {
  position: absolute;
  bottom: 2px;
  right: 4px;
  font-size: 9px;
  font-weight: 700;
  color: var(--text-0);
  text-shadow: 0 1px 3px rgba(0,0,0,0.8);
}

/* Pop-in animation */
.thumb-pop-enter-active {
  transition: all 320ms var(--ease-spring);
}
.thumb-pop-leave-active {
  transition: all 200ms var(--ease-out);
}
.thumb-pop-enter-from {
  opacity: 0;
  transform: scale(0.7) translateY(8px);
}
.thumb-pop-leave-to {
  opacity: 0;
  transform: scale(0.85);
}
</style>
