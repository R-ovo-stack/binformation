<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getAsset, listAssets } from '@/api/asset'
import { createDerivation, deleteDerivation, getDerivation, updateDerivation } from '@/api/derivation'
import { listEndpointOptions, listExecutorOptions } from '@/api/reference'
import { ENTITY_STATUS_OPTIONS } from '@/types/asset'
import { emptyDerivationForm, type DerivationSavePayload } from '@/types/derivation'
import type { DataAsset } from '@/types/graph'
import type { EndpointOption, ExecutorOption } from '@/types/flow'

const props = defineProps<{ id: string; derivationId?: string }>()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const asset = ref<DataAsset | null>(null)
const assets = ref<DataAsset[]>([])
const executors = ref<ExecutorOption[]>([])
const hosts = ref<EndpointOption[]>([])
const formRef = ref<FormInstance>()
const form = reactive<DerivationSavePayload>(emptyDerivationForm())

const assetId = computed(() => Number(props.id))
const isEdit = computed(() => Boolean(props.derivationId))
const pageTitle = computed(() => (isEdit.value ? '编辑派生/加工' : '新建派生/加工'))

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  executorId: [{ required: true, message: '请选择程序/脚本', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

function assetLabel(a: DataAsset) {
  return `${a.name} (${a.code})`
}

function hostLabel(ep: EndpointOption) {
  return `${ep.breadcrumb} / ${ep.name}`
}

async function loadReference() {
  const [allAssets, execs, hostList] = await Promise.all([
    listAssets(),
    listExecutorOptions(),
    listEndpointOptions('HOST'),
  ])
  assets.value = allAssets.filter((a) => a.id !== assetId.value)
  executors.value = execs
  hosts.value = hostList
}

async function load() {
  if (!Number.isFinite(assetId.value)) {
    ElMessage.error('无效的资产 ID')
    return
  }
  loading.value = true
  try {
    asset.value = await getAsset(assetId.value)
    await loadReference()
    if (isEdit.value && props.derivationId) {
      const detail = await getDerivation(Number(props.derivationId))
      Object.assign(form, {
        name: detail.name,
        executorId: detail.executorId,
        hostId: detail.hostId ?? null,
        status: detail.status,
        owner: detail.owner ?? null,
        remark: detail.remark ?? null,
        inputs: detail.inputs.length
          ? detail.inputs.map((i) => ({ inputAssetId: i.inputAssetId, sortOrder: i.sortOrder }))
          : [{ inputAssetId: 0, sortOrder: 0 }],
      })
    } else {
      Object.assign(form, emptyDerivationForm())
      if (executors.value.length && !form.executorId) {
        form.executorId = executors.value[0].id
      }
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function addInput() {
  form.inputs.push({ inputAssetId: 0, sortOrder: form.inputs.length })
}

function removeInput(index: number) {
  if (form.inputs.length <= 1) {
    ElMessage.warning('至少保留一个输入资产')
    return
  }
  form.inputs.splice(index, 1)
  form.inputs.forEach((item, i) => {
    item.sortOrder = i
  })
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.inputs.some((i) => !i.inputAssetId)) {
    ElMessage.warning('请为每个输入选择资产')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form,
      inputs: form.inputs.map((item, i) => ({ ...item, sortOrder: i })),
    }
    if (isEdit.value && props.derivationId) {
      await updateDerivation(Number(props.derivationId), payload)
      ElMessage.success('已保存')
    } else {
      await createDerivation(assetId.value, payload)
      ElMessage.success('已创建')
      void router.replace({ name: 'asset-derivations', params: { id: props.id } })
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (!isEdit.value || !props.derivationId) return
  try {
    await ElMessageBox.confirm('删除后不可恢复。', '删除确认', { type: 'warning' })
    await deleteDerivation(Number(props.derivationId))
    ElMessage.success('已删除')
    void router.push({ name: 'asset-derivations', params: { id: props.id } })
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e instanceof Error ? e.message : '删除失败')
    }
  }
}

onMounted(load)
watch(() => [props.id, props.derivationId], load)
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="topbar">
      <div>
        <el-button link @click="router.push({ name: 'asset-derivations', params: { id: props.id } })">
          ← 返回派生列表
        </el-button>
        <h1>{{ pageTitle }}</h1>
        <p v-if="asset" class="meta">输出资产：{{ asset.name }} ({{ asset.code }})</p>
      </div>
      <div class="actions">
        <el-button v-if="isEdit" type="danger" plain @click="remove">删除</el-button>
        <el-button @click="router.push({ name: 'asset-derivations', params: { id: props.id } })">
          取消
        </el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </header>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" class="card">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="如 ABC拼接生成数据D" />
      </el-form-item>
      <el-form-item label="程序/脚本" prop="executorId">
        <el-select v-model="form.executorId" filterable style="width: 100%">
          <el-option v-for="e in executors" :key="e.id" :label="`${e.name} (${e.code})`" :value="e.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="执行主机">
        <el-select v-model="form.hostId" filterable clearable placeholder="可选" style="width: 100%">
          <el-option v-for="h in hosts" :key="h.id" :label="hostLabel(h)" :value="h.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="form.status" style="width: 100%">
          <el-option v-for="o in ENTITY_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="责任人">
        <el-input v-model="form.owner" placeholder="可选" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
      </el-form-item>

      <div class="inputs-header">
        <h2>输入资产</h2>
        <el-button size="small" @click="addInput">添加输入</el-button>
      </div>
      <div v-for="(item, index) in form.inputs" :key="index" class="input-row">
        <el-form-item :label="`输入 ${index + 1}`" label-width="72px" class="input-field">
          <el-select v-model="item.inputAssetId" filterable placeholder="选择资产" style="width: 100%">
            <el-option v-for="a in assets" :key="a.id" :label="assetLabel(a)" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-button v-if="form.inputs.length > 1" link type="danger" @click="removeInput(index)">
          移除
        </el-button>
      </div>
    </el-form>
  </div>
</template>

<style scoped>
.page {
  max-width: 720px;
  margin: 0 auto;
  padding: 20px;
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

h1 {
  margin: 4px 0 0;
  font-size: 22px;
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
  padding: 20px;
}

.inputs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 8px 0 12px;
}

.inputs-header h2 {
  margin: 0;
  font-size: 15px;
}

.input-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.input-field {
  flex: 1;
  margin-bottom: 8px;
}
</style>
