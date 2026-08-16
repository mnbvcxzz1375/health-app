<template>
  <div class="space-y-5 pb-6 text-slate-950">
    <ClinicalFeatureNavBar title="健康知识图谱" back-to="/home" />

    <ClinicalPageHeader
      eyebrow="知识中心"
      title="健康知识图谱"
      description="整合中药材、药品临床信息、方剂、交互检查与用药间隔，为安全用药提供知识支撑。"
    />

    <section class="grid grid-cols-1 gap-3 sm:grid-cols-2">
      <button
        v-for="card in cards"
        :key="card.key"
        type="button"
        class="flex items-start gap-3 rounded-[1.35rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-secondary)] px-4 py-4 text-left transition hover:border-[color:var(--accent-strong)] hover:bg-white"
        @click="router.push(card.route)"
      >
        <div class="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-white text-slate-900 shadow-[var(--elevation-soft)]">
          <iconify-icon :icon="card.icon" width="22" height="22" />
        </div>
        <div class="flex-1">
          <p class="text-base font-semibold text-slate-950">{{ card.title }}</p>
          <p class="mt-1 text-sm leading-6 text-slate-600">{{ card.description }}</p>
        </div>
        <iconify-icon icon="solar:alt-arrow-right-outline" width="18" height="18" class="mt-1 text-slate-400" />
      </button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import ClinicalFeatureNavBar from '@/shared/components/clinical/ClinicalFeatureNavBar.vue'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'

const router = useRouter()

const cards = [
  {
    key: 'herbs',
    title: '中药材搜索',
    description: '按药名、拼音或别名检索 30+ 中药材的性味归经与功效。',
    route: '/knowledge/herbs',
    icon: 'solar:leaf-outline',
  },
  {
    key: 'formulas',
    title: '方剂管理',
    description: '维护个人方剂，记录药材组成、克数与角色（君臣佐使）。',
    route: '/knowledge/formulas',
    icon: 'solar:document-text-outline',
  },
  {
    key: 'recognize',
    title: '多药材识别',
    description: '拍照识别一张图中的多种药材，自动去重并补全药性。',
    route: '/knowledge/herbs/recognize',
    icon: 'solar:camera-outline',
  },
  {
    key: 'interactions',
    title: '交互检查与过敏',
    description: '六类告警（十八反十九畏/中西药/药食/DDI/过敏/禁忌人群）+ 用药间隔。',
    route: '/knowledge/interactions',
    icon: 'solar:shield-warning-outline',
  },
]
</script>
