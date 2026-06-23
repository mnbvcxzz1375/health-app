<template>
  <div class="flex items-start justify-between gap-3 px-4 pt-4">
    <div class="min-w-0 flex-1">
      <slot />
    </div>
    <slot name="right" />
  </div>
</template>
