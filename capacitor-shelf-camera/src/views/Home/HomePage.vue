<template>
  <div class="home-page">
    <!-- Floating prism particles background -->
    <div class="particle-field">
      <div v-for="i in 12" :key="i" class="particle" :style="particleStyle(i)" />
    </div>

    <!-- Hero -->
    <div class="hero">
      <div class="icon-ring">
        <LucideScanLine class="hero-icon" :size="48" />
      </div>
      <h1 class="hero-title">Shelf Camera</h1>
      <p class="hero-subtitle">Capture, stitch &amp; analyze shelf panoramas</p>
    </div>

    <!-- Actions -->
    <div class="actions">
      <kButton class="rounded-2xl p-6 gap-2" @click="startCapture">
        <LucideCamera :size="24" />
        <span>New Capture</span>
      </kButton>

      <div class="stats-row">
        <div class="stat-chip glass-surface">
          <LucideImage :size="16" />
          <span>{{ capturedCount }} captures</span>
        </div>
        <div class="stat-chip glass-surface">
          <LucideZap :size="16" />
          <span>Plugin v1.0</span>
        </div>
      </div>
    </div>

    <!-- Feature Cards -->
    <div class="feature-grid">
      <div class="feature-card glass-surface" v-for="feat in features" :key="feat.title">
        <component :is="feat.icon" :size="20" class="feature-icon" />
        <div class="feature-text">
          <span class="feature-title">{{ feat.title }}</span>
          <span class="feature-desc">{{ feat.desc }}</span>
        </div>
      </div>
    </div>

    <!-- Version footer -->
    <div class="footer">
      <span>capacitor-shelf-camera · Example App</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  LucideScanLine,
  LucideCamera,
  LucideImage,
  LucideZap,
  LucideWand2,
  LucideMove3d,
  LucideEye,
  LucideShield,
} from 'lucide-vue-next'
import { kButton } from 'konsta/vue'

const router = useRouter()
const capturedCount = ref(0)

const features = [
  { icon: LucideWand2, title: 'Smart Coaching', desc: 'Real-time signals guide your sweep' },
  { icon: LucideMove3d, title: 'Auto Stitch', desc: 'OpenCV panorama stitching on-device' },
  { icon: LucideEye, title: 'Live Preview', desc: 'Native camera preview underneath' },
  { icon: LucideShield, title: 'Gallery Save', desc: 'Panoramas saved to device gallery' },
]

function particleStyle(i: number) {
  const colors = ['#7aa8ff', '#b593ff', '#ff8ad1', '#ffb26b', '#7ef0c6']
  return {
    left: `${Math.random() * 100}%`,
    top: `${Math.random() * 100}%`,
    width: `${2 + Math.random() * 4}px`,
    height: `${2 + Math.random() * 4}px`,
    background: colors[i % colors.length],
    animationDelay: `${Math.random() * 6}s`,
    animationDuration: `${4 + Math.random() * 4}s`,
  }
}

function startCapture() {
  router.push({ name: 'capture' })
}
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 24px 32px;
  position: relative;
  overflow: hidden;
}

/* ─── Particles ─── */
.particle-field {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}
.particle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.25;
  animation: float-up linear infinite;
}
@keyframes float-up {
  0% {
    transform: translateY(0) scale(1);
    opacity: 0.15;
  }
  50% {
    opacity: 0.35;
  }
  100% {
    transform: translateY(-100vh) scale(0.5);
    opacity: 0;
  }
}

/* ─── Hero ─── */
.hero {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  margin-bottom: 48px;
  animation: fade-up-in 0.8s var(--ease-out) both;
}
.icon-ring {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--prism-gradient);
  padding: 3px;
  margin-bottom: 8px;
}
.icon-ring::before {
  content: '';
  position: absolute;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: var(--prism-gradient);
  filter: blur(24px);
  opacity: 0.4;
  animation: breathing 3s ease-in-out infinite;
}
.hero-icon {
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: var(--bg-0);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  color: var(--text-0);
}
.hero-title {
  font-size: 32px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.15;
  color: var(--text-0);
}
.hero-subtitle {
  font-size: 16px;
  color: var(--text-1);
  text-align: center;
  max-width: 260px;
}

/* ─── Actions ─── */
.actions {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  width: 100%;
  max-width: 320px;
  margin-bottom: 40px;
  animation: fade-up-in 0.8s var(--ease-out) 0.15s both;
}
.action-primary {
  width: 100%;
  padding: 3px;
  border-radius: 16px;
  background: var(--prism-gradient);
  background-size: 200% 100%;
  animation: shimmer 3s linear infinite;
  border: none;
  cursor: pointer;
  transition: transform var(--t-micro) var(--ease-spring);
}
.action-primary:active {
  transform: scale(0.97);
}
.action-primary-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 16px 24px;
  border-radius: 14px;
  background: var(--bg-0);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: -0.01em;
}
.stats-row {
  display: flex;
  gap: 10px;
}
.stat-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 99px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-1);
}

/* ─── Feature Cards ─── */
.feature-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 100%;
  max-width: 320px;
  animation: fade-up-in 0.8s var(--ease-out) 0.3s both;
}
.feature-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border-radius: 16px;
  transition: transform var(--t-micro) var(--ease-spring);
}
.feature-card:active {
  transform: scale(0.97);
}
.feature-icon {
  color: var(--prism-1);
}
.feature-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.feature-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-0);
}
.feature-desc {
  font-size: 12px;
  color: var(--text-2);
  line-height: 1.3;
}

/* ─── Footer ─── */
.footer {
  margin-top: auto;
  padding-top: 40px;
  font-size: 11px;
  color: var(--text-2);
  position: relative;
  z-index: 1;
}
</style>
