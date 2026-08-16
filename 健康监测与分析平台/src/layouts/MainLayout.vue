<template>
  <div
    class="min-h-dvh"
    :class="showNav ? 'px-0 lg:px-5 lg:py-5' : 'min-h-dvh'"
    :style="showNav ? { background: 'var(--background)' } : { background: 'var(--background)' }"
  >
    <div :class="showNav ? 'mx-auto flex max-w-[1440px] items-start gap-5' : 'min-h-dvh'">
      <aside v-if="showNav" class="sticky top-5 hidden w-[220px] shrink-0 lg:block">
        <nav
          class="rounded-[1.2rem] border p-2 space-y-1"
          style="border-color: var(--border); background: var(--card); box-shadow: var(--shadow-sm)"
          aria-label="主导航"
        >
          <NavItem to="/home" label="总览" icon="solar:home-outline" variant="sidebar" />
          <NavItem to="/monitor/hr" label="健康监测" icon="solar:graph-up-outline" variant="sidebar" />
          <NavItem to="/upload" label="上传分析" icon="solar:upload-outline" variant="sidebar" />
          <NavItem to="/medication" label="用药管理" icon="solar:pills-3-outline" variant="sidebar" />
          <NavItem to="/rehab" label="康复训练" icon="solar:wheel-outline" variant="sidebar" />
          <NavItem to="/diet" label="饮食推荐" icon="solar:fork-knife-outline" variant="sidebar" />
          <NavItem to="/knowledge" label="健康知识" icon="solar:book-2-outline" variant="sidebar" />
          <NavItem to="/devices" label="设备管理" icon="solar:smartwatch-outline" variant="sidebar" />
          <NavItem to="/assistant" label="智能助手" icon="solar:chat-round-line-outline" variant="sidebar" />
          <NavItem to="/profile" label="我的" icon="solar:user-outline" variant="sidebar" />
        </nav>
      </aside>

      <div :class="showNav ? 'min-w-0 flex-1' : 'min-h-dvh'">
        <main :class="showNav ? 'min-h-dvh lg:min-h-[calc(100vh-2.5rem)]' : 'min-h-dvh'">
          <section :class="showNav ? 'pb-24 lg:pb-6' : 'min-h-dvh'">
            <div :class="showNav ? 'mx-auto w-full max-w-[1280px]' : 'w-full min-h-dvh'">
              <RouterView />
            </div>
          </section>
        </main>
      </div>
    </div>

    <nav
      v-if="showNav"
      class="fixed bottom-0 left-0 right-0 z-30 lg:hidden"
      style="height: 49px; backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); background: rgba(255, 255, 255, 0.82); border-top: 0.5px solid var(--border); padding-bottom: env(safe-area-inset-bottom)"
      aria-label="底部导航"
    >
      <div class="grid grid-cols-5 h-full px-2">
        <NavItem to="/home" label="首页" icon="solar:home-outline" />
        <NavItem to="/upload" label="上传" icon="solar:upload-outline" />
        <NavItem to="/medication" label="用药" icon="solar:pills-3-outline" />
        <NavItem to="/rehab" label="康复" icon="solar:wheel-outline" />
        <NavItem to="/profile" label="我的" icon="solar:user-outline" />
      </div>
    </nav>

    <AssistantFloatingEntry :visible="showAssistantEntry" />
    <ToastViewport />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { useMedicationAlarmNotifications } from '@/composables/useMedicationAlarmNotifications'
import { useRehabPlanNotifications } from '@/composables/useRehabPlanNotifications'
import AssistantFloatingEntry from '@/shared/components/assistant/AssistantFloatingEntry.vue'
import NavItem from '@/shared/components/NavItem.vue'
import ToastViewport from '@/shared/components/ToastViewport.vue'

const route = useRoute()
useMedicationAlarmNotifications()
useRehabPlanNotifications()
const showNav = computed(() => !route.meta?.hideNav)
const showAssistantEntry = computed(() => showNav.value && route.name !== 'assistant')
</script>
