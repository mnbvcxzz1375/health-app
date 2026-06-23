<template>
  <div class="space-y-5 pb-4 text-slate-950">
    <ClinicalPageHeader
      eyebrow="Support Center"
      title="帮助与支持"
      description="按主题快速检索常见问题，必要时直接提交问题单给支持团队。"
      :meta="`${filteredFaqs.length} 条结果`"
      meta-label="当前命中"
    >
      <Button variant="secondary" @click="goBack">
        <iconify-icon icon="solar:alt-arrow-left-outline" width="16" height="16" />
        返回
      </Button>
    </ClinicalPageHeader>

    <ClinicalSurfaceCard
      eyebrow="Knowledge Search"
      title="问题检索"
      description="可以搜索导出、隐私、设备、提醒等主题，也可以直接使用快捷标签。"
    >
      <div class="flex flex-col gap-3 lg:flex-row">
        <input
          v-model="keyword"
          class="w-full rounded-[1rem] border border-[color:var(--surface-border)] px-4 py-3 text-sm outline-none focus:border-[color:var(--ring)]"
          placeholder="搜索问题，例如：导出、隐私、设备连接"
        />
        <Button variant="secondary" @click="resetSearch">清空</Button>
      </div>

      <div class="mt-4 flex flex-wrap gap-2">
        <button
          v-for="tag in quickTags"
          :key="tag"
          type="button"
          class="rounded-full border px-3 py-1.5 text-xs transition"
          :class="keyword === tag ? 'border-teal-300 bg-teal-50 text-teal-900' : 'border-[color:var(--surface-border)] bg-white text-slate-600 hover:text-slate-900'"
          @click="keyword = tag"
        >
          {{ tag }}
        </button>
      </div>
    </ClinicalSurfaceCard>

    <div v-if="filteredFaqs.length" class="grid gap-3">
      <ClinicalSurfaceCard
        v-for="item in filteredFaqs"
        :key="item.q"
        eyebrow="FAQ"
        :title="item.q"
        :description="item.a"
      >
        <div class="flex flex-wrap gap-2">
          <span v-for="tag in item.tags" :key="tag" class="rounded-full border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-2.5 py-1 text-[11px] text-slate-600">
            {{ tag }}
          </span>
        </div>
      </ClinicalSurfaceCard>
    </div>

    <ClinicalStateNotice
      v-else
      tone="empty"
      title="没有找到相关结果"
      description="可以尝试更换关键词，或直接联系支持团队。"
      action-label="联系支持"
      @action="contactSupport"
    />

    <ClinicalSurfaceCard
      eyebrow="Need More Help"
      title="仍需帮助？"
      description="复杂问题可以直接提交问题单，我们会带着上下文跟进。"
    >
      <div class="flex flex-col gap-3 rounded-[1.25rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p class="text-sm font-semibold text-slate-950">提交问题单</p>
          <p class="mt-1 text-sm leading-6 text-slate-600">描述问题、相关页面和操作场景，支持团队会在工作日尽快回复。</p>
        </div>
        <Button @click="contactSupport">提交问题</Button>
      </div>
    </ClinicalSurfaceCard>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import Button from '@/shared/components/ui/Button.vue'

type FaqItem = {
  q: string
  a: string
  tags: string[]
}

const route = useRoute()
const router = useRouter()
const { success, info } = useToast()

const keyword = ref(typeof route.query.topic === 'string' ? route.query.topic : '')

watch(
  () => route.query.topic,
  (next) => {
    if (typeof next === 'string') keyword.value = next
  },
)

const quickTags = ['导出数据', '隐私中心', '设备连接', '康复提醒', '账号安全']

const faqs: FaqItem[] = [
  {
    q: '如何导出最近一个月的数据？',
    a: '进入“导出数据”页面，点击“最近 30 天”并选择格式后即可开始导出。',
    tags: ['导出数据', '报告'],
  },
  {
    q: '设备连接失败怎么办？',
    a: '请确认蓝牙已开启，并在权限页面允许设备连接权限后重试。',
    tags: ['设备连接', '权限'],
  },
  {
    q: '隐私中心里可以做什么？',
    a: '你可以查看数据使用范围、管理共享偏好，并提交删除申请。',
    tags: ['隐私中心', '数据权利'],
  },
  {
    q: '康复提醒没有收到？',
    a: '请检查系统通知权限是否开启，并在个人设置里确认康复提醒开关。',
    tags: ['康复提醒', '通知'],
  },
  {
    q: '如何提升建议的准确性？',
    a: '保持监测数据连续、及时上传报告，并在个人设置中补充健康目标。',
    tags: ['建议质量', '设置'],
  },
]

const filteredFaqs = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  if (!key) return faqs

  return faqs.filter((item) => {
    const source = `${item.q} ${item.a} ${item.tags.join(' ')}`.toLowerCase()
    return source.includes(key)
  })
})

const resetSearch = () => {
  keyword.value = ''
  info('已清空搜索', '可以重新输入关键词。')
}

const contactSupport = () => {
  success('问题单已创建', '支持团队会在工作日尽快联系你。')
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/profile')
}
</script>
