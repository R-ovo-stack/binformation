import { createRouter, createWebHistory } from 'vue-router'
import AssetListView from '@/views/AssetListView.vue'
import AssetGraphView from '@/views/AssetGraphView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'assets', component: AssetListView },
    { path: '/assets/:id/graph', name: 'asset-graph', component: AssetGraphView, props: true },
  ],
})

export default router
