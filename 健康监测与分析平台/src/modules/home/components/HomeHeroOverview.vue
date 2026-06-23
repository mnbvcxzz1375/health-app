<template>
  <ClinicalPageHeader title="康复总览" />
</template>

<script setup lang="ts">
import type { HomeSummary } from '@/api/modules/home'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'

defineProps<{
  summary: HomeSummary | null
  displayName: string
  today: string
}>()
</script>
