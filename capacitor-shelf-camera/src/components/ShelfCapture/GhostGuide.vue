<template>
  <Transition name="ghost-fade">
    <div v-if="visible && src" class="ghost-guide">
      <div class="ghost-image" :style="{ backgroundImage: `url(${src})` }" />
      <div class="ghost-border" />
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { toWebSrc } from '../../utils/webSrc'

const props = defineProps<{
  visible: boolean
  uri?: string
}>()

const src = computed(() => {
  return props.uri ? toWebSrc(props.uri) : null
})
</script>

<style scoped>
.ghost-guide {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 25vw;
  max-width: 120px;
  min-width: 80px;
  z-index: 5; /* Above camera surface, below UI */
  pointer-events: none;
}

.ghost-image {
  width: 100%;
  height: 100%;
  background-size: cover;
  /* We want to show the RIGHT side of the previous image on the LEFT of the screen */
  background-position: right center;
  background-repeat: no-repeat;
  opacity: 0.55;
  transform: scaleX(-1); /* Flips the image horizontally to address mirroring feedback */
  /* Mask it so it fades elegantly into the live view */
  mask-image: linear-gradient(to left, rgba(0, 0, 0, 1) 30%, rgba(0, 0, 0, 0) 100%);
  -webkit-mask-image: linear-gradient(to left, rgba(0, 0, 0, 1) 30%, rgba(0, 0, 0, 0) 100%);
}

.ghost-border {
  position: absolute;
  top: 0;
  bottom: 0;
  right: 0;
  width: 2px;
  background: linear-gradient(to bottom, transparent, rgba(255, 255, 255, 0.5), transparent);
  box-shadow: 0 0 12px rgba(255, 255, 255, 0.3);
}

.ghost-fade-enter-active,
.ghost-fade-leave-active {
  transition: opacity var(--t-short) var(--ease-out);
}
.ghost-fade-enter-from,
.ghost-fade-leave-to {
  opacity: 0;
}
</style>
