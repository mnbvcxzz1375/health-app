<template>
  <div class="flex min-h-full items-center justify-center py-10">
    <div class="w-full max-w-[720px]">
      <ClinicalPageHeader
        eyebrow="404"
        title="页面不存在"
        description="你访问的地址可能已变更、无权限访问，或当前 Beta 版尚未开放这个页面。"
        meta="Unavailable"
        meta-label="状态"
      >
        <Button @click="goHome">返回首页</Button>
      </ClinicalPageHeader>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import ClinicalPageHeader from '@/shared/components/clinical/ClinicalPageHeader.vue'
import Button from '@/shared/components/ui/Button.vue'

const router = useRouter()
const goHome = () => router.push('/home')
</script>
