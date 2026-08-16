<template>
  <ClinicalSurfaceCard title="设备与健康源">
    <template #headerRight>
      <Badge v-if="devices.length" variant="info">{{ devices.length }} 台</Badge>
    </template>

    <ClinicalStateNotice
      v-if="!devices.length"
      tone="empty"
      title="暂未连接设备"
      description="连接 Apple Watch 或通过 Apple Health 同步健康数据。"
    />

    <div v-else class="space-y-2">
      <article
        v-for="item in devices"
        :key="item.id"
        class="flex items-center gap-3 rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-3 py-3"
      >
        <div class="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-white shadow-[var(--elevation-soft)]">
          <Watch class="h-4 w-4 text-slate-700" />
        </div>

        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <p class="truncate text-sm font-semibold text-slate-950">{{ item.name }}</p>
            <Badge :variant="item.connected ? 'success' : 'warning'" class="shrink-0">
              {{ item.connected ? '已连接' : '未连接' }}
            </Badge>
          </div>
          <p class="mt-0.5 text-xs text-slate-500">
            {{ formatSyncTime(item.lastSyncAt) }}
            <template v-if="item.battery > 0"> · 电量 {{ item.battery }}%</template>
          </p>
        </div>

        <div class="flex shrink-0 items-center gap-1">
          <button
            type="button"
            class="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 transition hover:bg-[color:var(--accent-soft)] hover:text-[color:var(--accent-strong)]"
            :disabled="syncingId === item.id"
            aria-label="同步设备数据"
            @click="$emit('sync-device', item.id)"
          >
            <RefreshCw class="h-3.5 w-3.5" :class="{ 'animate-spin': syncingId === item.id }" />
          </button>
          <button
            type="button"
            class="inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition hover:bg-rose-50 hover:text-rose-500"
            aria-label="删除设备"
            @click="$emit('remove-device', item.id)"
          >
            <Trash2 class="h-3.5 w-3.5" />
          </button>
        </div>
      </article>
    </div>

    <div class="mt-3 grid gap-2 sm:grid-cols-2">
      <Button variant="secondary" class="w-full gap-1.5" @click="$emit('connect-ble')">
        <Bluetooth class="h-4 w-4" />
        蓝牙配对
      </Button>
      <Button variant="secondary" class="w-full gap-1.5" @click="$emit('sync-apple-health')">
        <Heart class="h-4 w-4" />
        健康数据同步
      </Button>
    </div>

    <button
      v-if="!showForm"
      type="button"
      class="mt-2 w-full rounded-[1rem] border border-dashed border-[color:var(--surface-border)] px-3 py-2 text-xs text-slate-500 transition hover:border-[color:var(--accent-strong)] hover:text-[color:var(--accent-strong)]"
      @click="$emit('open-form')"
    >
      手动添加设备
    </button>

    <div
      v-if="showForm"
      class="mt-3 rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4"
    >
      <div class="grid gap-3 sm:grid-cols-2">
        <label class="block">
          <span class="text-xs text-slate-500">品牌</span>
          <input
            :value="form.brand"
            class="mt-1.5 w-full rounded-[1rem] border border-[color:var(--surface-border)] bg-white px-3 py-2 text-sm outline-none transition focus:border-[color:var(--accent-strong)]"
            placeholder="Apple"
            @input="$emit('update:form', { key: 'brand', value: ($event.target as HTMLInputElement).value })"
          />
        </label>
        <label class="block">
          <span class="text-xs text-slate-500">型号</span>
          <input
            :value="form.model"
            class="mt-1.5 w-full rounded-[1rem] border border-[color:var(--surface-border)] bg-white px-3 py-2 text-sm outline-none transition focus:border-[color:var(--accent-strong)]"
            placeholder="Watch Series 9"
            @input="$emit('update:form', { key: 'model', value: ($event.target as HTMLInputElement).value })"
          />
        </label>
      </div>
      <div class="mt-3 grid grid-cols-2 gap-2">
        <Button variant="secondary" @click="$emit('cancel-form')">取消</Button>
        <Button :loading="creating" @click="$emit('save-device')">保存</Button>
      </div>
    </div>
  </ClinicalSurfaceCard>
</template>

<script setup lang="ts">
import { Bluetooth, Heart, Watch, RefreshCw, Trash2 } from 'lucide-vue-next'
import type { CreateDevicePayload, DeviceItem } from '@/api/modules/device'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import Badge from '@/shared/components/ui/Badge.vue'
import Button from '@/shared/components/ui/Button.vue'

defineProps<{
  devices: DeviceItem[]
  showForm: boolean
  creating: boolean
  syncingId: number | null
  form: CreateDevicePayload
}>()

defineEmits<{
  (e: 'open-form'): void
  (e: 'cancel-form'): void
  (e: 'save-device'): void
  (e: 'connect-ble'): void
  (e: 'sync-apple-health'): void
  (e: 'sync-device', id: number): void
  (e: 'remove-device', id: number): void
  (e: 'update:form', payload: { key: keyof CreateDevicePayload; value: string }): void
}>()

const formatSyncTime = (value: string) => {
  if (!value) return '未同步'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
</script>