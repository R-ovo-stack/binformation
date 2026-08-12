<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getAsset } from '@/api/asset'
import { createFlow, getFlow, updateFlow } from '@/api/flow'
import { listEndpointOptions, listExecutorOptions } from '@/api/reference'
import EndpointTreeSelect from '@/components/EndpointTreeSelect.vue'
import type { DataAsset } from '@/types/graph'
import {
  FLOW_METHOD_OPTIONS,
  FLOW_PURPOSE_OPTIONS,
  FLOW_STATUS_OPTIONS,
  emptyFlow,
  emptyPath,
  emptyStep,
  toSavePayload,
  type EndpointOption,
  type ExecutorOption,
  type FlowDetail,
} from '@/types/flow'

const props = defineProps<{
  id: string
  flowId?: string
}>()

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const asset = ref<DataAsset | null>(null)
const formRef = ref<FormInstance>()
const endpoints = ref<EndpointOption[]>([])
const executors = ref<ExecutorOption[]>([])
const form = reactive<FlowDetail>(emptyFlow(Number(props.id)))

const assetId = computed(() => Number(props.id))
const isEdit = computed(() => Boolean(props.flowId))
const pageTitle = computed(() => (isEdit.value ? '编辑流向' : '新建流向'))

const rules: FormRules = {
  sourceEndpointId: [{ required: true, message: '请选择源落点', trigger: 'change' }],
  targetEndpointId: [{ required: true, message: '请选择目标落点', trigger: 'change' }],
  purpose: [{ required: true, message: '请选择用途', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

function endpointLabel(ep: EndpointOption) {
  return `${ep.breadcrumb} / ${ep.name} (${ep.type})`
}

async function loadReferenceData() {
  const [eps, execs] = await Promise.all([listEndpointOptions(), listExecutorOptions()])
  endpoints.value = eps
  executors.value = execs
}

async function load() {
  if (!Number.isFinite(assetId.value)) {
    ElMessage.error('无效的资产 ID')
    return
  }
  loading.value = true
  try {
    asset.value = await getAsset(assetId.value)
    await loadReferenceData()
    if (isEdit.value && props.flowId) {
      const detail = await getFlow(Number(props.flowId))
      Object.assign(form, {
        ...detail,
        paths: detail.paths.length
          ? detail.paths.map((p) => ({
              ...p,
              steps: p.steps.length ? p.steps : [emptyStep(1)],
            }))
          : [emptyPath(0)],
      })
    } else {
      Object.assign(form, emptyFlow(assetId.value))
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function addPath() {
  form.paths.push(emptyPath(form.paths.length))
}

function removePath(index: number) {
  if (form.paths.length <= 1) {
    ElMessage.warning('至少保留一个路径')
    return
  }
  form.paths.splice(index, 1)
}

function addStep(pathIndex: number) {
  const path = form.paths[pathIndex]
  const nextSeq = path.steps.length ? Math.max(...path.steps.map((s) => s.seq)) + 1 : 1
  path.steps.push(emptyStep(nextSeq))
}

function removeStep(pathIndex: number, stepIndex: number) {
  const path = form.paths[pathIndex]
  if (path.steps.length <= 1) {
    ElMessage.warning('路径至少需要一个步骤')
    return
  }
  path.steps.splice(stepIndex, 1)
  path.steps.forEach((step, idx) => {
    step.seq = idx + 1
  })
}

function onExecutorChange(pathIndex: number, stepIndex: number, executorId: number | null) {
  const step = form.paths[pathIndex].steps[stepIndex]
  step.executorId = executorId
  if (!step.hostId && executorId) {
    const ex = executors.value.find((e) => e.id === executorId)
    if (ex?.defaultHostId) {
      step.hostId = ex.defaultHostId
    }
  }
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  for (const path of form.paths) {
    if (!path.name.trim()) {
      ElMessage.warning('路径名称不能为空')
      return
    }
    for (const step of path.steps) {
      if (!step.executorId) {
        ElMessage.warning(`路径「${path.name}」存在未选择程序/脚本的步骤`)
        return
      }
    }
  }

  saving.value = true
  try {
    const payload = toSavePayload(form)
    if (isEdit.value && props.flowId) {
      await updateFlow(Number(props.flowId), payload)
      ElMessage.success('已保存')
    } else {
      const created = await createFlow(assetId.value, payload)
      ElMessage.success('已创建')
      void router.replace({
        name: 'flow-edit',
        params: { id: props.id, flowId: String(created.id) },
      })
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function backToList() {
  void router.push({ name: 'asset-flows', params: { id: props.id } })
}

onMounted(load)

watch(
  () => [props.id, props.flowId],
  () => {
    void load()
  },
)
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="topbar">
      <div>
        <el-button link @click="backToList">← 返回流向列表</el-button>
        <h1>{{ pageTitle }}</h1>
        <p class="meta" v-if="asset">{{ asset.name }} · {{ asset.code }}</p>
      </div>
      <div class="actions">
        <el-button @click="backToList">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </header>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" class="form">
      <section class="card">
        <h2>流向基本信息</h2>
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item label="源落点" prop="sourceEndpointId">
              <EndpointTreeSelect
                v-model="form.sourceEndpointId"
                :options="endpoints"
                placeholder="搜索或从树中选择源落点"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="目标落点" prop="targetEndpointId">
              <EndpointTreeSelect
                v-model="form.targetEndpointId"
                :options="endpoints"
                placeholder="搜索或从树中选择目标落点"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="用途" prop="purpose">
              <el-select v-model="form.purpose" style="width: 100%">
                <el-option
                  v-for="opt in FLOW_PURPOSE_OPTIONS"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" style="width: 100%">
                <el-option
                  v-for="opt in FLOW_STATUS_OPTIONS"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="主流向">
              <el-switch v-model="form.primary" active-text="是" inactive-text="否" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="责任人">
              <el-input v-model="form.owner" placeholder="可选" />
            </el-form-item>
          </el-col>
          <el-col :xs="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
            </el-form-item>
          </el-col>
        </el-row>
      </section>

      <section class="card">
        <div class="section-head">
          <h2>路径与步骤</h2>
          <el-button size="small" @click="addPath">添加路径</el-button>
        </div>

        <div v-for="(path, pathIndex) in form.paths" :key="pathIndex" class="path-block">
          <div class="path-head">
            <strong>路径 {{ pathIndex + 1 }}</strong>
            <el-button
              v-if="form.paths.length > 1"
              link
              type="danger"
              @click="removePath(pathIndex)"
            >
              删除路径
            </el-button>
          </div>

          <el-row :gutter="12">
            <el-col :xs="24" :sm="10">
              <el-form-item label="名称" label-width="64px">
                <el-input v-model="path.name" />
              </el-form-item>
            </el-col>
            <el-col :xs="12" :sm="4">
              <el-form-item label="排序" label-width="64px">
                <el-input-number v-model="path.sortOrder" :min="0" controls-position="right" />
              </el-form-item>
            </el-col>
            <el-col :xs="12" :sm="4">
              <el-form-item label="启用" label-width="64px">
                <el-switch v-model="path.enabled" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="6">
              <el-form-item label="备注" label-width="64px">
                <el-input v-model="path.remark" placeholder="可选" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-table :data="path.steps" size="small" border class="step-table">
            <el-table-column label="#" width="56">
              <template #default="{ row }">
                <el-input-number v-model="row.seq" :min="1" controls-position="right" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="程序/脚本" min-width="160">
              <template #default="{ row, $index }">
                <el-select
                  :model-value="row.executorId"
                  filterable
                  placeholder="选择"
                  style="width: 100%"
                  @update:model-value="onExecutorChange(pathIndex, $index, $event as number | null)"
                >
                  <el-option
                    v-for="ex in executors"
                    :key="ex.id"
                    :label="`${ex.name} (${ex.kind})`"
                    :value="ex.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="部署主机" min-width="160">
              <template #default="{ row }">
                <el-select
                  v-model="row.hostId"
                  filterable
                  clearable
                  placeholder="可选"
                  style="width: 100%"
                >
                  <el-option
                    v-for="ep in endpoints.filter((e) => e.type === 'HOST')"
                    :key="ep.id"
                    :label="endpointLabel(ep)"
                    :value="ep.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="方式" min-width="140">
              <template #default="{ row }">
                <el-select v-model="row.method" style="width: 100%">
                  <el-option
                    v-for="opt in FLOW_METHOD_OPTIONS"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="120">
              <template #default="{ row }">
                <el-input v-model="row.remark" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="88" fixed="right">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeStep(pathIndex, $index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-button class="add-step" size="small" @click="addStep(pathIndex)">添加步骤</el-button>
        </div>
      </section>
    </el-form>
  </div>
</template>

<style scoped>
.page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 20px 20px 48px;
}

.topbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-end;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

h1 {
  margin: 4px 0 0;
  font-size: 22px;
  color: #0f172a;
}

.meta {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 8px;
}

.card {
  background: #fff;
  border: 1px solid #d5e0db;
  border-radius: 12px;
  padding: 16px 18px 20px;
  margin-bottom: 16px;
}

.card h2 {
  margin: 0 0 14px;
  font-size: 16px;
  color: #0f172a;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-head h2 {
  margin: 0;
}

.path-block {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px 14px 14px;
  margin-bottom: 12px;
  background: #f8faf9;
}

.path-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.step-table {
  margin-top: 4px;
}

.add-step {
  margin-top: 8px;
}

@media (max-width: 768px) {
  .page {
    padding: 12px;
  }
}
</style>
