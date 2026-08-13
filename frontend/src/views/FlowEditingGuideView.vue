<script setup lang="ts">
import { useRouter } from 'vue-router'
import MarkdownDoc from '@/components/MarkdownDoc.vue'

const router = useRouter()

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  void router.push('/')
}
</script>

<template>
  <div class="page">
    <header class="topbar">
      <div>
        <el-button link @click="goBack">← 返回</el-button>
        <h1>流向配置指南</h1>
        <p class="meta">表单编辑 · 可视化编辑 · 多路径 · 多步骤</p>
      </div>
    </header>

    <main class="card doc-card">
      <MarkdownDoc fetch-url="/docs/flow-editing-guide.md" />
    </main>
  </div>
</template>

<style scoped>
.page {
  max-width: 920px;
  margin: 0 auto;
  padding: 16px 20px 40px;
}

.topbar {
  margin-bottom: 16px;
}

h1 {
  margin: 4px 0 0;
  font-size: 24px;
}

.meta {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.doc-card {
  background: var(--surface-solid);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 28px 32px 36px;
}

@media (max-width: 640px) {
  .doc-card {
    padding: 20px 16px 28px;
  }
}
</style>
