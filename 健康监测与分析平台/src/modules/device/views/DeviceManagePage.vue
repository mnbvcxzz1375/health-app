<template>
  <div class="flex min-h-[60vh] items-center justify-center">
    <iconify-icon icon="solar:refresh-outline" width="24" height="24" class="animate-spin" style="color: var(--muted-foreground);" />
  </div>
</template>

<script setup lang="ts">
/**
 * @deprecated 已迁移到 DeviceHubPage（/devices）。
 * 此页面仅作重定向兼容，保留旧路由 /devices/legacy 可访问。
 */
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

onMounted(() => {
  router.replace('/devices')
})
</script>
