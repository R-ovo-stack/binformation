<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchLedger } from '@/api/search'
import AppNav from '@/components/AppNav.vue'
import { SEARCH_ENTITY_LABELS, type SearchGroup, type SearchHit } from '@/types/search'
import { resolveSearchHitRoute } from '@/utils/searchNav'

const route = useRoute()
const router = useRouter()

const query = ref('')
const loading = ref(false)
const groups = ref<SearchGroup[]>([])
const total = ref(0)

const hasQuery = computed(() => query.value.trim().length > 0)

async function runSearch() {
  const q = query.value.trim()
  if (!q) {
    groups.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const result = await searchLedger(q, 20)
    groups.value = result.groups
    total.value = result.total
  } catch {
    groups.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function submit() {
  void router.replace({ query: { q: query.value.trim() || undefined } })
  void runSearch()
}

function openHit(hit: SearchHit) {
  const routeTo = resolveSearchHitRoute(hit)
  if (!routeTo) return
  void router.push(routeTo)
}

onMounted(() => {
  query.value = String(route.query.q ?? '')
  if (query.value.trim()) {
    void runSearch()
  }
})

watch(
  () => route.query.q,
  (q) => {
    const next = String(q ?? '')
    if (next !== query.value) {
      query.value = next
      void runSearch()
    }
  },
)
</script>

<template>
  <div class="page">
    <AppNav />
    <header class="topbar">
      <div>
        <h1>全局搜索</h1>
        <p class="meta">按名称、编码或 ID 搜索资产、落点、流向、程序与派生</p>
      </div>
    </header>

    <section class="page-panel query-panel">
      <form class="query-form" @submit.prevent="submit">
        <el-input
          v-model="query"
          clearable
          size="large"
          placeholder="输入关键词，如 kdc、21、quote-cross-sync"
          @clear="submit"
        >
          <template #append>
            <el-button type="primary" :loading="loading" native-type="submit">搜索</el-button>
          </template>
        </el-input>
      </form>
    </section>

    <section v-loading="loading" class="results">
      <p v-if="!hasQuery" class="hint">在上方输入关键词，或使用导航栏 ⌘K 快捷搜索。</p>
      <p v-else-if="!loading && total === 0" class="hint">未找到「{{ query.trim() }}」相关结果。</p>
      <p v-else-if="total > 0" class="summary">共 {{ total }} 条结果</p>

      <div v-for="group in groups" :key="group.entityType" class="result-group page-panel">
        <h2>{{ SEARCH_ENTITY_LABELS[group.entityType] ?? group.label }} · {{ group.count }}</h2>
        <button
          v-for="hit in group.items"
          :key="`${hit.entityType}-${hit.entityId}`"
          type="button"
          class="result-hit"
          @click="openHit(hit)"
        >
          <span class="label">{{ hit.label }}</span>
          <span v-if="hit.subtitle" class="sub">{{ hit.subtitle }}</span>
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.topbar {
  margin-bottom: 16px;
}

h1 {
  margin: 0 0 6px;
  font-family: var(--font-display);
  font-size: 28px;
}

.meta {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
}

.query-panel {
  padding: 16px 18px;
}

.query-form :deep(.el-input-group__append) {
  background: var(--el-color-primary);
}

.results {
  min-height: 120px;
}

.summary {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--muted);
}

.hint {
  margin: 0;
  padding: 18px 20px;
  border-radius: var(--radius);
  background: var(--surface-raised);
  border: 1px dashed var(--line);
  color: var(--muted);
  font-size: 14px;
}

.result-group {
  margin-bottom: 12px;
  padding: 14px 16px;
}

.result-group h2 {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 650;
  color: var(--ink-soft);
}

.result-hit {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: var(--accent-softer);
  text-align: left;
  cursor: pointer;
  font: inherit;
  color: inherit;
}

.result-hit + .result-hit {
  margin-top: 6px;
}

.result-hit:hover {
  border-color: rgba(13, 148, 136, 0.35);
  background: var(--accent-soft);
}

.label {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-soft);
}

.sub {
  font-size: 12px;
  color: var(--muted);
}
</style>
