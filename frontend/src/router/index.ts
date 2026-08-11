import { createRouter, createWebHistory } from 'vue-router'
import AssetListView from '@/views/AssetListView.vue'
import AssetGraphView from '@/views/AssetGraphView.vue'
import AssetFlowListView from '@/views/AssetFlowListView.vue'
import FlowEditView from '@/views/FlowEditView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'assets', component: AssetListView },
    { path: '/assets/:id/flows', name: 'asset-flows', component: AssetFlowListView, props: true },
    {
      path: '/assets/:id/flows/new',
      name: 'flow-create',
      component: FlowEditView,
      props: true,
    },
    {
      path: '/assets/:id/flows/:flowId/edit',
      name: 'flow-edit',
      component: FlowEditView,
      props: true,
    },
    { path: '/assets/:id/graph', name: 'asset-graph', component: AssetGraphView, props: true },
  ],
})

export default router
