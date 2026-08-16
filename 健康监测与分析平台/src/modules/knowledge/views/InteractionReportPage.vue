<template>
  <div class="space-y-5 pb-6 text-slate-950">
    <ClinicalFeatureNavBar title="交互报告与过敏" back-to="/knowledge" />

    <ClinicalPageHeader
      eyebrow="交互与过敏"
      title="交互报告与过敏"
      description="六类告警、中西药服用间隔安排、个人过敏原管理。"
    />

    <div class="flex gap-1 rounded-[1.2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] p-1">
      <button
        v-for="t in tabs"
        :key="t.value"
        type="button"
        class="flex flex-1 items-center justify-center gap-1.5 rounded-[1rem] px-3 py-2.5 text-sm font-medium transition"
        :class="activeTab === t.value ? 'bg-white text-teal-800 shadow-sm' : 'text-slate-500 hover:text-slate-700'"
        @click="activeTab = t.value"
      >
        <iconify-icon :icon="t.icon" width="16" height="16" />
        {{ t.label }}
      </button>
    </div>

    <!-- Tab 1: 交互报告 -->
    <div v-if="activeTab === 'report'" class="space-y-3">
      <div class="grid grid-cols-2 gap-2 sm:grid-cols-3">
        <ClinicalStatCard
          label="总告警数"
          :value="String(report?.totalWarnings ?? 0)"
          icon="solar:shield-warning-outline"
          :tone="(report?.totalWarnings ?? 0) > 0 ? 'danger' : 'success'"
        />
        <ClinicalStatCard
          label="中西药交互"
          :value="String(report?.tcmWmInteractions.length ?? 0)"
          icon="solar:danger-circle-outline"
          tone="warning"
        />
        <ClinicalStatCard
          label="DDI 警告"
          :value="String(report?.ddiWarnings.length ?? 0)"
          icon="solar:danger-triangle-outline"
          tone="warning"
        />
      </div>

      <ClinicalStateNotice
        v-if="reportLoading"
        tone="info"
        title="加载中…"
        description="正在生成交互报告"
      />
      <ClinicalStateNotice
        v-else-if="!report || report.totalWarnings === 0"
        tone="success"
        title="无告警"
        :description="(report?.summary[0]) || '当前用药清单未检测到交互'"
      />

      <template v-else>
        <ClinicalSurfaceCard
          v-for="group in alertGroups"
          :key="group.key"
          :title="group.title"
        >
          <ClinicalStateNotice
            v-if="!group.records.length"
            tone="success"
            title="无"
            description="—"
          />
          <ul v-else class="space-y-2">
            <li
              v-for="(r, idx) in group.records"
              :key="idx"
              class="rounded-2xl border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3"
            >
              <div class="flex items-center gap-2">
                <span
                  class="rounded-full px-2 py-0.5 text-xs"
                  :class="severityClass(r.severity)"
                >{{ r.severity }}</span>
                <span class="text-sm font-medium text-slate-950">{{ r.drugA }} ↔ {{ r.drugB }}</span>
              </div>
              <p class="mt-1 text-sm leading-6 text-slate-700">{{ r.description }}</p>
              <p v-if="r.source" class="mt-1 text-xs text-slate-400">来源：{{ r.source }}</p>
            </li>
          </ul>
        </ClinicalSurfaceCard>
      </template>
    </div>

    <!-- Tab 2: 用药间隔 -->
    <div v-if="activeTab === 'schedule'" class="space-y-3">
      <ClinicalStateNotice
        v-if="scheduleLoading"
        tone="info"
        title="加载中…"
        description="正在生成今日用药间隔"
      />
      <template v-else-if="schedule">
        <ClinicalSurfaceCard
          v-for="slot in scheduleSlots"
          :key="slot.key"
          :title="slot.title"
        >
          <ClinicalStateNotice
            v-if="!slot.items.length"
            tone="empty"
            title="无用药"
            description="—"
          />
          <ul v-else class="space-y-2">
            <li
              v-for="(item, idx) in slot.items"
              :key="idx"
              class="rounded-2xl border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3"
            >
              <div class="flex items-center gap-2">
                <span class="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">{{ item.medicineType }}</span>
                <span class="text-base font-semibold text-slate-950">{{ item.drugName }}</span>
                <span class="ml-auto text-sm text-teal-700">{{ item.suggestedTime }}</span>
              </div>
              <p v-if="item.reason" class="mt-1 text-sm text-slate-600">{{ item.reason }}</p>
              <p v-if="item.intervalMinutes" class="mt-0.5 text-xs text-slate-400">建议间隔 {{ item.intervalMinutes }} 分钟</p>
            </li>
          </ul>
        </ClinicalSurfaceCard>

        <ClinicalSurfaceCard v-if="schedule.notes.length" title="注意事项">
          <ul class="list-disc space-y-1 pl-5 text-sm text-slate-700">
            <li v-for="(n, idx) in schedule.notes" :key="idx">{{ n }}</li>
          </ul>
        </ClinicalSurfaceCard>
      </template>
    </div>

    <!-- Tab 3: 过敏管理 -->
    <div v-if="activeTab === 'allergy'" class="space-y-3">
      <ClinicalSurfaceCard title="添加过敏原">
        <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
          <input
            v-model="allergyForm.allergen"
            type="text"
            placeholder="过敏原（如：青霉素）"
            class="rounded-2xl border border-[color:var(--surface-border)] bg-white px-4 py-2.5 text-sm outline-none"
          />
          <input
            v-model="allergyForm.allergenType"
            type="text"
            placeholder="类型（drug / food / herb）"
            class="rounded-2xl border border-[color:var(--surface-border)] bg-white px-4 py-2.5 text-sm outline-none"
          />
          <input
            v-model="allergyForm.severity"
            type="text"
            placeholder="严重程度（mild / moderate / severe）"
            class="rounded-2xl border border-[color:var(--surface-border)] bg-white px-4 py-2.5 text-sm outline-none"
          />
          <input
            v-model="allergyForm.note"
            type="text"
            placeholder="备注"
            class="rounded-2xl border border-[color:var(--surface-border)] bg-white px-4 py-2.5 text-sm outline-none"
          />
        </div>
        <div class="mt-3">
          <Button :loading="allergySaving" :disabled="!allergyForm.allergen.trim()" @click="addAllergy">添加</Button>
        </div>
      </ClinicalSurfaceCard>

      <ClinicalSurfaceCard title="我的过敏原">
        <ClinicalStateNotice
          v-if="!allergies.length"
          tone="empty"
          title="暂无过敏原"
          description="请添加您的过敏信息。"
        />
        <ul v-else class="space-y-2">
          <li
            v-for="a in allergies"
            :key="a.id"
            class="flex items-center justify-between rounded-2xl border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3"
          >
            <div>
              <p class="text-base font-medium text-slate-950">{{ a.allergen }}</p>
              <p class="mt-1 text-xs text-slate-500">
                {{ a.allergenType || '—' }} · {{ a.severity || '—' }}
                <span v-if="a.note"> · {{ a.note }}</span>
              </p>
            </div>
            <Button size="sm" variant="ghost" @click="removeAllergy(a.id)">删除</Button>
          </li>
        </ul>
      </ClinicalSurfaceCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ClinicalFeatureNavBar from '@/shared/components/clinical/ClinicalFeatureNavBar.vue'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import ClinicalStatCard from '@/shared/components/clinical/ClinicalStatCard.vue'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'
import Button from '@/shared/components/ui/Button.vue'
import {
  addAllergy as apiAddAllergy,
  getDosingSchedule,
  getInteractionReport,
  listAllergies,
  removeAllergy as apiRemoveAllergy,
  type AllergyItem,
  type AllergySaveRequest,
  type DosingSchedule,
  type InteractionRecord,
  type InteractionReport,
} from '@/api/modules/knowledge'

const tabs = [
  { value: 'report', label: '交互报告', icon: 'solar:shield-warning-outline' },
  { value: 'schedule', label: '用药间隔', icon: 'solar:clock-circle-outline' },
  { value: 'allergy', label: '过敏管理', icon: 'solar:danger-circle-outline' },
] as const

const activeTab = ref<'report' | 'schedule' | 'allergy'>('report')

const report = ref<InteractionReport | null>(null)
const reportLoading = ref(false)
const schedule = ref<DosingSchedule | null>(null)
const scheduleLoading = ref(false)
const allergies = ref<AllergyItem[]>([])
const allergySaving = ref(false)
const allergyForm = ref<AllergySaveRequest>({ allergen: '', allergenType: '', severity: '', note: '' })

const alertGroups = computed(() => [
  { key: 'tcmIncompat', title: '十八反十九畏', records: report.value?.tcmIncompatibilities ?? [] },
  { key: 'tcmWm', title: '中西药交互', records: report.value?.tcmWmInteractions ?? [] },
  { key: 'drugFood', title: '药食相互作用', records: report.value?.drugFoodInteractions ?? [] },
  { key: 'ddi', title: 'DDI 警告', records: report.value?.ddiWarnings ?? [] },
  { key: 'allergy', title: '过敏冲突', records: report.value?.allergyConflicts ?? [] },
  { key: 'contraindicated', title: '禁忌人群', records: report.value?.contraindicatedGroupWarnings ?? [] },
])

const scheduleSlots = computed(() => [
  { key: 'morning', title: '早上', items: schedule.value?.morning ?? [] },
  { key: 'noon', title: '中午', items: schedule.value?.noon ?? [] },
  { key: 'evening', title: '晚上', items: schedule.value?.evening ?? [] },
])

function severityClass(severity: string): string {
  const s = (severity || '').toLowerCase()
  if (s === 'high' || s === 'severe') return 'bg-rose-50 text-rose-700'
  if (s === 'moderate') return 'bg-amber-50 text-amber-700'
  return 'bg-slate-100 text-slate-600'
}

async function loadReport() {
  reportLoading.value = true
  try {
    report.value = await getInteractionReport()
  } finally {
    reportLoading.value = false
  }
}

async function loadSchedule() {
  scheduleLoading.value = true
  try {
    schedule.value = await getDosingSchedule()
  } finally {
    scheduleLoading.value = false
  }
}

async function loadAllergies() {
  allergies.value = await listAllergies()
}

async function addAllergy() {
  if (!allergyForm.value.allergen.trim()) return
  allergySaving.value = true
  try {
    const item = await apiAddAllergy(allergyForm.value)
    allergies.value.push(item)
    allergyForm.value = { allergen: '', allergenType: '', severity: '', note: '' }
    await loadReport()
  } finally {
    allergySaving.value = false
  }
}

async function removeAllergy(id: number) {
  await apiRemoveAllergy(id)
  allergies.value = allergies.value.filter((a) => a.id !== id)
  await loadReport()
}

onMounted(() => {
  loadReport()
  loadSchedule()
  loadAllergies()
})
</script>
