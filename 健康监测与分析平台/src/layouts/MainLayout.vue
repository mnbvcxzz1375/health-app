<template>
  <div
    class="text-slate-950"
    :class="
      showNav
        ? 'min-h-dvh bg-[linear-gradient(180deg,#dce4e1_0%,#e8efec_100%)] px-3 py-3 lg:px-5 lg:py-5'
        : 'min-h-dvh bg-[linear-gradient(180deg,#dce8ff_0%,#eef3ff_100%)]'
    "
  >
    <div :class="showNav ? 'mx-auto flex max-w-[1440px] items-start gap-5' : 'min-h-dvh'">
      <aside v-if="showNav" class="sticky top-5 hidden w-[220px] shrink-0 lg:block">
        <nav
          class="rounded-[2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-primary)] p-3 shadow-[var(--elevation-soft)]"
          aria-label="主导航"
        >
          <div class="space-y-1.5">
            <NavItem to="/home" label="总览" icon="solar:home-outline" />
            <NavItem to="/upload" label="上传" icon="solar:upload-outline" />
            <NavItem to="/medication" label="用药" icon="solar:pills-3-outline" />
            <NavItem to="/rehab" label="康复" icon="solar:wheel-outline" />
            <NavItem to="/profile" label="我的" icon="solar:user-outline" />
          </div>
        </nav>
      </aside>

      <div :class="showNav ? 'min-w-0 flex-1' : 'min-h-dvh'">
        <main
          :class="
            showNav
              ? 'rounded-[2rem] border border-[color:var(--surface-border)] bg-[color:var(--surface-primary)] shadow-[var(--elevation-strong)]'
              : 'min-h-dvh'
          "
        >
          <section :class="showNav ? 'px-4 pt-4 pb-24 lg:px-6 lg:pt-6 lg:pb-6' : 'min-h-dvh'">
            <div :class="showNav ? 'mx-auto w-full max-w-[1280px]' : 'w-full min-h-dvh'">
              <RouterView />
            </div>
          </section>
        </main>
      </div>
    </div>

    <nav
      v-if="showNav"
      class="fixed bottom-3 left-3 right-3 z-30 rounded-[1.9rem] border border-[color:var(--surface-border)] bg-white/94 p-1.5 shadow-[var(--elevation-soft)] backdrop-blur lg:hidden"
      aria-label="底部导航"
    >
      <div class="grid grid-cols-5 gap-1">
        <NavItem to="/home" label="总览" icon="solar:home-outline" />
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
