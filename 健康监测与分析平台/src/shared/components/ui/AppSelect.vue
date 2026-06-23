<template>
  <div ref="rootRef" class="relative" @click.stop>
    <button
      type="button"
      class="flex w-full items-center justify-between rounded-[1rem] border border-[color:var(--surface-border)] bg-white px-3 py-2.5 text-left text-sm text-slate-900 shadow-[0_6px_18px_rgba(15,23,42,0.04)] outline-none transition focus-visible:border-[color:var(--ring)] focus-visible:ring-2 focus-visible:ring-[color:var(--ring)]/20"
      :aria-label="computedAriaLabel"
      :aria-expanded="open"
      aria-haspopup="listbox"
      @click.stop="toggleOpen"
    >
      <span class="truncate">{{ selectedLabel }}</span>
      <iconify-icon
        icon="solar:alt-arrow-down-outline"
        width="16"
        height="16"
        class="shrink-0 text-slate-400 transition"
        :class="open ? 'rotate-180' : ''"
      />
    </button>

    <div
      v-if="open"
      class="absolute left-0 right-0 top-[calc(100%+0.45rem)] z-30 overflow-hidden rounded-[1.1rem] border border-[color:var(--surface-border)] bg-white p-1.5 shadow-[0_20px_40px_rgba(15,23,42,0.14)]"
      role="listbox"
    >
      <div class="max-h-56 overflow-y-auto">
        <button
          v-for="option in normalizedOptions"
          :key="option.key"
          type="button"
          class="flex w-full items-center justify-between rounded-[0.85rem] px-3 py-2.5 text-left text-sm transition hover:bg-[color:var(--surface-secondary)]"
          :class="isSelected(option.value) ? 'bg-[color:var(--surface-secondary)] text-[color:var(--accent-strong)]' : 'text-slate-900'"
          @mousedown.prevent.stop
          @click.stop="selectOption(option.value)"
        >
          <span>{{ option.label }}</span>
          <iconify-icon
            v-if="isSelected(option.value)"
            icon="solar:check-circle-bold"
            width="16"
            height="16"
            class="text-[color:var(--accent-strong)]"
          />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

type SelectOption = {
  key?: string
  label: string
  value: string | number | boolean | null
}

const props = withDefaults(
  defineProps<{
    modelValue: string | number | boolean | null
    options: SelectOption[]
    placeholder?: string
    ariaLabel?: string
  }>(),
  {
    placeholder: '请选择',
    ariaLabel: '',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string | number | boolean | null]
}>()

const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)

const normalizedOptions = computed(() =>
  props.options.map((option, index) => ({
    ...option,
    key: option.key ?? `${index}_${String(option.value)}`,
  })),
)

const selectedLabel = computed(() => {
  const option = normalizedOptions.value.find((item) => Object.is(item.value, props.modelValue))
  return option?.label ?? props.placeholder
})

const computedAriaLabel = computed(() => props.ariaLabel || props.placeholder || '下拉选择')

function isSelected(value: string | number | boolean | null) {
  return Object.is(value, props.modelValue)
}

function toggleOpen() {
  open.value = !open.value
}

function selectOption(value: string | number | boolean | null) {
  emit('update:modelValue', value)
  open.value = false
}

function handleDocumentClick(event: MouseEvent) {
  if (!open.value || !rootRef.value) return
  if (rootRef.value.contains(event.target as Node)) return
  open.value = false
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})
</script>
