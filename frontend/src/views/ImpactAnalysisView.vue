<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listAssets } from '@/api/asset'
import { listDerivationsByAsset } from '@/api/derivation'
import { listEndpoints } from '@/api/endpoint'
import { listExecutors } from '@/api/executor'
import { listFlowsByAsset } from '@/api/flow'
import AppNav from '@/components/AppNav.vue'
import ImpactAnalysisPanel from '@/components/ImpactAnalysisPanel.vue'
import type { ImpactAction, ImpactEntityType } from '@/types/impact'
import type { DataAsset } from '@/types/graph'

type Option = { value: number; label: string }

const ENTITY_OPTIONS: { value: ImpactEntityType; label: string }[] = [
  { value: 'ENDPOINT', label: '落点' },
  { value: 'ASSET', label: '数据资产' },
  { value: 'FLOW', label: '流向' },
  { value: 'EXECUTOR', label: '程序/脚本' },
  { value: 'DERIVATION', label: '派生' },
]

const route = useRoute()
const router = useRouter()

const entityType = ref<ImpactEntityType>('ENDPOINT')
const entityId = ref<number | null>(null)
const action = ref<ImpactAction>('DELETE')
const loadingOptions = ref(false)
const analyzed = ref(false)
const options = ref<Option[]>([])
const assets = ref<DataAsset[]>([])
const cascadeAssetId = ref<number | null>(null)

const actionOptions = computed(() => {
  if (entityType.value === 'ENDPOINT') {
    return [
      { value: 'DELETE' as const, label: '删除影响' },
      { value: 'UPDATE' as const, label: '变更影响' },
    ]
  }
  return [{ value: 'DELETE' as const, label: '删除影响' }]
})

const needsCascade = computed(
  () => entityType.value === 'FLOW' || entityType.value === 'DERIVATION',
)

const selectedLabel = computed(() => {
  const hit = options.value.find((o) => o.value === entityId.value)
  return hit?.label ?? (entityId.value != null ? `#${entityId.value}` : '')
})

async function loadAssets() {
  assets.value = await listAssets()
}

async function loadOptions() {
  loadingOptions.value = true
  options.value = []
  try {
    if (entityType.value === 'ENDPOINT') {
      const list = await listEndpoints()
      options.value = list.map((e) => ({
        value: e.id,
        label: `${e.breadcrumb || e.name} (#${e.id})`,
      }))
    } else if (entityType.value === 'ASSET') {
      await loadAssets()
      options.value = assets.value.map((a) => ({
        value: a.id,
        label: `${a.name} (#${a.id})`,
      }))
    } else if (entityType.value === 'EXECUTOR') {
      const list = await listExecutors()
      options.value = list.map((e) => ({
        value: e.id,
        label: `${e.name} (${e.code})`,
      }))
    } else if (needsCascade.value) {
      await loadAssets()
      if (cascadeAssetId.value != null) {
        await loadCascadeOptions(cascadeAssetId.value)
      }
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载对象列表失败')
  } finally {
    loadingOptions.value = false
  }
}

async function loadCascadeOptions(assetId: number) {
  if (entityType.value === 'FLOW') {
    const flows = await listFlowsByAsset(assetId)
    options.value = flows.map((f) => ({
      value: f.id,
      label: `流向 #${f.id} · ${f.sourceEndpointLabel} → ${f.targetEndpointLabel}${f.primary ? ' · 主' : ''}`,
    }))
  } else if (entityType.value === 'DERIVATION') {
    const list = await listDerivationsByAsset(assetId)
    options.value = list.map((d) => ({
      value: d.id,
      label: `${d.name} (#${d.id})`,
    }))
  }
}

function runAnalyze() {
  if (entityId.value == null) {
    ElMessage.warning('请先选择要分析的对象')
    return
  }
  analyzed.value = true
  void router.replace({
    query: {
      type: entityType.value,
      id: String(entityId.value),
      action: action.value,
      ...(cascadeAssetId.value != null ? { assetId: String(cascadeAssetId.value) } : {}),
    },
  })
}

function applyQuery() {
  const type = String(route.query.type || '').toUpperCase() as ImpactEntityType
  if (ENTITY_OPTIONS.some((o) => o.value === type)) {
    entityType.value = type
  }
  const act = String(route.query.action || '').toUpperCase() as ImpactAction
  if (act === 'DELETE' || act === 'UPDATE') {
    action.value = entityType.value === 'ENDPOINT' ? act : 'DELETE'
  }
  const aid = Number(route.query.assetId)
  if (Number.isFinite(aid) && aid > 0) {
    cascadeAssetId.value = aid
  }
  const id = Number(route.query.id)
  if (Number.isFinite(id) && id > 0) {
    entityId.value = id
  }
}

watch(entityType, async () => {
  entityId.value = null
  analyzed.value = false
  cascadeAssetId.value = null
  options.value = []
  if (entityType.value !== 'ENDPOINT' && action.value === 'UPDATE') {
    action.value = 'DELETE'
  }
  await loadOptions()
})

watch(cascadeAssetId, async (aid) => {
  entityId.value = null
  analyzed.value = false
  options.value = []
  if (aid == null || !needsCascade.value) return
  loadingOptions.value = true
  try {
    await loadCascadeOptions(aid)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loadingOptions.value = false
  }
})

onMounted(async () => {
  applyQuery()
  await loadOptions()
  if (entityId.value != null) {
    analyzed.value = true
  }
})
</script>

<template>
  <div class="page">
    <AppNav />
    <header class="topbar">
      <div>
        <h1>变更影响分析</h1>
        <p class="meta">选择对象与操作类型，查看删除/变更会波及哪些流向、资产、落点与派生</p>
      </div>
    </header>

    <section class="page-panel query-panel">
      <el-form label-width="88px" class="query-form" @submit.prevent="runAnalyze">
        <el-form-item label="对象类型">
          <el-radio-group v-model="entityType">
            <el-radio-button v-for="o in ENTITY_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="needsCascade" label="所属资产">
          <el-select
            v-model="cascadeAssetId"
            filterable
            clearable
            placeholder="先选资产"
            style="width: min(420px, 100%)"
          >
            <el-option
              v-for="a in assets"
              :key="a.id"
              :label="`${a.name} (#${a.id})`"
              :value="a.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="分析对象">
          <el-select
            v-model="entityId"
            filterable
            clearable
            :loading="loadingOptions"
            :disabled="needsCascade && cascadeAssetId == null"
            placeholder="搜索并选择"
            style="width: min(520px, 100%)"
          >
            <el-option
              v-for="o in options"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="操作类型">
          <el-radio-group v-model="action">
            <el-radio-button v-for="o in actionOptions" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :disabled="entityId == null" native-type="submit">
            开始分析
          </el-button>
        </el-form-item>
      </el-form>
    </section>

    <ImpactAnalysisPanel
      v-if="analyzed && entityId != null"
      :entity-type="entityType"
      :entity-id="entityId"
      :action="action"
      :key="`${entityType}-${entityId}-${action}`"
    />

    <section v-else class="page-panel hint-panel">
      <p>选择上方对象后点击「开始分析」；结果中的条目可点击跳转到对应流程、资产或落点。</p>
      <p v-if="selectedLabel" class="muted">当前：{{ selectedLabel }}</p>
    </section>
  </div>
</template>

<style scoped>
.topbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

h1 {
  margin: 0 0 6px;
  font-family: var(--font-display);
  font-size: 28px;
  letter-spacing: -0.02em;
}

.meta {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
}

.query-panel {
  padding: 18px 20px 6px;
}

.query-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.hint-panel {
  margin-top: 16px;
  padding: 18px 20px;
  color: var(--muted);
  font-size: 14px;
}

.hint-panel p {
  margin: 0;
}

.hint-panel p + p {
  margin-top: 8px;
}

.muted {
  color: var(--muted-soft);
  font-size: 13px;
}
</style>
