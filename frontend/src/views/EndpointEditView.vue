<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createEndpoint,
  deleteEndpoint,
  getEndpoint,
  getEndpointParentTypes,
  getEndpointTypeMeta,
  listEndpoints,
  updateEndpoint,
} from '@/api/endpoint'
import { ENTITY_STATUS_OPTIONS } from '@/types/asset'
import {
  emptyEndpointForm,
  typeLabel,
  type EndpointSavePayload,
} from '@/types/endpoint'

const props = defineProps<{ id?: string }>()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<EndpointSavePayload>(emptyEndpointForm())
const typeLabels = ref<Record<string, string>>({})
const typeOptions = ref<string[]>([])
const parentTypeRules = ref<Record<string, string[]>>({})
const parentCandidates = ref<Array<{ id: number; label: string; type: string }>>([])

const isEdit = computed(() => Boolean(props.id && props.id !== 'new'))
const pageTitle = computed(() => (isEdit.value ? '编辑落点' : '新建落点'))
const isSecurityZone = computed(() => form.type === 'SECURITY_ZONE')

const rules: FormRules = {
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

async function loadMeta() {
  const [meta, parentTypes, allEndpoints] = await Promise.all([
    getEndpointTypeMeta(),
    getEndpointParentTypes(),
    listEndpoints(),
  ])
  typeLabels.value = meta.labels
  typeOptions.value = meta.types
  parentTypeRules.value = parentTypes
  refreshParentCandidates(allEndpoints)
}

function refreshParentCandidates(all: Awaited<ReturnType<typeof listEndpoints>>) {
  if (isSecurityZone.value) {
    parentCandidates.value = []
    form.parentId = null
    return
  }
  const allowed = parentTypeRules.value[form.type] ?? []
  parentCandidates.value = all
    .filter((ep) => allowed.includes(ep.type))
    .map((ep) => ({
      id: ep.id,
      type: ep.type,
      label: `${ep.breadcrumb} / ${ep.name} (${typeLabel(ep.type, typeLabels.value)})`,
    }))
  if (form.parentId && !parentCandidates.value.some((p) => p.id === form.parentId)) {
    form.parentId = null
  }
}

async function load() {
  loading.value = true
  try {
    await loadMeta()
    if (isEdit.value && props.id) {
      const ep = await getEndpoint(Number(props.id))
      Object.assign(form, {
        type: ep.type,
        name: ep.name,
        code: ep.code ?? null,
        parentId: ep.parentId ?? null,
        attrs: ep.attrs ?? null,
        status: ep.status,
        owner: ep.owner ?? null,
        remark: ep.remark ?? null,
      })
      refreshParentCandidates(await listEndpoints())
    } else {
      Object.assign(form, emptyEndpointForm())
      refreshParentCandidates(await listEndpoints())
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => form.type,
  async () => {
    refreshParentCandidates(await listEndpoints())
  },
)

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!isSecurityZone.value && !form.parentId) {
    ElMessage.warning('请选择父落点')
    return
  }
  saving.value = true
  try {
    const payload = { ...form, parentId: isSecurityZone.value ? null : form.parentId }
    if (isEdit.value && props.id) {
      await updateEndpoint(Number(props.id), payload)
      ElMessage.success('已保存')
    } else {
      const created = await createEndpoint(payload)
      ElMessage.success('已创建')
      void router.replace({ name: 'endpoint-edit', params: { id: String(created.id) } })
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
    await ElMessageBox.confirm('若仍被流向引用将无法删除。', '删除确认', { type: 'warning' })
    await deleteEndpoint(Number(props.id))
    ElMessage.success('已删除')
    void router.push({ name: 'endpoints' })
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
        <el-button link @click="router.push({ name: 'endpoints' })">← 返回落点列表</el-button>
        <h1>{{ pageTitle }}</h1>
      </div>
      <div class="actions">
        <el-button v-if="isEdit" type="danger" plain @click="remove">删除</el-button>
        <el-button @click="router.push({ name: 'endpoints' })">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </header>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" class="card">
      <el-form-item label="类型" prop="type">
        <el-select v-model="form.type" :disabled="isEdit" style="width: 100%">
          <el-option
            v-for="t in typeOptions"
            :key="t"
            :label="typeLabel(t, typeLabels)"
            :value="t"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="!isSecurityZone" label="父落点" required>
        <el-select v-model="form.parentId" filterable placeholder="选择父落点" style="width: 100%">
          <el-option v-for="p in parentCandidates" :key="p.id" :label="p.label" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="显示名称，如 topic-A 或 /data/in/" />
      </el-form-item>
      <el-form-item label="编码">
        <el-input v-model="form.code" placeholder="可选" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="form.status" style="width: 100%">
          <el-option v-for="o in ENTITY_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="扩展属性">
        <el-input
          v-model="form.attrs"
          type="textarea"
          :rows="3"
          placeholder='可选 JSON，如 {"topicName":"A"} 或 {"dirPath":"/data/d/"}'
        />
      </el-form-item>
      <el-form-item label="责任人">
        <el-input v-model="form.owner" placeholder="可选" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
      </el-form-item>
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
</style>
