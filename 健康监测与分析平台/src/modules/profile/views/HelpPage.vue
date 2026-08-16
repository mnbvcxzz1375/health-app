<template>
  <ProfileSubPage title="帮助与支持" subtitle="按主题检索常见问题，或直接提交问题单">
    <div class="space-y-5">
      <!-- 搜索 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">问题检索</h2>
        <div class="mt-3 flex gap-2">
          <div class="relative flex-1">
            <iconify-icon
              icon="solar:magnifer-outline"
              width="18"
              height="18"
              class="absolute left-3.5 top-1/2 -translate-y-1/2"
              style="color: var(--muted-foreground);"
            />
            <input
              v-model="keyword"
              type="text"
              class="h-12 w-full rounded-[12px] border pl-10 pr-4 text-[15px] outline-none transition focus:ring-2 focus:ring-[color:var(--ring)]"
              style="background: var(--secondary); border-color: var(--border); color: var(--foreground);"
              placeholder="搜索问题，例如：导出、隐私、设备连接"
            />
          </div>
          <button
            type="button"
            class="flex h-12 items-center gap-1.5 rounded-full px-4 text-[14px] font-medium transition active:scale-95"
            style="background: var(--secondary); color: var(--foreground);"
            @click="resetSearch"
          >
            清空
          </button>
        </div>

        <div class="mt-3 flex flex-wrap gap-2">
          <button
            v-for="tag in quickTags"
            :key="tag"
            type="button"
            class="rounded-full px-3 py-1.5 text-[13px] font-medium transition active:scale-95"
            :style="keyword === tag
              ? { background: 'var(--brand-500)', color: 'var(--primary-foreground)' }
              : { background: 'var(--secondary)', color: 'var(--foreground)', border: '1px solid var(--border)' }"
            @click="keyword = tag"
          >
            {{ tag }}
          </button>
        </div>
      </section>

      <!-- 结果 -->
      <section v-if="filteredFaqs.length" class="space-y-2">
        <div
          v-for="item in filteredFaqs"
          :key="item.q"
          class="rounded-[12px] border p-4"
          style="background: var(--secondary); border-color: var(--border);"
        >
          <p class="text-[15px] font-medium" style="color: var(--foreground);">{{ item.q }}</p>
          <p class="mt-1 text-[13px] leading-5" style="color: var(--muted-foreground);">{{ item.a }}</p>
          <div class="mt-2 flex flex-wrap gap-1.5">
            <span
              v-for="tag in item.tags"
              :key="tag"
              class="rounded-full border px-2 py-0.5 text-[11px]"
              style="background: var(--card); border-color: var(--border); color: var(--muted-foreground);"
            >
              {{ tag }}
            </span>
          </div>
        </div>
      </section>

      <!-- 空状态 -->
      <div
        v-else
        class="rounded-[19.2px] border p-6 text-center"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div
          class="mx-auto flex h-12 w-12 items-center justify-center rounded-full"
          style="background: var(--background-200); color: var(--muted-foreground);"
        >
          <iconify-icon icon="solar:document-text-outline" width="24" height="24" />
        </div>
        <p class="mt-3 text-[15px] font-medium" style="color: var(--foreground);">没有找到相关结果</p>
        <p class="mt-1 text-[13px]" style="color: var(--muted-foreground);">可以尝试更换关键词，或直接联系支持团队。</p>
      </div>

      <!-- 提交问题 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">仍需帮助？</h2>
        <p class="mt-0.5 text-[13px]" style="color: var(--muted-foreground);">复杂问题可以直接提交问题单，我们会带着上下文跟进。</p>
        <button
          type="button"
          class="mt-3 flex h-[48px] w-full items-center justify-center gap-2 rounded-full text-[15px] font-semibold transition active:scale-[0.98]"
          style="background: var(--primary); color: var(--primary-foreground);"
          @click="contactSupport"
        >
          <iconify-icon icon="solar:chat-round-dots-outline" width="18" height="18" />
          提交问题单
        </button>
      </section>
    </div>
  </ProfileSubPage>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from '@/composables/useToast'
import ProfileSubPage from '../components/ProfileSubPage.vue'

type FaqItem = {
  q: string
  a: string
  tags: string[]
}

const route = useRoute()
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
</script>
