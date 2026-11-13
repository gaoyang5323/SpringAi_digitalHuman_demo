import { reactive } from 'vue'

export const toasts = reactive([])
let seq = 0

export function dismissToast(id) {
  const i = toasts.findIndex((t) => t.id === id)
  if (i >= 0) toasts.splice(i, 1)
}

export function pushToast({ type = 'info', message, action = null, duration = 4200 }) {
  const id = ++seq
  toasts.push({ id, type, message, action, duration })
  if (duration > 0) setTimeout(() => dismissToast(id), duration)
  return id
}

export const toast = {
  success: (message, opts) => pushToast({ type: 'success', message, ...opts }),
  error: (message, opts) => pushToast({ type: 'error', message, duration: 6500, ...opts }),
  info: (message, opts) => pushToast({ type: 'info', message, ...opts })
}