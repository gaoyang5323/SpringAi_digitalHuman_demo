<template>
  <section class="page">
    <div class="page-head">
      <h2 class="page-title">实时音频</h2>
      <p class="page-desc">停顿 3 秒自动断句，每段语音独立转写并追加到下方文本；单段最长 20 秒，不限总时长。</p>
    </div>

    <div class="card">
      <div class="card-head">
        <span class="card-title">实时转文字</span>
        <span :class="['badge', { 'badge-live': isActive }]">{{ statusText }}</span>
      </div>
      <div class="card-body">
        <div class="rt-controls">
          <button
            :class="['btn', 'rt-btn', isActive ? 'btn-danger' : 'btn-primary']"
            :disabled="isBusy"
            @click="toggle"
          >
            <span v-if="isBusy" class="spinner spinner-sm"></span>
            {{ buttonText }}
          </button>
          <span class="rt-timer mono">{{ formatTime(elapsed) }}</span>
          <span class="rt-hint dim">停顿 3 秒断句 · 单段最长 20 秒 · 不限总时长</span>
        </div>

        <div :class="['transcript', { live: isActive }]">
          <p v-if="!segmentsText && !isSpeaking" class="dim">
            点击「开始实时转文字」后，每段说话内容会在此依次显示…
          </p>
          <p v-else class="transcript-text">{{ segmentsText }}<span v-if="isSpeaking" class="caret"></span></p>
          <p v-if="isSpeaking" class="live-hint dim">正在收音（第 {{ segmentCount + 1 }} 段）…</p>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { toast } from '../toast'

const SILENCE_MS = 3000
const MAX_UTTERANCE_MS = 20000
const VAD_INTERVAL_MS = 80
const SPEECH_RMS = 0.02
const ONSET_FRAMES = 2

const segmentsText = ref('')
const segmentCount = ref(0)
const status = ref('idle')
const elapsed = ref(0)

let ws = null
let stream = null
let audioCtx = null
let analyser = null
let vadBuffer = null
let vadTimer = null
let sessionTimer = null
let mediaRecorder = null
let chunks = []
let utteranceTimer = null
let finalTimer = null
let lastVoiceAt = 0
let loudFrames = 0
let stopping = false
let pendingStop = false

const isActive = computed(() => status.value === 'listening' || status.value === 'speaking')
const isSpeaking = computed(() => status.value === 'speaking')
const isBusy = computed(() => ['requesting', 'connecting', 'finishing'].includes(status.value))

const buttonText = computed(() => {
  if (status.value === 'requesting') return '获取麦克风…'
  if (status.value === 'connecting') return '连接中…'
  if (status.value === 'finishing') return '收尾中…'
  return isActive.value ? '结束' : '开始实时转文字'
})

const statusText = computed(
  () =>
    ({
      idle: '未开始',
      requesting: '获取麦克风',
      connecting: '连接中',
      listening: '监听中',
      speaking: '收音中',
      finishing: '收尾中'
    })[status.value]
)

const formatTime = (seconds) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
}

const pickMimeType = () => {
  const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus']
  if (typeof MediaRecorder === 'undefined') return ''
  return candidates.find((type) => MediaRecorder.isTypeSupported(type)) || ''
}

const wsUrl = () => {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${window.location.host}/ws/asr`
}

const start = async () => {
  segmentsText.value = ''
  segmentCount.value = 0
  elapsed.value = 0
  status.value = 'requesting'

  try {
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true, channelCount: 1 }
    })
  } catch (err) {
    console.error('获取麦克风失败:', err)
    status.value = 'idle'
    toast.error('无法访问麦克风，请检查浏览器权限')
    return
  }

  if (!pickMimeType()) {
    fail('当前浏览器不支持 webm/opus 录音编码')
    return
  }

  status.value = 'connecting'
  ws = new WebSocket(wsUrl())
  ws.onopen = async () => {
    try {
      await setupVad()
    } catch (err) {
      console.error('初始化音频分析失败:', err)
      fail('初始化音频分析失败')
      return
    }
    status.value = 'listening'
    sessionTimer = setInterval(() => {
      elapsed.value++
    }, 1000)
  }
  ws.onmessage = onMessage
  ws.onerror = () => console.error('实时转写 WebSocket 异常')
  ws.onclose = () => {
    if (status.value !== 'idle') toast.error('连接已断开，实时转写中止')
    cleanup()
  }
}

const setupVad = async () => {
  const AudioCtx = window.AudioContext || window.webkitAudioContext
  audioCtx = new AudioCtx()
  if (audioCtx.state === 'suspended') await audioCtx.resume()
  const source = audioCtx.createMediaStreamSource(stream)
  analyser = audioCtx.createAnalyser()
  analyser.fftSize = 512
  analyser.smoothingTimeConstant = 0.4
  source.connect(analyser)
  vadBuffer = new Float32Array(analyser.fftSize)
  lastVoiceAt = performance.now()
  loudFrames = 0
  vadTimer = setInterval(vadTick, VAD_INTERVAL_MS)
}

const vadTick = () => {
  if (!analyser || status.value === 'finishing' || status.value === 'idle') return

  analyser.getFloatTimeDomainData(vadBuffer)
  let sum = 0
  for (let i = 0; i < vadBuffer.length; i++) sum += vadBuffer[i] * vadBuffer[i]
  const rms = Math.sqrt(sum / vadBuffer.length)
  const now = performance.now()

  if (rms >= SPEECH_RMS) {
    lastVoiceAt = now
    loudFrames++
    if (!mediaRecorder && !stopping && loudFrames >= ONSET_FRAMES) {
      startUtterance()
    }
  } else {
    loudFrames = 0
  }

  if (mediaRecorder && now - lastVoiceAt >= SILENCE_MS) {
    stopUtterance()
  }
}

const startUtterance = () => {
  if (!stream || !ws || ws.readyState !== WebSocket.OPEN || stopping) return
  const mimeType = pickMimeType()
  if (!mimeType) return

  chunks = []
  mediaRecorder = new MediaRecorder(stream, { mimeType })
  mediaRecorder.ondataavailable = (event) => {
    if (event.data && event.data.size > 0) chunks.push(event.data)
  }
  mediaRecorder.onstop = onUtteranceStop
  mediaRecorder.onerror = () => toast.error('录音失败')
  mediaRecorder.start()
  status.value = 'speaking'
  utteranceTimer = setTimeout(stopUtterance, MAX_UTTERANCE_MS)
}

const stopUtterance = () => {
  if (!mediaRecorder) return
  if (utteranceTimer) {
    clearTimeout(utteranceTimer)
    utteranceTimer = null
  }
  stopping = true
  const recorder = mediaRecorder
  mediaRecorder = null
  if (!pendingStop) status.value = 'listening'
  try {
    recorder.stop()
  } catch {
    stopping = false
  }
}

const onUtteranceStop = () => {
  const blob = new Blob(chunks, { type: 'audio/webm' })
  chunks = []
  stopping = false
  if (blob.size > 0 && ws && ws.readyState === WebSocket.OPEN) {
    ws.send(blob)
  }
  if (pendingStop) {
    pendingStop = false
    sendStop()
  }
}

const sendStop = () => {
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    cleanup()
    return
  }
  ws.send('stop')
  finalTimer = setTimeout(() => {
    toast.error('转写收尾超时，已断开连接')
    cleanup()
  }, 30000)
}

const stop = () => {
  if (!isActive.value) return
  status.value = 'finishing'
  pendingStop = true
  stopVad()
  if (mediaRecorder) {
    stopUtterance()
  } else {
    pendingStop = false
    sendStop()
  }
}

const onMessage = (event) => {
  let msg
  try {
    msg = JSON.parse(event.data)
  } catch {
    return
  }
  if (msg.type === 'segment') {
    segmentCount.value = msg.index || segmentCount.value + 1
    if (msg.text) segmentsText.value += msg.text
  } else if (msg.type === 'final') {
    if (msg.text) segmentsText.value = msg.text
    cleanup()
  } else if (msg.type === 'error') {
    toast.error(msg.message || '语音识别失败')
  }
}

const fail = (message) => {
  toast.error(message)
  cleanup()
}

const toggle = () => {
  if (isActive.value) {
    stop()
  } else if (status.value === 'idle') {
    start()
  }
}

const stopVad = () => {
  if (vadTimer) {
    clearInterval(vadTimer)
    vadTimer = null
  }
}

const cleanup = () => {
  stopVad()
  if (sessionTimer) {
    clearInterval(sessionTimer)
    sessionTimer = null
  }
  if (utteranceTimer) {
    clearTimeout(utteranceTimer)
    utteranceTimer = null
  }
  if (finalTimer) {
    clearTimeout(finalTimer)
    finalTimer = null
  }
  if (mediaRecorder) {
    const recorder = mediaRecorder
    mediaRecorder = null
    recorder.ondataavailable = null
    recorder.onstop = null
    recorder.onerror = null
    if (recorder.state !== 'inactive') {
      try {
        recorder.stop()
      } catch {
        /* ignore */
      }
    }
  }
  chunks = []
  stopping = false
  pendingStop = false
  if (stream) {
    stream.getTracks().forEach((track) => track.stop())
    stream = null
  }
  if (audioCtx) {
    audioCtx.close().catch(() => {})
    audioCtx = null
  }
  analyser = null
  vadBuffer = null
  if (ws) {
    const socket = ws
    ws = null
    socket.onopen = null
    socket.onmessage = null
    socket.onerror = null
    socket.onclose = null
    if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
      try {
        socket.close()
      } catch {
        /* ignore */
      }
    }
  }
  status.value = 'idle'
}

onBeforeUnmount(cleanup)
</script>

<style scoped>
.rt-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.rt-btn {
  padding: 10px 26px;
  font-size: 14.5px;
}

.rt-timer {
  font-size: 19px;
  font-weight: 700;
  color: var(--accent-strong);
  font-variant-numeric: tabular-nums;
}

.rt-hint {
  font-size: 12px;
}

.transcript {
  margin-top: 18px;
  min-height: 180px;
  max-height: 46vh;
  overflow-y: auto;
  padding: 18px 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-1);
  font-size: 15px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.transcript.live {
  border-color: rgba(102, 126, 234, 0.4);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.transcript-text {
  color: var(--text);
}

.live-hint {
  margin-top: 8px;
  font-size: 12.5px;
}

.caret {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: -2px;
  background: var(--accent);
  animation: blink 1s steps(1) infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.badge-live {
  color: #047857;
  background: rgba(16, 185, 129, 0.12);
  border-color: rgba(16, 185, 129, 0.35);
}
</style>
