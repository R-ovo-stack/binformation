<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createExecutor, deleteExecutor, getExecutor, updateExecutor } from '@/api/executor'
import { listEndpointOptions } from '@/api/reference'
import { ENTITY_STATUS_OPTIONS } from '@/types/asset'
import { confirmImpactDelete } from '@/utils/impactConfirm'
import {
  EXECUTOR_KIND_OPTIONS,
  emptyExecutorForm,
  type ExecutorSavePayload,
} from '@/types/executor'
import type { EndpointOption } from '@/types/flow'
import ImpactAnalysisPanel from '@/components/ImpactAnalysisPanel.vue'

const props = defineProps<{ id?: string }>()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<ExecutorSavePayload>(emptyExecutorForm())
const hosts = ref<EndpointOption[]>([])

const isEdit = computed(() => Boolean(props.id && props.id !== 'new'))
const pageTitle = computed(() => (isEdit.value ? '编辑程序/脚本' : '新建程序/脚本'))

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  kind: [{ required: true, message: '请选择类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

function hostLabel(ep: EndpointOption) {
  return `${ep.breadcrumb} / ${ep.name}`
}

async function load() {
  loading.value = true
  try {
    hosts.value = await listEndpointOptions('HOST')
    if (isEdit.value && props.id) {
      const detail = await getExecutor(Number(props.id))
      Object.assign(form, {
        name: detail.name,
        code: detail.code,
        kind: detail.kind,
        defaultHostId: detail.defaultHostId ?? null,
        status: detail.status,
        owner: detail.owner ?? null,
        remark: detail.remark ?? null,
      })
    } else {
      Object.assign(form, emptyExecutorForm())
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value && props.id) {
      await updateExecutor(Number(props.id), form)
      ElMessage.success('已保存')
    } else {
      await createExecutor(form)
      ElMessage.success('已创建')
      void router.replace({ name: 'executors' })
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (!isEdit.value || !props.id) return
  try {
    const ok = await confirmImpactDelete({
      entityType: 'EXECUTOR',
      entityId: Number(props.id),
      entityLabel: form.name || `程序 #${props.id}`,
      title: '删除程序/脚本',
    })
    if (!ok) return
    await deleteExecutor(Number(props.id))
    ElMessage.success('已删除')
    void router.push({ name: 'executors' })
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e instanceof Error ? e.message : '删除失败')
    }
  }
}

onMounted(load)
watch(() => props.id, load)
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="topbar">
      <div>
        <el-button link @click="router.push({ name: 'executors' })">← 返回列表</el-button>
        <h1>{{ pageTitle }}</h1>
      </div>
      <div class="actions">
        <el-button v-if="isEdit" type="danger" plain @click="remove">删除</el-button>
        <el-button @click="router.push({ name: 'executors' })">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </header>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" class="card">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="如 quote-cross-sync" />
      </el-form-item>
      <el-form-item label="编码" prop="code">
        <el-input v-model="form.code" placeholder="唯一编码，可与名称相同" />
      </el-form-item>
      <el-form-item label="类型" prop="kind">
        <el-select v-model="form.kind" style="width: 100%">
          <el-option v-for="o in EXECUTOR_KIND_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="默认主机">
        <el-select
          v-model="form.defaultHostId"
          filterable
          clearable
          placeholder="可选，流向步骤可覆盖"
          style="width: 100%"
        >
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
    </el-form>

    <ImpactAnalysisPanel
      v-if="isEdit && props.id"
      entity-type="EXECUTOR"
      :entity-id="Number(props.id)"
      action="UPDATE"
    />
  </div>
</template>

<style scoped>
.page {
  max-width: 880px;
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

.actions {
  display: flex;
  gap: 8px;
}

.card {
  background: var(--surface-raised);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow-panel);
  padding: 20px;
  backdrop-filter: blur(8px);
}
</style>
