<template>
  <section class="page chat-page">
    <div class="page-head">
      <h2 class="page-title">文本对话</h2>
      <p class="page-desc">订单导购：RAG 商品检索，创建订单前需人工审批。</p>
    </div>

    <div class="chat-card">
      <div ref="logRef" class="chat-log">
        <div v-if="!messages.length" class="state">
          <div class="state-title">开始对话</div>
          <span class="muted">例如：帮我推荐一款 300 元以内的耳机</span>
        </div>

        <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
          <div class="bubble">{{ m.content }}</div>

          <div v-if="m.pending" class="approval">
            <div class="approval-title">待人工审批：{{ m.pending.toolName }}</div>
            <div class="approval-desc">{{ m.pending.description || '是否批准创建订单？' }}</div>
            <pre v-if="m.pending.arguments" class="approval-args">{{ m.pending.arguments }}</pre>
            <div class="approval-actions">
              <button class="btn btn-primary btn-sm" :disabled="busy" @click="decide(m, 'APPROVE')">
                批准
              </button>
              <button class="btn btn-ghost btn-sm" :disabled="busy" @click="decide(m, 'REJECT')">
                拒绝
              </button>
            </div>
          </div>
        </div>

        <div v-if="busy" class="msg assistant">
          <div class="bubble typing"><span class="spinner spinner-sm"></span>思考中…</div>
        </div>
      </div>

      <div class="chat-input">
        <textarea
          ref="inputRef"
          v-model="draft"
          class="input chat-textarea"
          rows="2"
          placeholder="输入消息，Enter 发送，Shift+Enter 换行"
          :disabled="busy"
          @keydown.enter.exact.prevent="send"
        ></textarea>
        <button class="btn btn-primary" :disabled="busy || !draft.trim()" @click="send">发送</button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { api } from '../api'
import { toast } from '../toast'

const sessionId = ref('')
const messages = ref([])
const draft = ref('')
const busy = ref(false)
const logRef = ref(null)
const inputRef = ref(null)

onMounted(() => {
  sessionId.value =
    typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : `sess-${Date.now()}-${Math.random().toString(16).slice(2)}`
  inputRef.value?.focus()
})

async function scrollToBottom() {
  await nextTick()
  if (logRef.value) logRef.value.scrollTop = logRef.value.scrollHeight
}

function appendResult(result) {
  const message = { role: 'assistant', content: result.answer || '' }
  if (result.status === 'WAITING_APPROVAL' && result.pending) {
    message.pending = result.pending
    if (!message.content) message.content = '已生成待审批订单，请确认后继续。'
  }
  if (!message.content) message.content = '（无回复）'
  messages.value.push(message)
}

async function send() {
  const msg = draft.value.trim()
  if (!msg || busy.value) return
  draft.value = ''
  messages.value.push({ role: 'user', content: msg })
  busy.value = true
  scrollToBottom()
  try {
    appendResult(await api.orderChat(msg, sessionId.value))
  } catch (e) {
    toast.error(e.message)
    messages.value.push({ role: 'assistant', content: `请求失败：${e.message}` })
  } finally {
    busy.value = false
    scrollToBottom()
  }
}

async function decide(message, decision) {
  if (busy.value || !message.pending) return
  const pending = message.pending
  message.pending = null
  messages.value.push({ role: 'user', content: decision === 'APPROVE' ? '批准下单' : '拒绝下单' })
  busy.value = true
  scrollToBottom()
  try {
    appendResult(
      await api.orderApproval({
        sessionId: sessionId.value,
        toolCallId: pending.toolCallId,
        decision,
        reason: decision === 'REJECT' ? '用户拒绝下单' : undefined
      })
    )
  } catch (e) {
    toast.error(e.message)
    messages.value.push({ role: 'assistant', content: `请求失败：${e.message}` })
  } finally {
    busy.value = false
    scrollToBottom()
  }
}
</script>

<style scoped>
.chat-card {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 200px);
  min-height: 420px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  overflow: hidden;
}

.chat-log {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.msg {
  display: flex;
  flex-direction: column;
  max-width: 78%;
}
.msg.user {
  align-self: flex-end;
  align-items: flex-end;
}
.msg.assistant {
  align-self: flex-start;
  align-items: flex-start;
}

.bubble {
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 13.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg.user .bubble {
  color: #fff;
  background: var(--grad);
  border-bottom-right-radius: 4px;
}
.msg.assistant .bubble {
  color: var(--text);
  background: var(--bg-3);
  border-bottom-left-radius: 4px;
}
.typing {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
}

.approval {
  margin-top: 8px;
  padding: 12px 14px;
  border: 1px solid rgba(102, 126, 234, 0.3);
  background: var(--accent-soft);
  border-radius: 12px;
  width: 100%;
}
.approval-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--accent-strong);
}
.approval-desc {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--text-muted);
}
.approval-args {
  margin-top: 8px;
  padding: 8px 10px;
  max-height: 140px;
  overflow: auto;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 8px;
  font-family: var(--mono);
  font-size: 11.5px;
  color: var(--text-muted);
  white-space: pre-wrap;
  word-break: break-all;
}
.approval-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.chat-input {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 14px 16px;
  border-top: 1px solid var(--border-soft);
  background: var(--bg);
}
.chat-textarea {
  flex: 1;
  max-height: 120px;
  resize: none;
}
</style>
