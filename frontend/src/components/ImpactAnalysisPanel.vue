<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { loadImpactUpdate } from '@/utils/impactConfirm'
import type { ImpactAnalysis, ImpactEntityType } from '@/types/impact'

const props = defineProps<{
  entityType: ImpactEntityType
  entityId: number | null
}>()

const loading = ref(false)
const analysis = ref<ImpactAnalysis | null>(null)

async function load() {
  if (!props.entityId) {
    analysis.value = null
    return
  }
  loading.value = true
  try {
    analysis.value = await loadImpactUpdate(props.entityType, props.entityId)
  } catch {
    analysis.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => [props.entityType, props.entityId] as const, load)
</script>

<template>
  <section v-if="entityId" v-loading="loading" class="impact-panel page-panel">
    <header class="impact-head">
      <h3>变更影响</h3>
      <el-button link size="small" :loading="loading" @click="load">刷新</el-button>
    </header>
    <p v-if="analysis" class="impact-summary">{{ analysis.summary }}</p>
    <template v-if="analysis && (analysis.warnings.length || analysis.blockers.length)">
      <div v-for="group in [...analysis.blockers, ...analysis.warnings]" :key="group.kind" class="impact-group">
        <p class="group-title">{{ group.message }}</p>
        <ul v-if="group.items.length" class="impact-items">
          <li v-for="item in group.items" :key="`${group.kind}-${item.id}-${item.label}`">
            {{ item.label }}
            <span v-if="item.assetName" class="muted">· {{ item.assetName }}</span>
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

.impact-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

h3 {
  margin: 0;
  font-size: 15px;
}

.impact-summary {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--ink-soft);
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

.impact-items {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--muted);
}

.impact-items li + li {
  margin-top: 4px;
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
