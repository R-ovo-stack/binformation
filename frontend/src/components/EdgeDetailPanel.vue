<script setup lang="ts">
import type { GraphEdge } from '@/types/graph'
import { methodLabel, purposeLabel } from '@/utils/graphLayout'

defineProps<{
  edge: GraphEdge | null
}>()
</script>

<template>
  <aside class="panel">
    <template v-if="edge">
      <h3>流向详情</h3>
      <dl>
        <div>
          <dt>用途</dt>
          <dd>{{ purposeLabel(edge.purpose) }}</dd>
        </div>
        <div>
          <dt>主流向</dt>
          <dd>{{ edge.primary ? '是' : '否' }}</dd>
        </div>
        <div v-if="edge.upstream">
          <dt>前置流程</dt>
          <dd>是</dd>
        </div>
        <div v-if="edge.fromAssetName">
          <dt>所属资产</dt>
          <dd>{{ edge.fromAssetName }}</dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd>{{ edge.status }}</dd>
        </div>
        <div v-if="edge.remark">
          <dt>备注</dt>
          <dd>{{ edge.remark }}</dd>
        </div>
      </dl>

      <h4>路径与步骤</h4>
      <div v-if="!edge.paths.length" class="empty">暂无路径</div>
      <section v-for="path in edge.paths" :key="path.pathId" class="path">
        <header>
          <strong>{{ path.name }}</strong>
          <span>{{ path.enabled ? '启用' : '停用' }}</span>
        </header>
        <ol>
          <li v-for="step in path.steps" :key="`${path.pathId}-${step.seq}`">
            <div class="step-title">#{{ step.seq }} {{ methodLabel(step.method) }}</div>
            <div class="step-meta">
              {{ step.executorName || `执行器#${step.executorId}` }}
              <template v-if="step.hostLabel"> · {{ step.hostLabel }}</template>
            </div>
            <div v-if="step.remark" class="step-remark">{{ step.remark }}</div>
          </li>
        </ol>
      </section>
    </template>
    <template v-else>
      <h3>流向详情</h3>
      <p class="hint">点击画布上的连线，查看路径与步骤。</p>
    </template>
  </aside>
</template>

<style scoped>
.panel {
  height: 100%;
  padding: 16px;
  background: #fff;
  border: 1px solid #d5e0db;
  border-radius: 12px;
  overflow: auto;
}

h3 {
  margin: 0 0 12px;
  font-size: 16px;
  color: #0f172a;
}

h4 {
  margin: 18px 0 10px;
  font-size: 13px;
  color: #334155;
}

dl {
  display: grid;
  gap: 8px;
  margin: 0;
}

dl > div {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 8px;
  font-size: 13px;
}

dt {
  color: #64748b;
}

dd {
  margin: 0;
  color: #0f172a;
}

.path {
  margin-bottom: 12px;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8faf9;
}

.path header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
}

ol {
  margin: 0;
  padding-left: 18px;
}

li {
  margin-bottom: 8px;
}

.step-title {
  font-size: 13px;
  color: #0f172a;
}

.step-meta,
.step-remark,
.hint,
.empty {
  font-size: 12px;
  color: #64748b;
}
</style>
