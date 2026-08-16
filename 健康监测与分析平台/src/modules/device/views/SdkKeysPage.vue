<template>
  <div class="apple-monitor pb-6">
    <!-- Page Header -->
    <div class="mx-auto max-w-[420px] px-4 pt-4">
      <div class="flex items-center gap-2">
        <button
          type="button"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full transition active:scale-95"
          style="background: var(--secondary); color: var(--foreground);"
          aria-label="返回"
          @click="router.back()"
        >
          <iconify-icon icon="solar:alt-arrow-left-outline" width="20" height="20" />
        </button>
        <h1 class="text-[28px] font-semibold tracking-[-0.02em]" style="color: var(--foreground);">开放 SDK 密钥</h1>
      </div>
      <p class="mt-0.5 pl-11 text-[14px]" style="color: var(--muted-foreground);">
        为第三方应用签发 API Key，接入设备聚合平台
      </p>
    </div>

    <!-- 创建新 Key -->
    <div class="mx-auto mt-4 max-w-[420px] px-4">
      <div
        class="rounded-[19.2px] p-[16px]"
        style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[15px] font-semibold" style="color: var(--foreground);">签发新 API Key</h2>
        <p class="mt-1 text-[12px]" style="color: var(--muted-foreground);">
          明文 Key 仅在创建时显示一次，请妥善保存。
        </p>

        <div class="mt-3 space-y-2">
          <input
            v-model="newAppName"
            type="text"
            placeholder="应用名称（如：我的健康助手）"
            class="h-10 w-full rounded-[10px] px-3 text-[14px] outline-none"
            style="background: var(--secondary); color: var(--foreground); border: 1px solid var(--border);"
          />
          <input
            v-model="newContactEmail"
            type="email"
            placeholder="联系邮箱（可选）"
            class="h-10 w-full rounded-[10px] px-3 text-[14px] outline-none"
            style="background: var(--secondary); color: var(--foreground); border: 1px solid var(--border);"
          />
          <button
            type="button"
            class="h-10 w-full rounded-[10px] text-[14px] font-medium transition active:scale-[0.98] disabled:opacity-50"
            style="background: var(--brand-500); color: var(--primary-foreground);"
            :disabled="creating || !newAppName.trim()"
            @click="onCreateKey"
          >
            <iconify-icon v-if="creating" icon="solar:refresh-outline" width="14" height="14" class="animate-spin inline-block mr-1" />
            生成 API Key
          </button>
        </div>

        <!-- 新 Key 展示（仅创建后显示一次） -->
        <div
          v-if="newlyCreatedKey"
          class="mt-3 rounded-[10px] p-[12px]"
          style="background: var(--brand-50, rgba(0, 122, 255, 0.08)); border: 1px solid var(--brand-500);"
        >
          <div class="flex items-center gap-2">
            <iconify-icon icon="solar:danger-triangle-outline" width="16" height="16" style="color: var(--brand-500);" />
            <span class="text-[12px] font-semibold" style="color: var(--brand-500);">请立即复制保存</span>
          </div>
          <p class="mt-2 break-all font-mono text-[12px]" style="color: var(--foreground);">{{ newlyCreatedKey }}</p>
          <button
            type="button"
            class="mt-2 h-7 rounded-full px-3 text-[12px] font-medium"
            style="background: var(--brand-500); color: var(--primary-foreground);"
            @click="onCopyKey"
          >
            <iconify-icon icon="solar:copy-outline" width="12" height="12" class="inline-block mr-1" />
            复制 Key
          </button>
        </div>
      </div>
    </div>

    <!-- 已有 Key 列表 -->
    <div class="mx-auto mt-4 max-w-[420px] px-4">
      <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">已签发的 Key</h2>

      <div v-if="loading" class="mt-3 flex h-16 items-center justify-center">
        <iconify-icon icon="solar:refresh-outline" width="20" height="20" class="animate-spin" style="color: var(--muted-foreground);" />
      </div>

      <div v-else-if="keys.length === 0" class="mt-3 text-center text-[13px]" style="color: var(--muted-foreground);">
        暂无 API Key
      </div>

      <div v-else class="mt-3 space-y-2">
        <div
          v-for="k in keys"
          :key="k.id"
          class="rounded-[19.2px] p-[14px]"
          style="background: var(--card); border: 1px solid var(--border); box-shadow: var(--shadow-xs);"
        >
          <div class="flex items-center justify-between">
            <div class="min-w-0 flex-1">
              <p class="truncate text-[15px] font-semibold" style="color: var(--foreground);">{{ k.appName }}</p>
              <p class="mt-0.5 truncate font-mono text-[11px]" style="color: var(--muted-foreground);">{{ k.keyPreview }}</p>
            </div>
            <span
              class="shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium"
              :style="k.status === 'active'
                ? 'background: rgba(48, 209, 97, 0.15); color: rgb(48, 209, 97);'
                : 'background: var(--secondary); color: var(--muted-foreground);'"
            >{{ k.status === 'active' ? '启用' : '已撤销' }}</span>
          </div>

          <div class="mt-2 flex items-center justify-between text-[11px]" style="color: var(--muted-foreground);">
            <span>创建：{{ formatTime(k.createdAt) }}</span>
            <span v-if="k.lastUsedAt">最近使用：{{ formatTime(k.lastUsedAt) }}</span>
            <span v-else>未使用</span>
          </div>

          <button
            v-if="k.status === 'active'"
            type="button"
            class="mt-2 h-7 rounded-full px-3 text-[12px] font-medium transition active:scale-95"
            style="background: var(--destructive); color: white;"
            @click="onRevoke(k.id)"
          >撤销</button>
        </div>
      </div>
    </div>

    <!-- 接入说明 -->
    <div class="mx-auto mt-5 max-w-[420px] px-4">
      <div
        class="rounded-[19.2px] p-[14px]"
        style="background: var(--card); border: 1px solid var(--border);"
      >
        <h3 class="text-[14px] font-semibold" style="color: var(--foreground);">接入示例</h3>
        <pre class="mt-2 overflow-x-auto rounded-[8px] p-[10px] text-[11px]"
             style="background: var(--secondary); color: var(--foreground);">curl -X POST https://your-host/api/devices/sdk/reading \
  -H "X-SDK-API-Key: ahsdk_xxxxxxxx" \
  -H "Content-Type: application/json" \
  -d '{"sourceDevice":"MyDevice","heartRateAvgBpm":72}'</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import { http } from '@/api/http'

const router = useRouter()
const toast = useToastStore()

const loading = ref(false)
const creating = ref(false)
const keys = ref<DevKeyItem[]>([])
const newAppName = ref('')
const newContactEmail = ref('')
const newlyCreatedKey = ref<string | null>(null)

interface DevKeyItem {
  id: number
  appName: string
  contactEmail: string
  keyPreview: string
  status: string
  createdAt: string | null
  lastUsedAt: string | null
}

onMounted(loadKeys)

async function loadKeys() {
  loading.value = true
  try {
    const { data } = await http.get<any[]>('/devices/sdk/keys')
    keys.value = (data || []).map((d: any) => ({
      id: d.id,
      appName: d.appName,
      contactEmail: d.contactEmail,
      keyPreview: d.keyPreview,
      status: d.status,
      createdAt: d.createdAt,
      lastUsedAt: d.lastUsedAt,
    }))
  } catch (e) {
    // mock 模式或失败时显示空列表
    keys.value = []
  } finally {
    loading.value = false
  }
}

async function onCreateKey() {
  if (!newAppName.value.trim()) return
  creating.value = true
  newlyCreatedKey.value = null
  try {
    const { data } = await http.post<any>('/devices/sdk/keys', {
      appName: newAppName.value.trim(),
      contactEmail: newContactEmail.value.trim() || undefined,
    })
    newlyCreatedKey.value = data?.apiKey ?? null
    newAppName.value = ''
    newContactEmail.value = ''
    await loadKeys()
    toast.success('API Key 已生成')
  } catch (e: any) {
    toast.error(e?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function onRevoke(id: number) {
  if (!confirm('确定要撤销此 API Key 吗？此操作不可恢复。')) return
  try {
    await http.delete(`/devices/sdk/keys/${id}`)
    await loadKeys()
    toast.success('已撤销')
  } catch (e: any) {
    toast.error(e?.message || '撤销失败')
  }
}

async function onCopyKey() {
  if (!newlyCreatedKey.value) return
  try {
    await navigator.clipboard.writeText(newlyCreatedKey.value)
    toast.success('已复制到剪贴板')
  } catch {
    toast.error('复制失败，请手动选择复制')
  }
}

function formatTime(iso: string | null): string {
  if (!iso) return ''
  try {
    const d = new Date(iso)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  } catch {
    return iso
  }
}
</script>
