<template>
  <div class="assistant-page flex h-dvh flex-col bg-[linear-gradient(180deg,#dfe8ff_0%,#edf3ff_40%,#f7f9ff_100%)] text-slate-950">
    <header class="sticky top-0 z-20 border-b border-white/70 bg-white/60 backdrop-blur">
      <div class="mx-auto flex max-w-[960px] items-center gap-3 px-4 py-3 lg:px-6">
        <button
          type="button"
          class="inline-flex h-11 w-11 items-center justify-center rounded-full border border-white/80 bg-white/90 text-slate-900 shadow-sm transition hover:bg-white"
          aria-label="返回"
          @click="goBack"
        >
          <iconify-icon icon="solar:alt-arrow-left-outline" width="22" height="22" />
        </button>
        <div class="min-w-0">
          <p class="text-lg font-semibold text-slate-950">智能助手</p>
          <p class="text-xs text-slate-500">历史会自动保留</p>
        </div>
        <button
          type="button"
          class="ml-auto inline-flex h-11 w-11 items-center justify-center rounded-full border border-white/80 bg-white/90 text-slate-900 shadow-sm transition hover:bg-white"
          aria-label="新对话"
          @click="startNewConversation"
        >
          <span class="text-lg font-bold leading-none">+</span>
        </button>
        <button
          type="button"
          class="inline-flex h-11 w-11 items-center justify-center rounded-full border border-white/80 bg-white/90 text-slate-900 shadow-sm transition hover:bg-white"
          aria-label="历史记录"
          @click="showHistory = !showHistory"
        >
          <iconify-icon icon="solar:history-outline" width="20" height="20" />
        </button>
      </div>
    </header>

    <div ref="messageViewport" class="min-h-0 flex-1 overflow-y-auto">
      <div class="mx-auto flex max-w-[960px] flex-col gap-4 px-4 py-4 pb-28 lg:px-6 lg:pb-32">
        <section class="rounded-[1.8rem] border border-white/80 bg-white/70 px-4 py-4 shadow-[0_18px_40px_rgba(148,163,184,0.14)]">
          <h1 class="text-[1.9rem] font-black leading-tight tracking-tight text-slate-950 lg:text-[2.3rem]">
            AI 健康助手
          </h1>
          <p class="mt-2 text-sm leading-6 text-slate-600">报告解读、康复建议、日常健康问题都可以直接问。</p>
        </section>

        <section class="grid gap-2 sm:grid-cols-2">
          <button
            v-for="question in assistantPresetQuestions"
            :key="question"
            type="button"
            class="rounded-[1.2rem] border border-white/85 bg-white/92 px-4 py-3 text-left text-sm font-medium text-slate-900 shadow-sm transition hover:-translate-y-0.5 hover:bg-white"
            @click="askPresetQuestion(question)"
          >
            {{ question }}
          </button>
        </section>

        <section class="flex flex-col gap-3">
          <article
            v-for="message in messages"
            :key="message.id"
            class="flex"
            :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
          >
            <div
              class="max-w-[88%] rounded-[1.6rem] px-4 py-3 shadow-sm lg:max-w-[72%]"
              :class="
                message.role === 'user'
                  ? 'bg-[color:var(--accent-strong)] text-white'
                  : 'border border-white/85 bg-white text-slate-900'
              "
            >
              <p class="whitespace-pre-wrap text-sm leading-7" v-html="renderMarkdown(message.content || '正在生成回答...')"></p>
              <div v-if="message.knowledgeSources?.length" class="mt-2 text-xs text-slate-400">
                <span>参考：</span>
                <span v-for="(src, i) in message.knowledgeSources" :key="i">{{ src }}{{ i < message.knowledgeSources.length - 1 ? '、' : '' }}</span>
              </div>
              <section v-if="message.evidence?.length" class="mt-3 rounded-xl border border-sky-100 bg-sky-50/70 p-2.5 text-xs text-slate-600">
                <p class="font-semibold text-sky-800">知识证据</p>
                <div v-for="item in message.evidence" :key="item.id" class="mt-1.5 border-t border-sky-100 pt-1.5 first:border-t-0 first:pt-0">
                  <p class="font-medium text-slate-700">{{ item.title }}</p>
                  <p class="mt-0.5 leading-5">{{ item.excerpt }}</p>
                </div>
              </section>
              <section v-if="message.safety" class="mt-3 rounded-xl border p-2.5 text-xs" :class="message.safety.level === 'emergency' ? 'border-red-200 bg-red-50 text-red-800' : 'border-amber-100 bg-amber-50/70 text-amber-800'">
                <p class="font-semibold">{{ message.safety.level === 'emergency' ? '紧急风险提示' : '回答边界与就医提示' }}</p>
                <p class="mt-1 leading-5">{{ message.safety.uncertainty }}</p>
                <p class="mt-1 leading-5">{{ message.safety.escalation }}</p>
                <div v-if="message.safety.actionTags?.length" class="mt-2 flex flex-wrap gap-1.5">
                  <span
                    v-for="tag in message.safety.actionTags"
                    :key="tag"
                    class="rounded-full bg-white/70 px-2 py-0.5 text-[11px] font-medium"
                  >
                    {{ tag }}
                  </span>
                </div>
              </section>
              <div v-if="message.suggestions?.length" class="mt-3 flex flex-wrap gap-2">
                <button
                  v-for="suggestion in message.suggestions"
                  :key="suggestion"
                  type="button"
                  class="rounded-full border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-3 py-1.5 text-xs font-medium text-slate-700 transition hover:border-[color:var(--accent-strong)] hover:bg-white"
                  @click="askPresetQuestion(suggestion)"
                >
                  {{ suggestion }}
                </button>
              </div>
            </div>
          </article>
        </section>
      </div>
    </div>

    <!-- History panel (slides in from right) -->
    <Transition name="slide-right">
      <div v-if="showHistory" class="fixed inset-y-0 right-0 z-50 flex w-80 flex-col border-l border-slate-200 bg-white shadow-xl">
        <div class="flex items-center justify-between border-b border-slate-100 px-4 py-3">
          <span class="text-base font-semibold text-slate-900">历史记录</span>
          <div class="flex items-center gap-2">
            <button
              v-if="historyItems.length"
              type="button"
              class="rounded-full px-2 py-1 text-xs text-red-500 transition hover:bg-red-50"
              @click="clearAllHistory"
            >
              清空
            </button>
            <button
              type="button"
              class="inline-flex h-8 w-8 items-center justify-center rounded-full text-slate-500 transition hover:bg-slate-100"
              @click="showHistory = false"
            >
              <iconify-icon icon="solar:close-outline" width="20" height="20" />
            </button>
          </div>
        </div>
        <div class="flex-1 overflow-y-auto">
          <div v-if="!historyItems.length" class="p-6 text-center text-sm text-slate-400">暂无历史记录</div>
          <div
            v-for="item in historyItems"
            :key="item.id"
            class="cursor-pointer border-b border-slate-50 px-4 py-3 transition hover:bg-slate-50"
            @click="loadHistoryQuestion(item)"
          >
            <p class="truncate text-sm font-medium text-slate-800">{{ item.question }}</p>
            <p class="mt-1 truncate text-xs text-slate-500">{{ item.answer?.substring(0, 60) }}...</p>
            <div class="mt-1.5 flex items-center justify-between">
              <span class="text-xs text-slate-400">{{ formatTime(item.createdAt) }}</span>
              <button
                type="button"
                class="rounded-full px-2 py-0.5 text-xs text-red-400 transition hover:bg-red-50 hover:text-red-600"
                @click.stop="deleteHistoryItem(item.id)"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- History overlay backdrop -->
    <Transition name="fade">
      <div
        v-if="showHistory"
        class="fixed inset-0 z-40 bg-black/20"
        @click="showHistory = false"
      />
    </Transition>

    <footer class="sticky bottom-0 z-20 border-t border-white/70 bg-white/90 backdrop-blur">
      <div class="mx-auto max-w-[960px] px-4 py-3 lg:px-6">
        <form @submit.prevent="submitDraftQuestion">
          <div class="flex items-end gap-3 rounded-[1.7rem] border border-[color:var(--surface-border)] bg-white px-3 py-3 shadow-sm">
            <textarea
              id="assistant-question"
              v-model="draftQuestion"
              aria-label="输入问题"
              rows="1"
              class="min-h-[44px] flex-1 resize-none bg-transparent px-1 py-2 text-base leading-6 text-slate-900 outline-none placeholder:text-slate-400"
              placeholder="输入健康问题"
            />
            <button
              type="submit"
              class="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-[color:var(--accent-strong)] text-white shadow-sm transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-40"
              :disabled="loading || !draftQuestion.trim()"
              aria-label="发送提问"
            >
              <iconify-icon icon="solar:plain-2-bold" width="20" height="20" />
            </button>
          </div>
        </form>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { streamConsultSSE, streamConsultQuestion, getConsultHistory, deleteConsultHistory, clearConsultHistory, type ConsultEvidence, type ConsultHistoryItem, type ConsultSafety } from '@/api/modules/consult'
import { assistantPresetQuestions } from '@/modules/assistant/presets'

type AssistantMessage = {
  id: string
  role: 'user' | 'assistant'
  content: string
  suggestions?: string[]
  knowledgeSources?: string[]
  evidence?: ConsultEvidence[]
  safety?: ConsultSafety | null
}

const STORAGE_KEY = 'hm_assistant_history'

const router = useRouter()
const draftQuestion = ref('')
const loading = ref(false)
const messageViewport = ref<HTMLElement | null>(null)
const messages = ref<AssistantMessage[]>([])
const showHistory = ref(false)
const historyItems = ref<ConsultHistoryItem[]>([])

async function loadHistoryList() {
  historyItems.value = await getConsultHistory(50, 0)
}

function loadHistoryQuestion(item: ConsultHistoryItem) {
  messages.value.push({
    id: createMessageId('user'),
    role: 'user',
    content: item.question,
  })
  messages.value.push({
    id: item.requestId || createMessageId('history'),
    role: 'assistant',
    content: item.answer,
    suggestions: item.suggestions,
    knowledgeSources: item.knowledgeSources,
    evidence: item.evidence,
    safety: item.safety,
  })
  showHistory.value = false
  void scrollMessagesToBottom()
}

async function deleteHistoryItem(id: number) {
  await deleteConsultHistory(id)
  historyItems.value = historyItems.value.filter(item => item.id !== id)
}

async function clearAllHistory() {
  await clearConsultHistory()
  historyItems.value = []
}

function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr)
    const now = new Date()
    const diffMs = now.getTime() - d.getTime()
    const diffMin = Math.floor(diffMs / 60000)
    if (diffMin < 1) return '刚刚'
    if (diffMin < 60) return diffMin + ' 分钟前'
    const diffHours = Math.floor(diffMin / 60)
    if (diffHours < 24) return diffHours + ' 小时前'
    const diffDays = Math.floor(diffHours / 24)
    if (diffDays < 7) return diffDays + ' 天前'
    return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  } catch {
    return dateStr
  }
}

function createWelcomeMessages(): AssistantMessage[] {
  return [
    {
      id: 'assistant-welcome',
      role: 'assistant',
      content: '你好，我可以帮你解读报告、整理康复建议，也可以回答日常健康管理问题。',
      suggestions: assistantPresetQuestions.slice(0, 3),
    },
  ]
}

function createMessageId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

/** Simple markdown-to-HTML renderer for assistant messages */
function renderMarkdown(text: string): string {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/`(.+?)`/g, '<code class="rounded bg-slate-100 px-1 py-0.5 text-xs text-slate-700">$1</code>')
    .replace(/\n/g, '<br>')
}

function persistMessages() {
  if (typeof window === 'undefined') return
  // 过滤掉 content 为空的 assistant 消息（未完成的 pending message），
  // 否则退出后再进入会一直显示"正在生成回答..."
  const toSave = messages.value.filter(
    (m) => m.role === 'user' || (m.content && m.content.trim().length > 0),
  )
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(toSave.slice(-30)))
}

function loadMessages() {
  if (typeof window === 'undefined') {
    messages.value = createWelcomeMessages()
    return
  }

  const raw = window.localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    messages.value = createWelcomeMessages()
    return
  }

  try {
    const parsed = JSON.parse(raw) as AssistantMessage[]
    // 二次清理：过滤掉 content 为空的 assistant 消息（旧数据可能已存入）
    const cleaned = Array.isArray(parsed)
      ? parsed.filter((m) => m.role === 'user' || (m.content && m.content.trim().length > 0))
      : []
    messages.value = cleaned.length ? cleaned : createWelcomeMessages()
  } catch {
    messages.value = createWelcomeMessages()
  }
}

async function scrollMessagesToBottom() {
  await nextTick()
  if (!messageViewport.value) return
  messageViewport.value.scrollTop = messageViewport.value.scrollHeight
}

function readApiErrorMessage(err: unknown): string {
  if (typeof err === 'object' && err && 'response' in err) {
    const message = (err as { response?: { data?: { message?: string } } }).response?.data?.message
    if (message) return message
  }
  return err instanceof Error ? err.message : '智能助手暂时不可用，请稍后再试。'
}

async function submitQuestion(question: string) {
  const normalized = question.trim()
  if (!normalized || loading.value) return

  messages.value.push({
    id: createMessageId('user'),
    role: 'user',
    content: normalized,
  })

  loading.value = true
  draftQuestion.value = ''

  const pendingMessage: AssistantMessage = {
    id: createMessageId('assistant-stream'),
    role: 'assistant',
    content: '',
    suggestions: [],
  }

  messages.value.push(pendingMessage)
  await scrollMessagesToBottom()

  try {
    // Use SSE streaming for real-time character-by-character display
    let usedSSE = false
    try {
      for await (const event of streamConsultSSE(normalized, 'assistant')) {
        if (event.chunk) {
          pendingMessage.content += event.chunk
          usedSSE = true
          void scrollMessagesToBottom()
        }
        if (event.done) {
          pendingMessage.id = event.done.requestId
          pendingMessage.suggestions = event.done.suggestions
          pendingMessage.evidence = event.done.evidence
          pendingMessage.safety = event.done.safety
        }
      }
    } catch {
      // SSE failed, fall through to legacy streaming
    }

    if (!usedSSE) {
      // Fallback to the legacy ndjson / direct-LLM streaming path
      await streamConsultQuestion(
        { question: normalized, scene: 'assistant' },
        {
          onChunk(delta) {
            pendingMessage.content += delta
            void scrollMessagesToBottom()
          },
          onComplete(response) {
            pendingMessage.id = response.requestId
            pendingMessage.content = response.answer || pendingMessage.content
            pendingMessage.suggestions = response.suggestions
            pendingMessage.evidence = response.evidence
            pendingMessage.safety = response.safety
          },
        },
      )
    }
  } catch (err) {
    pendingMessage.id = createMessageId('assistant-error')
    pendingMessage.content = readApiErrorMessage(err)
    pendingMessage.suggestions = assistantPresetQuestions.slice(0, 2)
  } finally {
    loading.value = false
    await scrollMessagesToBottom()
  }
}

async function submitDraftQuestion() {
  await submitQuestion(draftQuestion.value)
}

async function askPresetQuestion(question: string) {
  draftQuestion.value = question
  await submitQuestion(question)
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/home')
}

function startNewConversation() {
  messages.value = createWelcomeMessages()
  showHistory.value = false
  persistMessages()
  void scrollMessagesToBottom()
}

watch(
  messages,
  () => {
    persistMessages()
  },
  { deep: true },
)

watch(showHistory, (open) => {
  if (open) void loadHistoryList()
})

onMounted(async () => {
  loadMessages()
  await scrollMessagesToBottom()
})
</script>

<style scoped>
.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.25s ease, opacity 0.25s ease;
}
.slide-right-enter-from,
.slide-right-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
