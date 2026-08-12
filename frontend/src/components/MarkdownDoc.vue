<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { marked } from 'marked'

const props = withDefaults(
  defineProps<{
    /** Markdown 原文 */
    source?: string
    /** 从 public 目录 fetch 的路径，如 /docs/flow-editing-guide.md */
    fetchUrl?: string
  }>(),
  {},
)

const loading = ref(false)
const error = ref('')
const markdown = ref(props.source ?? '')

marked.setOptions({
  gfm: true,
  breaks: true,
})

const html = computed(() => {
  if (!markdown.value) return ''
  return marked.parse(markdown.value) as string
})

async function loadFromUrl(url: string) {
  loading.value = true
  error.value = ''
  try {
    const res = await fetch(url)
    if (!res.ok) throw new Error(`加载文档失败 (${res.status})`)
    markdown.value = await res.text()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载文档失败'
    markdown.value = ''
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (props.fetchUrl && !props.source) void loadFromUrl(props.fetchUrl)
})

watch(
  () => props.source,
  (val) => {
    if (val != null) markdown.value = val
  },
)

watch(
  () => props.fetchUrl,
  (url) => {
    if (url && !props.source) void loadFromUrl(url)
  },
)
</script>

<template>
  <article v-loading="loading" class="markdown-doc">
    <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" />
    <div v-else class="markdown-body" v-html="html" />
  </article>
</template>

<style scoped>
.markdown-doc {
  min-height: 120px;
}

.markdown-body {
  color: #1e293b;
  font-size: 15px;
  line-height: 1.75;
  word-break: break-word;
}

.markdown-body :deep(h1) {
  margin: 0 0 20px;
  font-size: 26px;
  line-height: 1.3;
  color: #0f172a;
  border-bottom: 1px solid #e2e8f0;
  padding-bottom: 12px;
}

.markdown-body :deep(h2) {
  margin: 32px 0 12px;
  font-size: 20px;
  color: #0f172a;
}

.markdown-body :deep(h3) {
  margin: 24px 0 8px;
  font-size: 16px;
  color: #334155;
}

.markdown-body :deep(p) {
  margin: 0 0 12px;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0 0 16px;
  padding-left: 1.4em;
}

.markdown-body :deep(li) {
  margin: 4px 0;
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0 20px;
  font-size: 14px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 8px 12px;
  text-align: left;
}

.markdown-body :deep(th) {
  background: #f8fafc;
  font-weight: 600;
  color: #334155;
}

.markdown-body :deep(tr:nth-child(even) td) {
  background: #fafafa;
}

.markdown-body :deep(code) {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.92em;
  color: #0f766e;
}

.markdown-body :deep(pre) {
  background: #0f172a;
  color: #e2e8f0;
  padding: 14px 16px;
  border-radius: 10px;
  overflow-x: auto;
  margin: 12px 0 20px;
  font-size: 13px;
  line-height: 1.6;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
  color: inherit;
}

.markdown-body :deep(blockquote) {
  margin: 12px 0;
  padding: 10px 14px;
  border-left: 4px solid #0f766e;
  background: #ecfdf5;
  color: #334155;
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid #e2e8f0;
  margin: 28px 0;
}

.markdown-body :deep(a) {
  color: #0f766e;
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.markdown-body :deep(input[type='checkbox']) {
  margin-right: 6px;
}
</style>
