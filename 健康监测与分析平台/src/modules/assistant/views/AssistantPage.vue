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
              <p class="whitespace-pre-wrap text-sm leading-7">{{ message.content || '正在生成回答...' }}</p>
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
import { streamConsultQuestion } from '@/api/modules/consult'
import { assistantPresetQuestions } from '@/modules/assistant/presets'

type AssistantMessage = {
  id: string
  role: 'user' | 'assistant'
  content: string
  suggestions?: string[]
}

const STORAGE_KEY = 'hm_assistant_history'

const router = useRouter()
const draftQuestion = ref('')
const loading = ref(false)
const messageViewport = ref<HTMLElement | null>(null)
const messages = ref<AssistantMessage[]>([])

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

function persistMessages() {
  if (typeof window === 'undefined') return
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(messages.value.slice(-30)))
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
    messages.value = Array.isArray(parsed) && parsed.length ? parsed : createWelcomeMessages()
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
    await streamConsultQuestion(
      {
        question: normalized,
        scene: 'assistant',
      },
      {
        onChunk(delta) {
          pendingMessage.content += delta
          void scrollMessagesToBottom()
        },
        onComplete(response) {
          pendingMessage.id = response.requestId
          pendingMessage.content = response.answer || pendingMessage.content
          pendingMessage.suggestions = response.suggestions
        },
      },
    )
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

watch(
  messages,
  () => {
    persistMessages()
  },
  { deep: true },
)

onMounted(async () => {
  loadMessages()
  await scrollMessagesToBottom()
})
</script>
