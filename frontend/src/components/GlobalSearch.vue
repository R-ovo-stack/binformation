<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { searchLedger } from '@/api/search'
import { SEARCH_ENTITY_LABELS, type SearchGroup, type SearchHit } from '@/types/search'
import { resolveSearchHitRoute, searchPageQuery } from '@/utils/searchNav'

const router = useRouter()

const inputRef = ref<HTMLInputElement | null>(null)
const rootRef = ref<HTMLElement | null>(null)
const query = ref('')
const loading = ref(false)
const open = ref(false)
const groups = ref<SearchGroup[]>([])
const total = ref(0)
const activeIndex = ref(-1)

let debounceTimer: ReturnType<typeof setTimeout> | undefined

const flatHits = computed(() => groups.value.flatMap((g) => g.items))
const showDropdown = computed(() => open.value && query.value.trim().length > 0)

function resetResults() {
  groups.value = []
  total.value = 0
  activeIndex.value = -1
}

async function runSearch(q: string) {
  const trimmed = q.trim()
  if (!trimmed) {
    resetResults()
    return
  }
  loading.value = true
  try {
    const result = await searchLedger(trimmed, 6)
    groups.value = result.groups
    total.value = result.total
    activeIndex.value = flatHits.value.length ? 0 : -1
  } catch {
    resetResults()
  } finally {
    loading.value = false
  }
}

function scheduleSearch() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    void runSearch(query.value)
  }, 220)
}

function focusInput() {
  open.value = true
  inputRef.value?.focus()
  inputRef.value?.select()
}

function closeDropdown() {
  open.value = false
  activeIndex.value = -1
}

function navigateHit(hit: SearchHit) {
  const route = resolveSearchHitRoute(hit)
  if (!route) return
  closeDropdown()
  query.value = ''
  resetResults()
  void router.push(route)
}

function openSearchPage() {
  const q = query.value.trim()
  if (!q) return
  closeDropdown()
  void router.push(searchPageQuery(q))
}

function onKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    focusInput()
    return
  }
  if (!showDropdown.value) return

  if (event.key === 'Escape') {
    closeDropdown()
    inputRef.value?.blur()
    return
  }
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    if (!flatHits.value.length) return
    activeIndex.value = (activeIndex.value + 1) % flatHits.value.length
    return
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault()
    if (!flatHits.value.length) return
    activeIndex.value = (activeIndex.value - 1 + flatHits.value.length) % flatHits.value.length
    return
  }
  if (event.key === 'Enter') {
    event.preventDefault()
    const hit = flatHits.value[activeIndex.value]
    if (hit) {
      navigateHit(hit)
    } else {
      openSearchPage()
    }
  }
}

function onDocumentClick(event: MouseEvent) {
  if (!rootRef.value?.contains(event.target as Node)) {
    closeDropdown()
  }
}

function hitIndex(hit: SearchHit): number {
  return flatHits.value.findIndex(
    (h) => h.entityType === hit.entityType && h.entityId === hit.entityId,
  )
}

watch(query, () => {
  if (!query.value.trim()) {
    resetResults()
    return
  }
  open.value = true
  scheduleSearch()
})

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('click', onDocumentClick)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('click', onDocumentClick)
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<template>
  <div ref="rootRef" class="global-search">
    <label class="search-shell" :class="{ open: showDropdown }">
      <span class="icon" aria-hidden="true">⌕</span>
      <input
        ref="inputRef"
        v-model="query"
        type="search"
        class="search-input"
        placeholder="搜索资产、落点、流向…"
        autocomplete="off"
        spellcheck="false"
        @focus="open = true"
      />
      <kbd class="hint" aria-hidden="true">⌘K</kbd>
    </label>

    <div v-if="showDropdown" class="dropdown" role="listbox">
      <p v-if="loading" class="status">搜索中…</p>
      <template v-else-if="groups.length">
        <div v-for="group in groups" :key="group.entityType" class="group">
          <p class="group-label">
            {{ SEARCH_ENTITY_LABELS[group.entityType] ?? group.label }}
            <span class="count">{{ group.count }}</span>
          </p>
          <button
            v-for="hit in group.items"
            :key="`${hit.entityType}-${hit.entityId}`"
            type="button"
            class="hit"
            :class="{ active: hitIndex(hit) === activeIndex }"
            role="option"
            @mousedown.prevent="navigateHit(hit)"
          >
            <span class="hit-label">{{ hit.label }}</span>
            <span v-if="hit.subtitle" class="hit-sub">{{ hit.subtitle }}</span>
          </button>
        </div>
        <button v-if="total > flatHits.length" type="button" class="more" @mousedown.prevent="openSearchPage">
          查看全部 {{ total }} 条结果 →
        </button>
      </template>
      <p v-else class="status empty">未找到「{{ query.trim() }}」</p>
    </div>
  </div>
</template>

<style scoped>
.global-search {
  position: relative;
  flex: 1 1 220px;
  max-width: 420px;
  min-width: 180px;
}

.search-shell {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid var(--line);
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.72);
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.search-shell.open,
.search-shell:focus-within {
  border-color: rgba(13, 148, 136, 0.45);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.icon {
  color: var(--muted-soft);
  font-size: 14px;
  line-height: 1;
}

.search-input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  font: inherit;
  font-size: 13px;
  color: var(--ink);
  outline: none;
}

.search-input::placeholder {
  color: var(--muted-soft);
}

.search-input::-webkit-search-cancel-button {
  display: none;
}

.hint {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: rgba(15, 35, 55, 0.04);
  color: var(--muted-soft);
  font-size: 10px;
  font-family: inherit;
  line-height: 1.2;
  white-space: nowrap;
}

.dropdown {
  position: absolute;
  z-index: 40;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  max-height: min(420px, 60vh);
  overflow: auto;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface-solid);
  box-shadow: var(--shadow-soft);
}

.group + .group {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
}

.group-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 0 4px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--muted-soft);
}

.count {
  font-weight: 600;
}

.hit {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 9px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font: inherit;
  color: inherit;
}

.hit:hover,
.hit.active {
  border-color: rgba(13, 148, 136, 0.25);
  background: var(--accent-softer);
}

.hit-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-soft);
}

.hit-sub {
  font-size: 11px;
  color: var(--muted);
  line-height: 1.35;
}

.status {
  margin: 0;
  padding: 10px 8px;
  font-size: 12px;
  color: var(--muted);
}

.status.empty {
  color: var(--muted-soft);
}

.more {
  display: block;
  width: 100%;
  margin-top: 6px;
  padding: 8px 10px;
  border: none;
  border-radius: 9px;
  background: var(--accent-softer);
  color: var(--accent-deep);
  font-size: 12px;
  font-weight: 650;
  text-align: left;
  cursor: pointer;
}

.more:hover {
  background: var(--accent-soft);
}

@media (max-width: 900px) {
  .hint {
    display: none;
  }

  .global-search {
    order: 3;
    flex-basis: 100%;
    max-width: none;
  }
}
</style>
