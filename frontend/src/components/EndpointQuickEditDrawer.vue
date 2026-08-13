<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getEndpoint, updateEndpoint } from '@/api/endpoint'
import { ENTITY_STATUS_OPTIONS } from '@/types/asset'
import { typeLabel, type EndpointSavePayload } from '@/types/endpoint'
import {
  attrFieldsForType,
  hasTypedAttrs,
  parseAttrsJson,
  serializeAttrs,
  suggestNameFromAttrs,
} from '@/utils/endpointAttrs'
import ImpactAnalysisPanel from '@/components/ImpactAnalysisPanel.vue'

const props = defineProps<{
  modelValue: boolean
  endpointId: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [open: boolean]
  saved: [endpointId: number]
}>()

const open = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const title = ref('编辑落点')
const typeLabels = ref<Record<string, string>>({})
const form = reactive<EndpointSavePayload>({
  type: 'HOST',
  name: '',
  code: null,
  parentId: null,
  attrs: null,
  status: 'ACTIVE',
  owner: null,
  remark: null,
})
const attrFields = reactive<Record<string, string>>({})
const showAdvancedJson = ref(false)
const advancedJson = ref('')
const breadcrumb = ref('')

const typedAttrDefs = computed(() => attrFieldsForType(form.type))
const useTypedAttrs = computed(() => hasTypedAttrs(form.type))

const rules: FormRules = {
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
  const known = new Set(defs.map((d) => d.key))
  const extras: Record<string, string> = {}
  for (const [k, v] of Object.entries(parsed)) {
    if (!known.has(k)) extras[k] = v
  }
  advancedJson.value = Object.keys(extras).length ? JSON.stringify(extras, null, 2) : ''
  showAdvancedJson.value = Object.keys(extras).length > 0
  if (!defs.length) {
    advancedJson.value = raw?.trim() ? raw : ''
    showAdvancedJson.value = Boolean(raw?.trim())
  }
}

async function load() {
  if (!props.endpointId) return
  loading.value = true
  try {
    const ep = await getEndpoint(props.endpointId)
    form.type = ep.type
    form.name = ep.name
    form.code = ep.code ?? null
    form.parentId = ep.parentId ?? null
    form.attrs = ep.attrs ?? null
    form.status = ep.status
    form.owner = ep.owner ?? null
    form.remark = ep.remark ?? null
    breadcrumb.value = ep.breadcrumb
    title.value = `编辑落点 · ${ep.name}`
    typeLabels.value = { [ep.type]: ep.type }
    resetAttrFields(ep.type, ep.attrs)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载落点失败')
    open.value = false
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!props.endpointId) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (!form.name.trim()) {
    const suggested = suggestNameFromAttrs(form.type, attrFields)
    if (suggested) form.name = suggested
  }
  if (!form.name.trim()) {
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
    await updateEndpoint(props.endpointId, {
      type: form.type,
      name: form.name.trim(),
      code: form.code,
      parentId: form.parentId,
      attrs,
      status: form.status,
      owner: form.owner,
      remark: form.remark,
    })
    ElMessage.success('落点已更新')
    emit('saved', props.endpointId)
    open.value = false
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

watch(
  () => [props.modelValue, props.endpointId] as const,
  ([visible, id]) => {
    if (visible && id != null) void load()
  },
)
</script>

<template>
  <el-drawer
    v-model="open"
    :title="title"
    size="480px"
    destroy-on-close
    class="endpoint-quick-edit"
  >
    <div v-loading="loading" class="body">
      <p v-if="breadcrumb" class="crumb">{{ breadcrumb }} / {{ form.name }}</p>
      <p class="tip">类型与父落点请在落点管理页调整；此处可改名称与属性。</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="类型">
          <el-input :model-value="typeLabel(form.type, typeLabels)" disabled />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="form.code" placeholder="可选" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option
              v-for="o in ENTITY_STATUS_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>

        <template v-if="useTypedAttrs">
          <el-form-item v-for="def in typedAttrDefs" :key="def.key" :label="def.label">
            <el-input v-model="attrFields[def.key]" :placeholder="def.placeholder" />
          </el-form-item>
          <el-form-item label="高级 JSON">
            <el-switch v-model="showAdvancedJson" />
          </el-form-item>
          <el-form-item v-if="showAdvancedJson" label="扩展">
            <el-input v-model="advancedJson" type="textarea" :rows="4" placeholder='{"key":"value"}' />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="扩展属性">
            <el-input
              v-model="advancedJson"
              type="textarea"
              :rows="4"
              placeholder='可选 JSON，如 {"key":"value"}'
            />
          </el-form-item>
        </template>

        <el-form-item label="责任人">
          <el-input v-model="form.owner" placeholder="可选" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>

      <ImpactAnalysisPanel
        v-if="endpointId"
        entity-type="ENDPOINT"
        :entity-id="endpointId"
        action="UPDATE"
        compact
        embedded
      />
    </div>

    <template #footer>
      <div class="footer">
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.body {
  min-height: 240px;
}

.crumb {
  margin: 0 0 8px;
  color: var(--muted);
  font-size: 12px;
}

.tip {
  margin: 0 0 14px;
  color: var(--muted-soft);
  font-size: 12px;
  line-height: 1.45;
}

.footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
