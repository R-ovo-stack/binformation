<script setup lang="ts">
import type { ImpactAnalysis, ImpactItem } from '@/types/impact'
import { impactItemLinkLabel, resolveImpactItemRoute } from '@/utils/impactNav'

const props = defineProps<{
  analysis: ImpactAnalysis
}>()

const emit = defineEmits<{
  openItem: [item: ImpactItem]
  openFull: []
}>()

function canNavigate(item: ImpactItem): boolean {
  return resolveImpactItemRoute(item) != null
}

function groups() {
  return [...props.analysis.blockers, ...props.analysis.warnings]
}
</script>

<template>
  <div class="impact-delete-body">
    <p class="summary">{{ analysis.summary }}</p>

    <div v-for="group in groups()" :key="group.kind" class="group" :data-severity="group.severity">
      <p class="group-title">{{ group.message }}</p>
      <ul v-if="group.items.length" class="items">
        <li v-for="item in group.items.slice(0, 8)" :key="`${group.kind}-${item.id}-${item.label}`">
          <button
            v-if="canNavigate(item)"
            type="button"
            class="item-link"
            @click="emit('openItem', item)"
          >
            <span>{{ item.label }}</span>
            <span v-if="item.assetName" class="muted">· {{ item.assetName }}</span>
            <span class="go">{{ impactItemLinkLabel(item) }} →</span>
          </button>
          <span v-else class="item-plain">
            {{ item.label }}
            <span v-if="item.assetName" class="muted">· {{ item.assetName }}</span>
          </span>
        </li>
        <li v-if="group.items.length > 8" class="more">… 另有 {{ group.items.length - 8 }} 条</li>
      </ul>
    </div>

    <button type="button" class="full-link" @click="emit('openFull')">
      在影响分析页打开 →
    </button>
  </div>
</template>

<style scoped>
.impact-delete-body {
  text-align: left;
  max-width: 480px;
  white-space: normal;
}

.summary {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
}

.group + .group {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.group-title {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
}

.group[data-severity='BLOCKER'] .group-title {
  color: var(--el-color-danger);
}

.items {
  margin: 0;
  padding: 0;
  list-style: none;
}

.items li + li {
  margin-top: 4px;
}

.item-link {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 8px;
  width: 100%;
  padding: 5px 8px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  color: inherit;
  font: inherit;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}

.item-link:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.go {
  margin-left: auto;
  font-size: 11px;
  color: var(--el-color-primary);
  font-weight: 600;
  white-space: nowrap;
}

.item-plain {
  display: block;
  padding: 4px 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.muted {
  color: var(--el-text-color-placeholder);
}

.more {
  padding: 4px 8px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.full-link {
  display: inline-flex;
  margin-top: 12px;
  padding: 0;
  border: none;
  background: none;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.full-link:hover {
  text-decoration: underline;
}
</style>
