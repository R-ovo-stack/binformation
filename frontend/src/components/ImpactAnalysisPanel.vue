<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { analyzeImpact } from '@/api/impact'
import { impactItemLinkLabel, resolveImpactItemRoute } from '@/utils/impactNav'
import type {
  ImpactAction,
  ImpactAnalysis,
  ImpactEntityType,
  ImpactItem,
} from '@/types/impact'

const props = withDefaults(
  defineProps<{
    entityType: ImpactEntityType
    entityId: number | null
    action?: ImpactAction
    /** Compact layout for drawers / side panels */
    compact?: boolean
    /** Skip outer page-panel chrome when embedded */
    embedded?: boolean
  }>(),
  {
    action: 'UPDATE',
    compact: false,
    embedded: false,
  },
)

const router = useRouter()
const loading = ref(false)
const analysis = ref<ImpactAnalysis | null>(null)
const error = ref('')

async function load() {
  if (!props.entityId) {
    analysis.value = null
    error.value = ''
    return
  }
  loading.value = true
  error.value = ''
  try {
    analysis.value = await analyzeImpact(props.entityType, props.entityId, props.action)
  } catch (e) {
    analysis.value = null
    error.value = e instanceof Error ? e.message : '影响分析加载失败'
  } finally {
    loading.value = false
  }
}

function canNavigate(item: ImpactItem): boolean {
  return resolveImpactItemRoute(item) != null
}

function openItem(item: ImpactItem) {
  const route = resolveImpactItemRoute(item)
  if (!route) return
  void router.push(route)
}

onMounted(load)
watch(
  () => [props.entityType, props.entityId, props.action] as const,
  load,
)

defineExpose({ reload: load })
</script>

<template>
  <section
    v-if="entityId"
    v-loading="loading"
    class="impact-panel"
    :class="{ 'page-panel': !embedded, compact, embedded }"
  >
    <header class="impact-head">
      <h3>{{ action === 'DELETE' ? '删除影响' : '变更影响' }}</h3>
      <div class="head-actions">
        <RouterLink
          v-if="entityId"
          class="full-link"
          :to="{
            name: 'impact',
            query: { type: entityType, id: String(entityId), action },
          }"
        >
          完整分析
        </RouterLink>
        <el-button link size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <p v-if="error" class="impact-error">{{ error }}</p>
    <p v-else-if="analysis" class="impact-summary">
      <span
        class="badge"
        :class="analysis.canProceed ? (analysis.warnings.length ? 'warn' : 'ok') : 'block'"
      >
        {{ analysis.canProceed ? (analysis.warnings.length ? '有关联' : '可操作') : '阻塞' }}
      </span>
      {{ analysis.summary }}
    </p>

    <template v-if="analysis && (analysis.warnings.length || analysis.blockers.length)">
      <div
        v-for="group in [...analysis.blockers, ...analysis.warnings]"
        :key="group.kind"
        class="impact-group"
        :data-severity="group.severity"
      >
        <p class="group-title">{{ group.message }}</p>
        <ul v-if="group.items.length" class="impact-items">
          <li v-for="item in group.items" :key="`${group.kind}-${item.id}-${item.label}`">
            <button
              v-if="canNavigate(item)"
              type="button"
              class="item-link"
              :title="impactItemLinkLabel(item)"
              @click="openItem(item)"
            >
              <span class="item-label">{{ item.label }}</span>
              <span v-if="item.assetName" class="muted">· {{ item.assetName }}</span>
              <span class="go">{{ impactItemLinkLabel(item) }} →</span>
            </button>
            <span v-else class="item-plain">
              {{ item.label }}
              <span v-if="item.assetName" class="muted">· {{ item.assetName }}</span>
            </span>
          </li>
        </ul>
      </div>
    </template>
    <p v-else-if="analysis" class="impact-empty">暂无关联引用</p>
  </section>
</template>

<style scoped>
.impact-panel {
  margin-top: 16px;
  padding: 16px 18px;
}

.impact-panel.embedded {
  margin-top: 12px;
  padding: 12px 0 0;
  border-top: 1px dashed var(--line);
}

.impact-panel.compact {
  margin-top: 12px;
  padding: 12px 14px;
}

.impact-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.head-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.full-link {
  font-size: 12px;
  color: var(--accent-deep);
  text-decoration: none;
  padding: 0 4px;
}

.full-link:hover {
  text-decoration: underline;
}

h3 {
  margin: 0;
  font-size: 15px;
  font-family: var(--font-display);
}

.compact h3 {
  font-size: 14px;
}

.impact-summary {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--ink-soft);
  line-height: 1.45;
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}

.badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  flex-shrink: 0;
}

.badge.ok {
  background: var(--accent-soft);
  color: var(--accent-ink);
}

.badge.warn {
  background: var(--warn-soft);
  color: #9a3412;
}

.badge.block {
  background: #fef2f2;
  color: #b91c1c;
}

.impact-error {
  margin: 0;
  font-size: 12px;
  color: #b91c1c;
}

.impact-group + .impact-group {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
}

.group-title {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
}

.impact-group[data-severity='BLOCKER'] .group-title {
  color: #b91c1c;
}

.impact-items {
  margin: 0;
  padding-left: 0;
  list-style: none;
  font-size: 12px;
  color: var(--muted);
}

.impact-items li + li {
  margin-top: 4px;
}

.item-link {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 8px;
  width: 100%;
  padding: 6px 8px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: var(--accent-softer);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease;
}

.item-link:hover {
  border-color: rgba(13, 148, 136, 0.35);
  background: var(--accent-soft);
}

.item-label {
  color: var(--ink-soft);
  font-weight: 500;
}

.go {
  margin-left: auto;
  font-size: 11px;
  color: var(--accent-deep);
  font-weight: 600;
  white-space: nowrap;
}

.item-plain {
  display: block;
  padding: 4px 8px;
}

.muted {
  color: var(--muted-soft);
}

.impact-empty {
  margin: 0;
  font-size: 12px;
  color: var(--muted-soft);
}
</style>
