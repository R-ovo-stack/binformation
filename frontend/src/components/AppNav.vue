<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const items = [
  { name: 'assets', label: '数据资产', path: '/' },
  { name: 'endpoints', label: '落点', path: '/endpoints' },
  { name: 'executors', label: '程序/脚本', path: '/executors' },
  { name: 'impact', label: '影响分析', path: '/impact' },
] as const

const active = computed(() => {
  const n = String(route.name ?? '')
  if (n === 'assets' || n.startsWith('asset')) return 'assets'
  if (n.startsWith('endpoint')) return 'endpoints'
  if (n.startsWith('executor')) return 'executors'
  if (n.startsWith('derivation') || n === 'asset-derivations') return 'assets'
  if (n === 'asset-flows' || n.startsWith('flow')) return 'assets'
  if (n === 'impact') return 'impact'
  return ''
})

function go(path: string) {
  void router.push(path)
}

function goHome() {
  void router.push('/')
}
</script>

<template>
  <header class="app-chrome">
    <button type="button" class="brand-lockup" aria-label="回到首页" @click="goHome">
      <span class="brand-mark" aria-hidden="true">
        <span class="mark-core" />
        <span class="mark-ring" />
      </span>
      <span class="brand-text">
        <span class="brand-name">数据中心台账</span>
        <span class="brand-tag">Ledger Atlas</span>
      </span>
    </button>

    <nav class="app-nav" aria-label="主导航">
      <button
        v-for="item in items"
        :key="item.name"
        type="button"
        class="nav-item"
        :class="{ active: active === item.name }"
        @click="go(item.path)"
      >
        {{ item.label }}
      </button>
    </nav>
  </header>
</template>

<style scoped>
.app-chrome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin: 0 0 20px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--surface);
  box-shadow: var(--shadow-panel);
  backdrop-filter: blur(14px);
  animation: chrome-in 0.5s ease both;
}

.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border: 0;
  background: transparent;
  padding: 4px 6px;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;
}

.brand-mark {
  position: relative;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
}

.mark-core {
  position: absolute;
  inset: 8px;
  border-radius: 8px;
  background: linear-gradient(145deg, #14b8a6 0%, var(--accent-deep) 55%, #0e7490 100%);
  box-shadow: 0 6px 14px rgba(13, 148, 136, 0.35);
}

.mark-ring {
  position: absolute;
  inset: 2px;
  border-radius: 11px;
  border: 1.5px solid rgba(13, 148, 136, 0.45);
  animation: ring-pulse 3.2s ease-in-out infinite;
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.brand-name {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--ink);
  line-height: 1.2;
}

.brand-tag {
  font-size: 11px;
  font-weight: 560;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--muted-soft);
}

.app-nav {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  padding: 3px;
  border-radius: var(--radius);
  background: rgba(15, 35, 55, 0.04);
}

.nav-item {
  position: relative;
  border: 1px solid transparent;
  background: transparent;
  color: var(--muted);
  font-size: 13px;
  font-weight: 560;
  padding: 8px 14px;
  border-radius: 9px;
  cursor: pointer;
  font-family: inherit;
  transition:
    color 0.18s ease,
    background 0.18s ease,
    border-color 0.18s ease,
    transform 0.18s ease;
}

.nav-item:hover {
  color: var(--accent-ink);
  background: rgba(255, 255, 255, 0.7);
}

.nav-item.active {
  color: var(--accent-ink);
  background: var(--surface-solid);
  border-color: var(--line);
  box-shadow: 0 4px 12px rgba(15, 35, 55, 0.06);
  font-weight: 650;
}

@keyframes chrome-in {
  from {
    opacity: 0;
    transform: translateY(-6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes ring-pulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.9;
  }
  50% {
    transform: scale(1.06);
    opacity: 0.55;
  }
}

@media (max-width: 640px) {
  .app-chrome {
    padding: 10px;
  }

  .brand-tag {
    display: none;
  }

  .nav-item {
    padding: 7px 11px;
  }
}
</style>
