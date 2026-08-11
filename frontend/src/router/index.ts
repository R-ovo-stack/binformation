import { createRouter, createWebHistory } from 'vue-router'
import AssetListView from '@/views/AssetListView.vue'
import AssetEditView from '@/views/AssetEditView.vue'
import AssetGraphView from '@/views/AssetGraphView.vue'
import AssetFlowListView from '@/views/AssetFlowListView.vue'
import FlowEditView from '@/views/FlowEditView.vue'
import EndpointListView from '@/views/EndpointListView.vue'
import EndpointEditView from '@/views/EndpointEditView.vue'
import ExecutorListView from '@/views/ExecutorListView.vue'
import ExecutorEditView from '@/views/ExecutorEditView.vue'
import DerivationListView from '@/views/DerivationListView.vue'
import DerivationEditView from '@/views/DerivationEditView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'assets', component: AssetListView },
    { path: '/assets/new', name: 'asset-create', component: AssetEditView },
    { path: '/assets/:id/edit', name: 'asset-edit', component: AssetEditView, props: true },
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
    { path: '/endpoints', name: 'endpoints', component: EndpointListView },
    { path: '/endpoints/new', name: 'endpoint-create', component: EndpointEditView },
    { path: '/endpoints/:id/edit', name: 'endpoint-edit', component: EndpointEditView, props: true },
    { path: '/executors', name: 'executors', component: ExecutorListView },
    { path: '/executors/new', name: 'executor-create', component: ExecutorEditView },
    { path: '/executors/:id/edit', name: 'executor-edit', component: ExecutorEditView, props: true },
    {
      path: '/assets/:id/derivations',
      name: 'asset-derivations',
      component: DerivationListView,
      props: true,
    },
    {
      path: '/assets/:id/derivations/new',
      name: 'derivation-create',
      component: DerivationEditView,
      props: true,
    },
    {
      path: '/assets/:id/derivations/:derivationId/edit',
      name: 'derivation-edit',
      component: DerivationEditView,
      props: true,
    },
  ],
})

export default router
