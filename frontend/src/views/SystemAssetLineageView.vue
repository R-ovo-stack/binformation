<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listAssets } from '@/api/asset'
import {
  getAssetDownstreamSystems,
  getSystemConsumedAssets,
  listLineageSystems,
} from '@/api/lineage'
import AppNav from '@/components/AppNav.vue'
import { dataTypeLabel, statusLabel } from '@/types/asset'
import { purposeLabel } from '@/types/flow'
import {
  LINEAGE_ROLE_LABELS,
  type AssetDownstreamQuery,
  type LineageFlowRef,
  type SystemAssetQuery,
  type SystemOption,
} from '@/types/lineage'
import type { DataAsset } from '@/types/graph'

type QueryMode = 'system' | 'asset'

const route = useRoute()
const router = useRouter()

const mode = ref<QueryMode>('system')
const includeAuxiliary = ref(false)
const loading = ref(false)

const systems = ref<SystemOption[]>([])
const assets = ref<DataAsset[]>([])
const selectedSystemId = ref<number | null>(null)
const selectedAssetId = ref<number | null>(null)

const systemResult = ref<SystemAssetQuery | null>(null)
const assetResult = ref<AssetDownstreamQuery | null>(null)

const systemLabel = computed(() => {
  const hit = systems.value.find((s) => s.id === selectedSystemId.value)
  return hit ? `${hit.name}${hit.zoneName ? ` · ${hit.zoneName}` : ''}` : ''
})

function roleLabel(role: string) {
  return LINEAGE_ROLE_LABELS[role] ?? role
}

function applyQuery() {
  const m = String(route.query.mode || '')
  if (m === 'asset' || m === 'system') mode.value = m
  const sid = Number(route.query.systemId)
  if (Number.isFinite(sid) && sid > 0) selectedSystemId.value = sid
  const aid = Number(route.query.assetId)
  if (Number.isFinite(aid) && aid > 0) selectedAssetId.value = aid
  includeAuxiliary.value = String(route.query.aux) === '1'
}

function syncQuery() {
  void router.replace({
    query: {
      mode: mode.value,
      ...(mode.value === 'system' && selectedSystemId.value != null
        ? { systemId: String(selectedSystemId.value) }
        : {}),
      ...(mode.value === 'asset' && selectedAssetId.value != null
        ? { assetId: String(selectedAssetId.value) }
        : {}),
      ...(includeAuxiliary.value ? { aux: '1' } : {}),
    },
  })
}

async function loadOptions() {
  try {
    const [sys, ast] = await Promise.all([listLineageSystems(), listAssets()])
    systems.value = sys
    assets.value = ast
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载选项失败')
  }
}

async function runQuery() {
  loading.value = true
  try {
    if (mode.value === 'system') {
      if (selectedSystemId.value == null) {
        ElMessage.warning('请先选择系统')
        return
      }
      systemResult.value = await getSystemConsumedAssets(selectedSystemId.value, includeAuxiliary.value)
      assetResult.value = null
    } else {
      if (selectedAssetId.value == null) {
        ElMessage.warning('请先选择数据资产')
        return
      }
      assetResult.value = await getAssetDownstreamSystems(selectedAssetId.value, includeAuxiliary.value)
      systemResult.value = null
    }
    syncQuery()
  } catch (e) {
    systemResult.value = null
    assetResult.value = null
    ElMessage.error(e instanceof Error ? e.message : '查询失败')
  } finally {
    loading.value = false
  }
}

function openAsset(assetId: number) {
  void router.push({ name: 'asset-edit', params: { id: String(assetId) } })
}

function openFlows(assetId: number) {
  void router.push({ name: 'asset-flows', params: { id: String(assetId) } })
}

function openFlow(flow: LineageFlowRef) {
  void router.push({
    name: 'flow-visual-edit',
    params: { id: String(flow.assetId), flowId: String(flow.id) },
  })
}

function openSystem(systemId: number | null) {
  if (systemId == null) return
  void router.push({ name: 'endpoint-edit', params: { id: String(systemId) } })
}

function queryThisSystem(systemId: number | null) {
  if (systemId == null) return
  mode.value = 'system'
  selectedSystemId.value = systemId
  void runQuery()
}

function queryThisAsset(assetId: number) {
  mode.value = 'asset'
  selectedAssetId.value = assetId
  void runQuery()
}

onMounted(async () => {
  applyQuery()
  await loadOptions()
  if (
    (mode.value === 'system' && selectedSystemId.value != null) ||
    (mode.value === 'asset' && selectedAssetId.value != null)
  ) {
    await runQuery()
  }
})

watch(mode, () => {
  systemResult.value = null
  assetResult.value = null
})
</script>

<template>
  <div class="page">
    <AppNav />
    <header class="topbar">
      <div>
        <h1>系统 × 资产供需</h1>
        <p class="meta">快速查看某系统获取了哪些数据资产，以及某资产流向了哪些下游应用系统</p>
      </div>
    </header>

    <section class="page-panel query-panel">
      <el-form label-width="108px" @submit.prevent="runQuery">
        <el-form-item label="查询方向">
          <el-radio-group v-model="mode">
            <el-radio-button value="system">系统获取了哪些资产</el-radio-button>
            <el-radio-button value="asset">资产流向哪些下游系统</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="mode === 'system'" label="应用系统">
          <el-select
            v-model="selectedSystemId"
            filterable
            clearable
            placeholder="选择或搜索系统"
            style="width: min(520px, 100%)"
          >
            <el-option
              v-for="s in systems"
              :key="s.id"
              :label="`${s.name}${s.zoneName ? ' · ' + s.zoneName : ''} (#${s.id})`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-else label="数据资产">
          <el-select
            v-model="selectedAssetId"
            filterable
            clearable
            placeholder="选择或搜索资产"
            style="width: min(520px, 100%)"
          >
            <el-option
              v-for="a in assets"
              :key="a.id"
              :label="`${a.name} (${a.code})`"
              :value="a.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="辅助流向">
          <el-switch v-model="includeAuxiliary" active-text="包含 AUX" />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
            :disabled="mode === 'system' ? selectedSystemId == null : selectedAssetId == null"
          >
            查询
          </el-button>
        </el-form-item>
      </el-form>
    </section>

    <section v-loading="loading" class="results">
      <template v-if="mode === 'system' && systemResult">
        <p class="summary">
          「{{ systemResult.systemName }}」
          <span v-if="systemResult.zoneName" class="muted">· {{ systemResult.zoneName }}</span>
          作为流向目标，共获取
          <strong>{{ systemResult.assetCount }}</strong>
          项数据资产
        </p>
        <el-empty v-if="!systemResult.assets.length" description="该系统当前没有作为目标的流向" />
        <article v-for="row in systemResult.assets" :key="row.assetId" class="page-panel card">
          <header class="card-head">
            <div>
              <h2>{{ row.assetName }}</h2>
              <p class="sub">
                {{ row.assetCode }} · {{ dataTypeLabel(row.dataType) }} · {{ statusLabel(row.status) }}
                · {{ roleLabel(row.role) }}
              </p>
            </div>
            <div class="card-actions">
              <el-button size="small" @click="openAsset(row.assetId)">编辑资产</el-button>
              <el-button size="small" @click="openFlows(row.assetId)">流向</el-button>
              <el-button size="small" type="primary" plain @click="queryThisAsset(row.assetId)">
                看下游系统
              </el-button>
            </div>
          </header>
          <ul class="flow-list">
            <li v-for="flow in row.flows" :key="flow.id">
              <button type="button" class="flow-link" @click="openFlow(flow)">
                <span class="purpose">{{ purposeLabel(flow.purpose) }}</span>
                <span>{{ flow.source.breadcrumb || flow.source.name }} → {{ flow.target.breadcrumb || flow.target.name }}</span>
                <span class="go">流向 #{{ flow.id }} →</span>
              </button>
            </li>
          </ul>
        </article>
      </template>

      <template v-else-if="mode === 'asset' && assetResult">
        <p class="summary">
          「{{ assetResult.assetName }}」流向
          <strong>{{ assetResult.systemCount }}</strong>
          个下游应用系统
        </p>
        <el-empty v-if="!assetResult.systems.length" description="该资产当前没有下游系统流向" />
        <article v-for="row in assetResult.systems" :key="`${row.systemId ?? 'na'}-${row.systemName}`" class="page-panel card">
          <header class="card-head">
            <div>
              <h2>{{ row.systemName }}</h2>
              <p class="sub">
                <span v-if="row.zoneName">{{ row.zoneName }} · </span>
                {{ roleLabel(row.role) }}
                <span v-if="row.systemBreadcrumb" class="muted"> · {{ row.systemBreadcrumb }}</span>
              </p>
            </div>
            <div class="card-actions">
              <el-button v-if="row.systemId" size="small" @click="openSystem(row.systemId)">打开系统</el-button>
              <el-button
                v-if="row.systemId"
                size="small"
                type="primary"
                plain
                @click="queryThisSystem(row.systemId)"
              >
                看该系统获取的资产
              </el-button>
            </div>
          </header>
          <ul class="flow-list">
            <li v-for="flow in row.flows" :key="flow.id">
              <button type="button" class="flow-link" @click="openFlow(flow)">
                <span class="purpose">{{ purposeLabel(flow.purpose) }}</span>
                <span>{{ flow.source.breadcrumb || flow.source.name }} → {{ flow.target.breadcrumb || flow.target.name }}</span>
                <span class="go">流向 #{{ flow.id }} →</span>
              </button>
            </li>
          </ul>
        </article>
      </template>

      <p v-else-if="!loading" class="hint">
        {{
          mode === 'system'
            ? '选择一个应用系统后查询：将列出以该系统（及其下属落点）为流向目标的全部数据资产。'
            : '选择一项数据资产后查询：将列出该资产流向目标所属的下游应用系统。'
        }}
        <span v-if="mode === 'system' && systemLabel" class="muted">当前：{{ systemLabel }}</span>
      </p>
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
  padding: 16px 18px 4px;
  margin-bottom: 16px;
}

.summary {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--ink-soft);
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

.card {
  padding: 16px 18px;
  margin-bottom: 12px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  flex-wrap: wrap;
}

h2 {
  margin: 0 0 4px;
  font-size: 17px;
}

.sub {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
}

.card-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.flow-list {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.flow-list li + li {
  margin-top: 6px;
}

.flow-link {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 9px;
  background: var(--accent-softer);
  text-align: left;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  color: inherit;
}

.flow-link:hover {
  border-color: rgba(13, 148, 136, 0.35);
  background: var(--accent-soft);
}

.purpose {
  font-weight: 650;
  color: var(--accent-ink);
}

.go {
  margin-left: auto;
  font-size: 12px;
  font-weight: 650;
  color: var(--accent-deep);
  white-space: nowrap;
}

.muted {
  color: var(--muted-soft);
}
</style>
