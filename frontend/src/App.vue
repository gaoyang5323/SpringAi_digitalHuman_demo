<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <span class="brand-dot"></span>
        <div>
          <div class="brand-title">数字人控制台</div>
          <div class="brand-sub">Digital Human Console</div>
        </div>
      </div>

      <nav class="sidebar-nav">
        <button
          :class="['sidebar-item', { active: router.path === '/chat' }]"
          @click="navigate('/chat')"
        >
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M12,2A3,3 0 0,1 15,5V11A3,3 0 0,1 12,14A3,3 0 0,1 9,11V5A3,3 0 0,1 12,2M19,11C19,14.53 16.39,17.44 13,18V21H11V18C7.61,17.44 5,14.53 5,11H7A5,5 0 0,0 12,16A5,5 0 0,0 17,11H19Z" />
          </svg>
          <span>语音对话</span>
        </button>
        <button
          :class="['sidebar-item', { active: router.path === '/text-chat' }]"
          @click="navigate('/text-chat')"
        >
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M20,2H4A2,2 0 0,0 2,4V22L6,18H20A2,2 0 0,0 22,16V4A2,2 0 0,0 20,2Z" />
          </svg>
          <span>文本对话</span>
        </button>
        <button
          :class="['sidebar-item', { active: router.path === '/knowledge' }]"
          @click="navigate('/knowledge')"
        >
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M12,3C7.58,3 4,4.79 4,7C4,9.21 7.58,11 12,11C16.42,11 20,9.21 20,7C20,4.79 16.42,3 12,3M4,9V12C4,14.21 7.58,16 12,16C16.42,16 20,14.21 20,12V9C20,11.21 16.42,13 12,13C7.58,13 4,11.21 4,9M4,14V17C4,19.21 7.58,21 12,21C16.42,21 20,19.21 20,17V14C20,16.21 16.42,18 12,18C7.58,18 4,16.21 4,14Z" />
          </svg>
          <span>知识库</span>
        </button>
      </nav>

      <div class="sidebar-foot">v1.0.0</div>
    </aside>

    <main class="main">
      <component :is="currentView" />
    </main>

    <div class="toast-host">
      <transition-group name="toast">
        <div v-for="t in toasts" :key="t.id" :class="['toast', 'toast-' + t.type]">
          <span class="toast-msg">{{ t.message }}</span>
          <button v-if="t.action" class="toast-action" @click="onAction(t)">
            {{ t.action.label }}
          </button>
          <button class="toast-close" @click="dismissToast(t.id)" aria-label="关闭">×</button>
        </div>
      </transition-group>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { router, navigate } from './router'
import { toasts, dismissToast } from './toast'
import ChatView from './views/ChatView.vue'
import TextChatView from './views/TextChatView.vue'
import KnowledgeView from './views/KnowledgeView.vue'

const views = {
  '/chat': ChatView,
  '/text-chat': TextChatView,
  '/knowledge': KnowledgeView
}

const currentView = computed(() => views[router.path] ?? ChatView)

function onAction(t) {
  try {
    t.action.onClick?.()
  } finally {
    dismissToast(t.id)
  }
}
</script>
