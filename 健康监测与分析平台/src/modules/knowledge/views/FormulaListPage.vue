<template>
  <div class="space-y-5 pb-6 text-slate-950">
    <ClinicalFeatureNavBar title="方剂管理" back-to="/knowledge" />

    <ClinicalPageHeader
      eyebrow="方剂"
      title="方剂管理"
      description="维护个人方剂，记录药材组成、克数与角色。"
    />

    <ClinicalSurfaceCard title="我的方剂">
      <template #headerRight>
        <Button size="sm" @click="openCreate">新建方剂</Button>
      </template>

      <div v-if="loading" class="py-6 text-center text-sm text-slate-600">加载中…</div>
      <ClinicalStateNotice
        v-else-if="viewState === 'error'"
        tone="error"
        title="加载失败"
        :description="errorMsg || '请稍后重试'"
        action-label="重试"
        @action="loadFormulas"
      />
      <ClinicalStateNotice
        v-else-if="!formulas.length"
        tone="empty"
        title="暂无方剂"
        description="点击右上角「新建方剂」开始记录。"
      />
      <div v-else class="space-y-3">
        <article
          v-for="f in formulas"
          :key="f.id"
          class="flex items-center justify-between rounded-[1.15rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-3"
        >
          <div>
            <p class="text-base font-semibold text-slate-950">{{ f.name }}</p>
            <p class="mt-1 text-xs text-slate-500">{{ f.herbCount }} 味药材 · 创建于 {{ formatDate(f.createdAt) }}</p>
          </div>
          <div class="flex gap-2">
            <Button size="sm" variant="ghost" @click="viewDetail(f.id)">查看</Button>
            <Button size="sm" variant="ghost" @click="removeFormula(f.id)">删除</Button>
          </div>
        </article>
      </div>
    </ClinicalSurfaceCard>

    <!-- 创建/详情弹窗 -->
    <div
      v-if="dialogOpen"
      class="fixed inset-0 z-30 flex items-center justify-center bg-slate-900/50 p-4"
      @click.self="dialogOpen = false"
    >
      <div class="max-h-[90vh] w-full max-w-lg overflow-auto rounded-[1.5rem] bg-white p-5">
        <h3 class="text-lg font-semibold">{{ editing ? '方剂详情' : '新建方剂' }}</h3>
        <div class="mt-4 space-y-3">
          <div>
            <label class="text-xs text-slate-500">方剂名称</label>
            <input
              v-model="form.name"
              type="text"
              :disabled="editing"
              class="mt-1 w-full rounded-2xl border border-[color:var(--surface-border)] bg-white px-4 py-2.5 text-sm outline-none disabled:bg-slate-50"
              placeholder="如：四物汤"
            />
          </div>
          <div>
            <label class="text-xs text-slate-500">诊断</label>
            <input
              v-model="form.diagnosis"
              type="text"
              :disabled="editing"
              class="mt-1 w-full rounded-2xl border border-[color:var(--surface-border)] bg-white px-4 py-2.5 text-sm outline-none disabled:bg-slate-50"
              placeholder="如：气血两虚"
            />
          </div>
          <div>
            <label class="text-xs text-slate-500">药材列表（每行一味：药材名 克数 角色）</label>
            <textarea
              v-model="herbsText"
              :disabled="editing"
              rows="6"
              class="mt-1 w-full rounded-2xl border border-[color:var(--surface-border)] bg-white px-4 py-2.5 text-sm outline-none disabled:bg-slate-50"
              placeholder="当归 12 君&#10;白芍 9 臣&#10;熟地 12 佐"
            />
          </div>
          <div>
            <label class="text-xs text-slate-500">备注</label>
            <textarea
              v-model="form.notes"
              :disabled="editing"
              rows="2"
              class="mt-1 w-full rounded-2xl border border-[color:var(--surface-border)] bg-white px-4 py-2.5 text-sm outline-none disabled:bg-slate-50"
            />
          </div>
          <div v-if="editing && detail" class="rounded-2xl bg-slate-50 p-3 text-sm">
            <p class="mb-2 font-medium">药材详情</p>
            <ul class="space-y-1 text-slate-700">
              <li v-for="(h, idx) in detail.herbs" :key="idx">
                {{ h.herbName }} {{ h.dosageGrams ?? '' }}g（{{ h.role || '—' }}）
                <span v-if="h.efficacy" class="text-xs text-slate-400"> · {{ h.efficacy }}</span>
              </li>
            </ul>
          </div>
        </div>
        <div class="mt-5 flex justify-end gap-2">
          <Button variant="ghost" @click="dialogOpen = false">取消</Button>
          <Button v-if="!editing" :loading="saving" @click="submitCreate">保存</Button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ClinicalFeatureNavBar from '@/shared/components/clinical/ClinicalFeatureNavBar.vue'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import ClinicalSurfaceCard from '@/shared/components/clinical/ClinicalSurfaceCard.vue'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'
import Button from '@/shared/components/ui/Button.vue'
import {
  createFormula,
  deleteFormula,
  getFormula,
  listFormulas,
  type FormulaListItem,
  type FormulaResponse,
  type FormulaSaveRequest,
} from '@/api/modules/knowledge'

const formulas = ref<FormulaListItem[]>([])
const loading = ref(false)
const saving = ref(false)
const errorMsg = ref('')
const viewState = ref<'idle' | 'empty' | 'error' | 'success'>('idle')

const dialogOpen = ref(false)
const editing = ref(false)
const form = ref<{ name: string; diagnosis: string; notes: string }>({ name: '', diagnosis: '', notes: '' })
const herbsText = ref('')
const detail = ref<FormulaResponse | null>(null)

async function loadFormulas() {
  loading.value = true
  errorMsg.value = ''
  try {
    formulas.value = await listFormulas()
    viewState.value = formulas.value.length ? 'success' : 'empty'
  } catch (e: any) {
    errorMsg.value = e?.message ?? '加载失败'
    viewState.value = 'error'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = false
  form.value = { name: '', diagnosis: '', notes: '' }
  herbsText.value = ''
  detail.value = null
  dialogOpen.value = true
}

async function viewDetail(id: number) {
  try {
    detail.value = await getFormula(id)
    editing.value = true
    form.value = {
      name: detail.value.name,
      diagnosis: detail.value.diagnosis ?? '',
      notes: detail.value.notes ?? '',
    }
    herbsText.value = detail.value.herbs
      .map((h) => `${h.herbName} ${h.dosageGrams ?? ''} ${h.role ?? ''}`.trim())
      .join('\n')
    dialogOpen.value = true
  } catch (e: any) {
    errorMsg.value = e?.message ?? '加载详情失败'
  }
}

async function removeFormula(id: number) {
  if (!confirm('确认删除此方剂？')) return
  try {
    await deleteFormula(id)
    formulas.value = formulas.value.filter((f) => f.id !== id)
  } catch (e: any) {
    errorMsg.value = e?.message ?? '删除失败'
  }
}

async function submitCreate() {
  if (!form.value.name.trim()) {
    alert('请输入方剂名称')
    return
  }
  const herbs = herbsText.value
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const parts = line.split(/\s+/)
      return {
        herbName: parts[0] ?? '',
        dosageGrams: parts[1] ? Number(parts[1]) : null,
        role: parts[2] ?? '',
      }
    })
    .filter((h) => h.herbName)

  if (!herbs.length) {
    alert('请至少输入一味药材')
    return
  }

  const payload: FormulaSaveRequest = {
    name: form.value.name.trim(),
    diagnosis: form.value.diagnosis.trim(),
    notes: form.value.notes.trim(),
    herbs,
  }

  saving.value = true
  try {
    await createFormula(payload)
    dialogOpen.value = false
    await loadFormulas()
  } catch (e: any) {
    alert(e?.message ?? '保存失败')
  } finally {
    saving.value = false
  }
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString('zh-CN', { dateStyle: 'short', timeStyle: 'short' })
  } catch {
    return iso
  }
}

onMounted(loadFormulas)
</script>
