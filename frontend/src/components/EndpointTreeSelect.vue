<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getEndpointTypeMeta } from '@/api/endpoint'
import type { EndpointOption } from '@/types/flow'
import {
  buildEndpointSelectTree,
  type EndpointSelectTreeNode,
} from '@/utils/endpointSelectTree'

const props = withDefaults(
  defineProps<{
    modelValue: number | null
    options: EndpointOption[]
    placeholder?: string
    clearable?: boolean
    /** 仅允许选择这些类型；空则全部可选 */
    selectableTypes?: string[]
  }>(),
  {
    placeholder: '选择落点',
    clearable: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
}>()

const typeLabels = ref<Record<string, string>>({})

const treeData = computed(() => {
  const roots = buildEndpointSelectTree(props.options, typeLabels.value)
  if (!props.selectableTypes?.length) return roots
  const allow = new Set(props.selectableTypes)
  function mark(nodes: EndpointSelectTreeNode[]): EndpointSelectTreeNode[] {
    return nodes.map((n) => ({
      ...n,
      disabled: !allow.has(n.type),
      children: n.children ? mark(n.children) : undefined,
    }))
  }
  return mark(roots)
})

const selectedPath = computed(() => {
  const id = props.modelValue
  if (id == null) return ''
  const ep = props.options.find((o) => o.id === id)
  if (!ep) return ''
  const t = typeLabels.value[ep.type] ?? ep.type
  return `${ep.breadcrumb} / ${ep.name}（${t}）`
})

function filterNode(query: string, data: EndpointSelectTreeNode) {
  const q = query.trim().toLowerCase()
  if (!q) return true
  return data.filterText.includes(q) || String(data.id).includes(q)
}

async function loadLabels() {
  try {
    const meta = await getEndpointTypeMeta()
    typeLabels.value = meta.labels
  } catch {
    typeLabels.value = {}
  }
}

onMounted(loadLabels)

function onUpdate(value: number | string | null | undefined) {
  if (value == null || value === '') {
    emit('update:modelValue', null)
    return
  }
  emit('update:modelValue', Number(value))
}
</script>

<template>
  <div class="wrap">
    <el-tree-select
      :model-value="modelValue ?? undefined"
      class="endpoint-tree-select"
      :data="treeData"
      node-key="id"
      filterable
      default-expand-all
      check-strictly
      :clearable="clearable"
      :placeholder="placeholder"
      :filter-node-method="filterNode"
      :props="{
        value: 'id',
        label: 'label',
        children: 'children',
        disabled: 'disabled',
      }"
      style="width: 100%"
      @update:model-value="onUpdate"
    >
      <template #default="{ data }: { data: EndpointSelectTreeNode }">
        <span
          class="node"
          :title="data.breadcrumb ? `${data.breadcrumb} / ${data.label}` : data.label"
        >
          <span class="name">{{ data.label }}</span>
          <span class="type">{{ data.typeLabel }}</span>
        </span>
      </template>
    </el-tree-select>
    <p v-if="selectedPath" class="path" :title="selectedPath">{{ selectedPath }}</p>
  </div>
</template>

<style scoped>
.wrap {
  width: 100%;
}

.node {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
}

.name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--muted-soft);
}

.path {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--muted);
  line-height: 1.4;
  word-break: break-all;
}

:deep(.el-tree-select__popper .el-tree),
:deep(.el-select-dropdown .el-tree) {
  min-width: 320px;
  max-height: 360px;
  padding: 4px 0;
}
</style>
