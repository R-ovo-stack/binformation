<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
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
import ImpactAnalysisPanel from '@/components/ImpactAnalysisPanel.vue'
import { confirmImpactDelete } from '@/utils/impactConfirm'
import {
  emptyEndpointForm,
  typeLabel,
  type EndpointSavePayload,
} from '@/types/endpoint'
import {
  attrFieldsForType,
  hasTypedAttrs,
  parseAttrsJson,
  serializeAttrs,
  suggestNameFromAttrs,
} from '@/utils/endpointAttrs'

const props = defineProps<{ id?: string }>()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<EndpointSavePayload>(emptyEndpointForm())
const typeLabels = ref<Record<string, string>>({})
const typeOptions = ref<string[]>([])
const parentTypeRules = ref<Record<string, string[]>>({})
const parentCandidates = ref<Array<{ id: number; label: string; type: string }>>([])
const attrFields = reactive<Record<string, string>>({})
const showAdvancedJson = ref(false)
const advancedJson = ref('')

const isEdit = computed(() => Boolean(props.id && props.id !== 'new'))
const pageTitle = computed(() => (isEdit.value ? '编辑落点' : '新建落点'))
const isSecurityZone = computed(() => form.type === 'SECURITY_ZONE')
const typedAttrDefs = computed(() => attrFieldsForType(form.type))
const useTypedAttrs = computed(() => hasTypedAttrs(form.type))

const parentHint = computed(() => {
  if (!form.parentId) return ''
  const p = parentCandidates.value.find((c) => c.id === form.parentId)
  return p?.label ?? `父落点 #${form.parentId}`
})

const rules: FormRules = {
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

function resetAttrFields(type: string, raw?: string | null) {
  const parsed = parseAttrsJson(raw)
  const defs = attrFieldsForType(type)
  for (const key of Object.keys(attrFields)) {
    delete attrFields[key]
  }
  for (const def of defs) {
    attrFields[def.key] = parsed[def.key] ?? ''
  }
  // Keep unknown keys in advanced JSON for typed types
  const known = new Set(defs.map((d) => d.key))
  const extras: Record<string, string> = {}
  for (const [k, v] of Object.entries(parsed)) {
    if (!known.has(k)) extras[k] = v
  }
  advancedJson.value = Object.keys(extras).length ? JSON.stringify(extras, null, 2) : ''
  showAdvancedJson.value = Object.keys(extras).length > 0
  if (!defs.length) {
    advancedJson.value = raw?.trim() ? raw : ''
    showAdvancedJson.value = true
  }
}

function syncNameFromPrimaryAttr() {
  if (form.name?.trim()) return
  const suggested = suggestNameFromAttrs(form.type, attrFields)
  if (suggested) form.name = suggested
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
      resetAttrFields(ep.type, ep.attrs)
      refreshParentCandidates(await listEndpoints())
    } else {
      Object.assign(form, emptyEndpointForm())
      const qParent = route.query.parentId
      const qChild = route.query.childType
      if (typeof qChild === 'string' && qChild) {
        form.type = qChild
      }
      if (typeof qParent === 'string' && qParent && Number.isFinite(Number(qParent))) {
        form.parentId = Number(qParent)
      }
      resetAttrFields(form.type, null)
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
  async (type, prev) => {
    if (prev && type !== prev && !isEdit.value) {
      resetAttrFields(type, null)
    }
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
  syncNameFromPrimaryAttr()
  if (!form.name?.trim()) {
    ElMessage.warning('请输入名称')
    return
  }

  let attrs: string | null
  if (useTypedAttrs.value) {
    if (showAdvancedJson.value && advancedJson.value.trim()) {
      try {
        JSON.parse(advancedJson.value)
      } catch {
        ElMessage.error('高级 JSON 格式无效')
        return
      }
    }
    attrs = serializeAttrs(form.type, attrFields, advancedJson.value)
  } else {
    const raw = advancedJson.value.trim()
    if (raw) {
      try {
        JSON.parse(raw)
      } catch {
        ElMessage.error('扩展属性 JSON 格式无效')
        return
      }
      attrs = raw
    } else {
      attrs = null
    }
  }

  saving.value = true
  try {
    const payload = {
      ...form,
      parentId: isSecurityZone.value ? null : form.parentId,
      attrs,
    }
    if (isEdit.value && props.id) {
      await updateEndpoint(Number(props.id), payload)
      ElMessage.success('已保存')
    } else {
      await createEndpoint(payload)
      ElMessage.success('已创建')
      void router.replace({ name: 'endpoints' })
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
      entityType: 'ENDPOINT',
      entityId: Number(props.id),
      entityLabel: form.name || `落点 #${props.id}`,
      title: '删除落点',
    })
    if (!ok) return
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
        <el-button
          v-if="isEdit && form.type === 'SYSTEM'"
          @click="router.push({ name: 'lineage', query: { mode: 'system', systemId: props.id! } })"
        >
          获取的资产
        </el-button>
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
        <p v-if="parentHint" class="hint">当前父级：{{ parentHint }}</p>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="显示名称；留空时可由下方属性自动填充" />
      </el-form-item>
      <el-form-item label="编码">
        <el-input v-model="form.code" placeholder="可选" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="form.status" style="width: 100%">
          <el-option v-for="o in ENTITY_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>

      <template v-if="useTypedAttrs">
        <el-form-item
          v-for="def in typedAttrDefs"
          :key="def.key"
          :label="def.label"
        >
          <el-input
            v-model="attrFields[def.key]"
            :placeholder="def.placeholder"
            @blur="syncNameFromPrimaryAttr"
          />
        </el-form-item>
        <el-form-item label="高级">
          <el-checkbox v-model="showAdvancedJson">编辑原始 JSON（额外字段）</el-checkbox>
        </el-form-item>
        <el-form-item v-if="showAdvancedJson" label="JSON">
          <el-input
            v-model="advancedJson"
            type="textarea"
            :rows="3"
            placeholder='可选，额外键会与上方字段合并，如 {"extra":"x"}'
          />
        </el-form-item>
      </template>
      <el-form-item v-else label="扩展属性">
        <el-input
          v-model="advancedJson"
          type="textarea"
          :rows="3"
          placeholder="可选 JSON 对象"
        />
      </el-form-item>

      <el-form-item label="责任人">
        <el-input v-model="form.owner" placeholder="可选" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
      </el-form-item>
    </el-form>

    <ImpactAnalysisPanel v-if="isEdit && props.id" entity-type="ENDPOINT" :entity-id="Number(props.id)" />
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

.hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--muted);
}
</style>
