<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalPageHeader
      eyebrow="Privacy Center"
      title="隐私中心"
      description="查看数据收集范围、共享偏好和敏感操作入口，确保健康数据使用边界清晰可见。"
      :meta="shareResearch ? '研究共享开启' : '研究共享关闭'"
      meta-label="共享状态"
    >
      <Button variant="secondary" @click="goBack">
        <iconify-icon icon="solar:alt-arrow-left-outline" width="16" height="16" />
        返回
      </Button>
    </ClinicalPageHeader>

    <ClinicalSurfaceCard
      eyebrow="Data Scope"
      title="数据使用说明"
      description="用分段方式解释平台收集、使用、存储和提供的数据权利，避免隐私信息被隐藏在长文里。"
    >
      <div class="space-y-3">
        <button
          v-for="section in sections"
          :key="section.key"
          type="button"
          class="w-full rounded-[1.25rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4 text-left transition hover:border-teal-200"
          @click="toggleSection(section.key)"
        >
          <div class="flex items-center justify-between gap-3">
            <div>
              <p class="text-sm font-semibold text-slate-950">{{ section.title }}</p>
              <p class="mt-1 text-sm leading-6 text-slate-600">{{ section.summary }}</p>
            </div>
            <span class="rounded-full bg-white px-3 py-1 text-xs text-slate-500">{{ openKey === section.key ? '展开中' : '查看' }}</span>
          </div>
          <p v-if="openKey === section.key" class="mt-3 rounded-[1rem] bg-white px-4 py-3 text-sm leading-6 text-slate-700">{{ section.detail }}</p>
        </button>
      </div>
    </ClinicalSurfaceCard>

    <section class="grid gap-4 lg:grid-cols-[1fr_0.95fr]">
      <ClinicalSurfaceCard
        eyebrow="Sharing Preferences"
        title="共享偏好"
        description="共享开关都以脱敏为前提，你可以随时调整。"
      >
        <div class="space-y-3">
          <label class="flex items-center justify-between rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
            <div>
              <p class="text-sm font-semibold text-slate-950">用于模型改进</p>
              <p class="mt-1 text-xs text-slate-500">仅使用脱敏统计数据</p>
            </div>
            <input :checked="shareImprove" type="checkbox" class="h-4 w-4" @change="toggleImprove" />
          </label>

          <label class="flex items-center justify-between rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3">
            <div>
              <p class="text-sm font-semibold text-slate-950">用于健康研究</p>
              <p class="mt-1 text-xs text-slate-500">参与匿名研究项目</p>
            </div>
            <input :checked="shareResearch" type="checkbox" class="h-4 w-4" @change="toggleResearch" />
          </label>
        </div>
      </ClinicalSurfaceCard>

      <ClinicalSurfaceCard
        eyebrow="Sensitive Action"
        title="申请删除数据"
        description="高风险动作要求二次确认和冷静期，避免误操作带来不可逆后果。"
      >
        <div class="rounded-[1.25rem] border border-rose-200 bg-rose-50/90 px-4 py-4">
          <p class="text-sm font-semibold text-rose-950">敏感操作说明</p>
          <p class="mt-2 text-sm leading-6 text-rose-900/80">提交后进入冷静期，期间可撤销。关键数据会保留最小化审计记录，用于保障合规和安全追溯。</p>
          <Button variant="danger" class="mt-4 w-full" :loading="deleting" @click="handleDelete">
            {{ confirmRequired ? '确认删除数据' : '申请删除数据' }}
          </Button>
          <p v-if="confirmRequired" class="mt-2 text-xs text-rose-900/80">再次点击即提交申请。</p>
        </div>
      </ClinicalSurfaceCard>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import Button from '@/shared/components/ui/Button.vue'

type SectionKey = 'collect' | 'use' | 'storage' | 'rights'

type Section = {
  key: SectionKey
  title: string
  summary: string
  detail: string
}

const router = useRouter()
const { success, info, warning } = useToast()

const sections: Section[] = [
  {
    key: 'collect',
    title: '我们收集哪些数据',
    summary: '仅收集提供服务所需的健康记录与操作日志。',
    detail: '包括监测指标、上传资料、计划记录与必要设备信息，用于生成个性化建议与安全审计。',
  },
  {
    key: 'use',
    title: '数据如何被使用',
    summary: '用于分析趋势、生成建议与提醒服务。',
    detail: '数据用于趋势分析、风险提示、计划推荐与产品稳定性保障，不用于与健康无关的目的。',
  },
  {
    key: 'storage',
    title: '数据存储与保护',
    summary: '采用传输加密与静态加密，并进行访问审计。',
    detail: '关键数据在传输与存储阶段均加密处理，访问行为会被记录并定期审计。',
  },
  {
    key: 'rights',
    title: '你的数据权利',
    summary: '你可以导出、纠正或申请删除数据。',
    detail: '我们提供导出、纠正与删除申请通道，涉及敏感操作时要求二次确认与冷静期。',
  },
]

const openKey = ref<SectionKey>('collect')
const shareImprove = ref(true)
const shareResearch = ref(false)
const confirmRequired = ref(false)
const deleting = ref(false)

let confirmTimer: number | null = null

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/profile')
}

const resetConfirm = () => {
  confirmRequired.value = false
  if (confirmTimer) {
    window.clearTimeout(confirmTimer)
    confirmTimer = null
  }
}

const toggleSection = (key: SectionKey) => {
  openKey.value = key
}

const toggleImprove = () => {
  shareImprove.value = !shareImprove.value
  info(shareImprove.value ? '已开启模型改进' : '已关闭模型改进', '你可以随时调整该设置。')
}

const toggleResearch = () => {
  shareResearch.value = !shareResearch.value
  info(shareResearch.value ? '已加入匿名研究' : '已退出匿名研究', '所有研究数据均经过脱敏处理。')
}

const handleDelete = async () => {
  if (!confirmRequired.value) {
    confirmRequired.value = true
    warning('请再次确认', '再次点击将提交删除申请。')
    if (confirmTimer) window.clearTimeout(confirmTimer)
    confirmTimer = window.setTimeout(() => {
      confirmRequired.value = false
      confirmTimer = null
    }, 4000)
    return
  }

  deleting.value = true
  await sleep(900)
  deleting.value = false
  resetConfirm()
  success('删除申请已提交', '我们将进入冷静期流程并通知你进度。')
}
</script>
