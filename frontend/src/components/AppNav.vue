<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const items = [
  { name: 'assets', label: '数据资产', path: '/' },
  { name: 'endpoints', label: '落点', path: '/endpoints' },
  { name: 'executors', label: '程序/脚本', path: '/executors' },
] as const

const active = computed(() => {
  const n = String(route.name ?? '')
  if (n === 'assets' || n.startsWith('asset')) return 'assets'
  if (n.startsWith('endpoint')) return 'endpoints'
  if (n.startsWith('executor')) return 'executors'
  if (n.startsWith('derivation') || n === 'asset-derivations') return 'assets'
  if (n === 'asset-flows' || n.startsWith('flow')) return 'assets'
  return ''
})

function go(path: string) {
  void router.push(path)
}
</script>

<template>
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
</template>

<style scoped>
.app-nav {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.nav-item {
  border: 1px solid transparent;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-family: inherit;
}

.nav-item:hover {
  color: #0f3d36;
  background: #ecf4f1;
}

.nav-item.active {
  color: #0f3d36;
  background: #dceae4;
  border-color: #b7d0c6;
  font-weight: 600;
}
</style>
