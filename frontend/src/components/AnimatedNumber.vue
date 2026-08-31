<template>
  <span class="num">{{ display.toLocaleString() }}</span>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    value: number
    duration?: number
  }>(),
  { duration: 600 },
)

const display = ref(0)
let raf: number | undefined

function animate(to: number) {
  if (raf !== undefined) cancelAnimationFrame(raf)
  if (to === display.value) return
  const from = display.value
  const start = performance.now()
  const step = (t: number) => {
    const p = Math.min(1, (t - start) / props.duration)
    const e = 1 - Math.pow(1 - p, 3)
    display.value = Math.round(from + (to - from) * e)
    if (p < 1) raf = requestAnimationFrame(step)
  }
  raf = requestAnimationFrame(step)
}

watch(
  () => props.value,
  (v) => animate(v),
  { immediate: true },
)

onBeforeUnmount(() => {
  if (raf !== undefined) cancelAnimationFrame(raf)
})
</script>
