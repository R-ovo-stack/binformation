<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteEndpoint, getEndpointTypeMeta, listEndpoints } from '@/api/endpoint'
import {
  buildEndpointTree,
  childTypesForParent,
  collectExpandKeys,
  filterEndpointTree,
  type EndpointTreeNode,
} from '@/utils/endpointTree'
import { typeLabel } from '@/types/endpoint'

const router = useRouter()
const loading = ref(false)
const endpoints = ref<Awaited<ReturnType<typeof listEndpoints>>>([])
const typeFilter = ref('')
const keyword = ref('')
const typeLabels = ref<Record<string, string>>({})
const typeOptions = ref<string[]>([])
const expandedKeys = ref<number[]>([])

const fullTree = computed(() => buildEndpointTree(endpoints.value, typeLabels.value))

const displayTree = computed(() =>
  filterEndpointTree(fullTree.value, typeFilter.value, keyword.value),
)

async function load() {
  loading.value = true
  try {
    const [meta, list] = await Promise.all([getEndpointTypeMeta(), listEndpoints()])
    typeLabels.value = meta.labels
    typeOptions.value = meta.types
    endpoints.value = list
    expandedKeys.value = collectExpandKeys(displayTree.value)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

watch([typeFilter, keyword, fullTree], () => {
  expandedKeys.value = collectExpandKeys(displayTree.value)
})

function openCreate(parent?: EndpointTreeNode, childType?: string) {
  void router.push({
    name: 'endpoint-create',
    query: {
      parentId: parent ? String(parent.id) : undefined,
      childType,
    },
  })
}

function openEdit(node: EndpointTreeNode) {
  void router.push({ name: 'endpoint-edit', params: { id: String(node.id) } })
}

async function remove(node: EndpointTreeNode) {
  try {
    await ElMessageBox.confirm(`确定删除落点「${node.label}」吗？`, '删除确认', { type: 'warning' })
    await deleteEndpoint(node.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e instanceof Error ? e.message : '删除失败')
    }
  }
}

function childTypeLabel(childType: string) {
  return `添加${typeLabel(childType, typeLabels.value)}`
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="topbar">
      <div>
        <el-button link @click="router.push('/')">← 返回资产列表</el-button>
        <h1>落点管理</h1>
        <p class="meta">树形浏览拓扑；在主机等节点上可直接添加子落点（如 idc301 下新增目录）</p>
      </div>
      <div class="actions">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索名称 / ID"
          style="width: 160px"
        />
        <el-select v-model="typeFilter" clearable placeholder="按类型筛选" style="width: 160px">
          <el-option
            v-for="t in typeOptions"
            :key="t"
            :label="typeLabel(t, typeLabels)"
            :value="t"
          />
        </el-select>
        <el-button type="primary" @click="openCreate()">新建顶层</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </header>

    <section class="tree-panel">
      <el-tree
        v-if="displayTree.length"
        :data="displayTree"
        node-key="id"
        :default-expanded-keys="expandedKeys"
        :expand-on-click-node="false"
        highlight-current
      >
        <template #default="{ data }: { data: EndpointTreeNode }">
          <div class="tree-row">
            <div class="tree-main">
              <span class="name">{{ data.label }}</span>
              <el-tag size="small" type="info" effect="plain">{{ data.typeLabel }}</el-tag>
              <span class="id">#{{ data.id }}</span>
              <el-tag v-if="data.status !== 'ACTIVE'" size="small" type="warning">{{ data.status }}</el-tag>
            </div>
            <div class="tree-actions" @click.stop>
              <template v-if="childTypesForParent(data.type).length === 1">
                <el-button
                  link
                  type="primary"
                  @click="openCreate(data, childTypesForParent(data.type)[0])"
                >
                  {{ childTypeLabel(childTypesForParent(data.type)[0]) }}
                </el-button>
              </template>
              <el-dropdown
                v-else-if="childTypesForParent(data.type).length > 1"
                trigger="click"
                @command="(t: string) => openCreate(data, t)"
              >
                <el-button link type="primary">添加子落点</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="ct in childTypesForParent(data.type)"
                      :key="ct"
                      :command="ct"
                    >
                      {{ typeLabel(ct, typeLabels) }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button link type="primary" @click="openEdit(data)">编辑</el-button>
              <el-button link type="danger" @click="remove(data)">删除</el-button>
            </div>
          </div>
        </template>
      </el-tree>
      <el-empty v-else description="暂无落点，可新建安全区或调整筛选条件" />
    </section>
  </div>
</template>

<style scoped>
.page {
  max-width: 1180px;
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
}

.meta {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.tree-panel {
  background: #fff;
  border: 1px solid #d5e0db;
  border-radius: 12px;
  padding: 12px 16px 20px;
}

.tree-row {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 32px;
  padding-right: 8px;
}

.tree-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}

.name {
  font-weight: 500;
  color: #0f172a;
}

.id {
  font-size: 12px;
  color: #94a3b8;
}

.tree-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

:deep(.el-tree-node__content) {
  height: auto;
  min-height: 36px;
  padding-top: 4px;
  padding-bottom: 4px;
}

@media (max-width: 768px) {
  .tree-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .tree-actions {
    flex-wrap: wrap;
  }
}
</style>
