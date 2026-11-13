import { reactive } from 'vue'

const VALID = ['/chat', '/text-chat', '/knowledge']

export const router = reactive({ path: '/chat' })

function sync() {
  const hash = window.location.hash.replace(/^#/, '')
  router.path = VALID.includes(hash) ? hash : '/chat'
}

window.addEventListener('hashchange', sync)
sync()

export function navigate(path) {
  if (window.location.hash !== '#' + path) {
    window.location.hash = path
  } else {
    sync()
  }
}