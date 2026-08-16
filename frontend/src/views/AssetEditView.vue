<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createAsset, deleteAsset, getAsset, updateAsset } from '@/api/asset'
import { listChangeLogsByAsset } from '@/api/changelog'
import { changeActionLabel, changeEntityLabel, type ChangeLogEntry } from '@/types/changelog'
import { confirmImpactDelete } from '@/utils/impactConfirm'
import {
  ASSET_DATA_TYPE_OPTIONS,
  ENTITY_STATUS_OPTIONS,
  emptyAssetForm,
  type AssetSavePayload,
} from '@/types/asset'
import ImpactAnalysisPanel from '@/components/ImpactAnalysisPanel.vue'

const props = defineProps<{ id?: string }>()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<AssetSavePayload>(emptyAssetForm())
const changeLogs = ref<ChangeLogEntry[]>([])
const logsLoading = ref(false)

const isEdit = computed(() => Boolean(props.id && props.id !== 'new'))
const pageTitle = computed(() => (isEdit.value ? '编辑数据资产' : '新建数据资产'))

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  dataType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

async function loadChangeLogs() {
  if (!isEdit.value || !props.id) return
  logsLoading.value = true
  try {
    changeLogs.value = await listChangeLogsByAsset(Number(props.id))
  } catch {
    changeLogs.value = []
  } finally {
    logsLoading.value = false
  }
}

async function load() {
  if (!isEdit.value || !props.id) return
  loading.value = true
  try {
    const asset = await getAsset(Number(props.id))
    Object.assign(form, {
      name: asset.name,
      code: asset.code,
      dataType: asset.dataType,
      status: asset.status,
      owner: asset.owner ?? null,
      remark: asset.remark ?? null,
    })
    await loadChangeLogs()
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
      await updateAsset(Number(props.id), form)
      ElMessage.success('已保存')
    } else {
      const created = await createAsset(form)
      ElMessage.success('已创建')
      void router.replace({ name: 'asset-edit', params: { id: String(created.id) } })
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
      entityType: 'ASSET',
      entityId: Number(props.id),
      entityLabel: form.name || `资产 #${props.id}`,
      title: '删除数据资产',
    })
    if (!ok) return
    await deleteAsset(Number(props.id))
    ElMessage.success('已删除')
    void router.push('/')
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
        <el-button link @click="router.push('/')">← 返回资产列表</el-button>
        <h1>{{ pageTitle }}</h1>
      </div>
      <div class="actions">
        <el-button v-if="isEdit" type="danger" plain @click="remove">删除</el-button>
        <el-button @click="router.push('/')">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </div>
    </header>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" class="card">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="如：变化遥信数据" />
      </el-form-item>
      <el-form-item label="编码" prop="code">
        <el-input v-model="form.code" placeholder="如：ASSET_GRID_YX" />
      </el-form-item>
      <el-form-item label="数据类型" prop="dataType">
        <el-select v-model="form.dataType" style="width: 100%">
          <el-option v-for="o in ASSET_DATA_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
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
        <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="可选" />
      </el-form-item>

      <div v-if="isEdit" class="links">
        <el-button @click="router.push({ name: 'asset-flows', params: { id: props.id! } })">
          管理流向
        </el-button>
        <el-button @click="router.push({ name: 'asset-derivations', params: { id: props.id! } })">
          管理派生
        </el-button>
        <el-button @click="router.push({ name: 'asset-graph', params: { id: props.id! } })">
          一键成图
        </el-button>
      </div>
    </el-form>

    <ImpactAnalysisPanel
      v-if="isEdit && props.id"
      entity-type="ASSET"
      :entity-id="Number(props.id)"
      action="UPDATE"
    />

    <section v-if="isEdit" class="changelog card" v-loading="logsLoading">
      <h2>变更记录</h2>
      <el-empty v-if="!changeLogs.length" description="暂无变更记录" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="log in changeLogs"
          :key="log.id"
          :timestamp="log.operatedAt"
          placement="top"
        >
          <p class="log-summary">{{ log.summary }}</p>
          <p class="log-meta">
            {{ changeActionLabel(log.action) }} · {{ changeEntityLabel(log.entityType) }} #{{ log.entityId }} ·
            {{ log.operator }}
          </p>
          <ul v-if="log.items.length" class="log-items">
            <li v-for="(item, i) in log.items" :key="i">
              {{ item.fieldName }}：{{ item.oldValue ?? '—' }} → {{ item.newValue ?? '—' }}
            </li>
          </ul>
        </el-timeline-item>
      </el-timeline>
    </section>
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
  padding: 22px;
  backdrop-filter: blur(8px);
}

.links {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.changelog {
  margin-top: 16px;
}

.changelog h2 {
  margin: 0 0 12px;
  font-size: 15px;
}

.log-summary {
  margin: 0 0 4px;
  font-weight: 500;
}

.log-meta {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
}

.log-items {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--muted);
}
</style>
