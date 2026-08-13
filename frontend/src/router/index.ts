import { createRouter, createWebHistory } from 'vue-router'
import AssetListView from '@/views/AssetListView.vue'
import AssetEditView from '@/views/AssetEditView.vue'
import AssetGraphView from '@/views/AssetGraphView.vue'
import AssetFlowListView from '@/views/AssetFlowListView.vue'
import FlowEditView from '@/views/FlowEditView.vue'
import FlowVisualEditView from '@/views/FlowVisualEditView.vue'
import EndpointListView from '@/views/EndpointListView.vue'
import EndpointEditView from '@/views/EndpointEditView.vue'
import ExecutorListView from '@/views/ExecutorListView.vue'
import ExecutorEditView from '@/views/ExecutorEditView.vue'
import DerivationListView from '@/views/DerivationListView.vue'
import DerivationEditView from '@/views/DerivationEditView.vue'
import FlowEditingGuideView from '@/views/FlowEditingGuideView.vue'
import PanoramaGraphView from '@/views/PanoramaGraphView.vue'
import ImpactAnalysisView from '@/views/ImpactAnalysisView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'assets', component: AssetListView },
    { path: '/panorama', name: 'panorama', component: PanoramaGraphView },
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
    {
      path: '/assets/:id/flows/visual',
      name: 'flow-visual',
      component: FlowVisualEditView,
      props: true,
    },
    {
      path: '/assets/:id/flows/:flowId/visual',
      name: 'flow-visual-edit',
      component: FlowVisualEditView,
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
    {
      path: '/docs/flow-editing',
      name: 'flow-editing-guide',
      component: FlowEditingGuideView,
    },
    {
      path: '/impact',
      name: 'impact',
      component: ImpactAnalysisView,
    },
  ],
})

export default router
