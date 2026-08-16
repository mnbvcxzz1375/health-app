<template>
  <ProfileSubPage title="隐私中心" subtitle="查看数据收集范围、共享偏好和敏感操作入口">
    <div class="space-y-5">
      <!-- 数据使用说明 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">数据使用说明</h2>
        <p class="mt-0.5 text-[13px]" style="color: var(--muted-foreground);">分段说明数据的收集、使用、存储与你的权利。</p>
        <div class="mt-3 space-y-2">
          <button
            v-for="section in sections"
            :key="section.key"
            type="button"
            class="w-full rounded-[12px] border px-4 py-3 text-left transition active:scale-[0.99]"
            style="background: var(--secondary); border-color: var(--border);"
            @click="toggleSection(section.key)"
          >
            <div class="flex items-center justify-between gap-3">
              <div class="min-w-0 flex-1">
                <p class="text-[15px] font-medium" style="color: var(--foreground);">{{ section.title }}</p>
                <p class="mt-0.5 text-[12px] leading-4" style="color: var(--muted-foreground);">{{ section.summary }}</p>
              </div>
              <iconify-icon
                icon="solar:alt-arrow-down-outline"
                width="18"
                height="18"
                class="shrink-0 transition"
                style="color: var(--muted-foreground);"
                :class="openKey === section.key ? 'rotate-180' : ''"
              />
            </div>
            <p
              v-if="openKey === section.key"
              class="mt-3 rounded-[10px] p-3 text-[13px] leading-5"
              style="background: var(--card); color: var(--muted-foreground); border: 1px solid var(--border);"
            >{{ section.detail }}</p>
          </button>
        </div>
      </section>

      <!-- 共享偏好 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">共享偏好</h2>
        <p class="mt-0.5 text-[13px]" style="color: var(--muted-foreground);">共享均以脱敏为前提，可随时调整。</p>
        <div class="mt-3 space-y-2">
          <label
            class="flex items-center justify-between gap-3 rounded-[12px] border px-4 py-3"
            style="background: var(--secondary); border-color: var(--border);"
          >
            <div>
              <p class="text-[15px] font-medium" style="color: var(--foreground);">用于模型改进</p>
              <p class="mt-0.5 text-[12px]" style="color: var(--muted-foreground);">仅使用脱敏统计数据</p>
            </div>
            <input
              v-model="shareImprove"
              type="checkbox"
              class="h-5 w-5 shrink-0 accent-[color:var(--brand-500)]"
              @change="toggleImprove"
            />
          </label>

          <label
            class="flex items-center justify-between gap-3 rounded-[12px] border px-4 py-3"
            style="background: var(--secondary); border-color: var(--border);"
          >
            <div>
              <p class="text-[15px] font-medium" style="color: var(--foreground);">用于健康研究</p>
              <p class="mt-0.5 text-[12px]" style="color: var(--muted-foreground);">参与匿名研究项目</p>
            </div>
            <input
              v-model="shareResearch"
              type="checkbox"
              class="h-5 w-5 shrink-0 accent-[color:var(--brand-500)]"
              @change="toggleResearch"
            />
          </label>
        </div>
      </section>

      <!-- 敏感操作 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">敏感操作</h2>
        <div
          class="mt-3 rounded-[12px] border p-4"
          style="background: var(--state-error-surface); border-color: color-mix(in srgb, var(--state-error) 20%, var(--card));"
        >
          <p class="text-[15px] font-medium" style="color: var(--state-error);">申请删除数据</p>
          <p class="mt-1 text-[13px] leading-5" style="color: var(--foreground);">提交后进入冷静期，期间可撤销。关键数据会保留最小化审计记录用于合规追溯。</p>
          <button
            type="button"
            class="mt-4 flex h-[48px] w-full items-center justify-center rounded-full text-[15px] font-semibold transition active:scale-[0.98] disabled:opacity-60"
            style="background: var(--state-error); color: var(--state-error-foreground);"
            :disabled="deleting"
            @click="handleDelete"
          >
            {{ deleting ? '提交中…' : confirmRequired ? '确认删除数据' : '申请删除数据' }}
          </button>
          <p v-if="confirmRequired" class="mt-2 text-center text-[12px]" style="color: var(--state-error);">再次点击即提交申请</p>
        </div>
      </section>
    </div>
  </ProfileSubPage>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useToast } from '@/composables/useToast'
import ProfileSubPage from '../components/ProfileSubPage.vue'

type SectionKey = 'collect' | 'use' | 'storage' | 'rights'

type Section = {
  key: SectionKey
  title: string
  summary: string
  detail: string
}

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

const toggleSection = (key: SectionKey) => {
  openKey.value = openKey.value === key ? ('' as SectionKey) : key
}

const toggleImprove = () => {
  info(shareImprove.value ? '已开启模型改进' : '已关闭模型改进', '你可以随时调整该设置。')
}

const toggleResearch = () => {
  info(shareResearch.value ? '已加入匿名研究' : '已退出匿名研究', '所有研究数据均经过脱敏处理。')
}

const resetConfirm = () => {
  confirmRequired.value = false
  if (confirmTimer) {
    window.clearTimeout(confirmTimer)
    confirmTimer = null
  }
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
