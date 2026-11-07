<template>
  <div class="audio-recorder">
    <!-- WebRTC 视频展示区域 -->
    <div class="video-container">
      <video ref="videoRef" autoplay playsinline :class="{ hidden: !videoReady }"></video>
      <div v-if="!videoReady" class="video-placeholder">
        <div v-if="webrtcStatus === 'connecting'" class="connecting-tip">
          <div class="spinner small"></div>
          <span>正在连接数字人...</span>
        </div>
        <div v-else-if="webrtcStatus === 'error'" class="error-tip">
          <span>连接失败: {{ webrtcError }}</span>
          <button class="retry-btn" @click="initWebRTC">重试</button>
        </div>
        <div v-else class="waiting-tip">数字人视频待加载</div>
      </div>
    </div>

    <div class="controls">
      <button
        @click="toggleRecording"
        :class="['record-button', { recording: isRecording }]"
        :disabled="isLoading"
      >
        <div class="mic-icon">
          <svg v-if="!isRecording" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12,2A3,3 0 0,1 15,5V11A3,3 0 0,1 12,14A3,3 0 0,1 9,11V5A3,3 0 0,1 12,2M19,11C19,14.53 16.39,17.44 13,18V21H11V18C7.61,17.44 5,14.53 5,11H7A5,5 0 0,0 12,16A5,5 0 0,0 17,11H19Z" />
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="currentColor">
            <path d="M12,2A3,3 0 0,1 15,5V11A3,3 0 0,1 12,14A3,3 0 0,1 9,11V5A3,3 0 0,1 12,2M19,11C19,14.53 16.39,17.44 13,18V21H11V18C7.61,17.44 5,14.53 5,11H7A5,5 0 0,0 12,16A5,5 0 0,0 17,11H19Z" />
            <circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" stroke-width="2">
              <animate attributeName="r" from="8" to="12" dur="1s" repeatCount="indefinite" />
              <animate attributeName="opacity" from="1" to="0" dur="1s" repeatCount="indefinite" />
            </circle>
          </svg>
        </div>
        <span class="button-text">{{ buttonText }}</span>
      </button>

      <div v-if="recordingTime > 0" class="timer">{{ formatTime(recordingTime) }}</div>
    </div>

    <div v-if="error" class="error-message">{{ error }}</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

const isRecording = ref(false)
const recordingTime = ref(0)
const error = ref('')

// WebRTC 相关
const videoRef = ref(null)
const videoReady = ref(false)
const sessionId = ref('')
const webrtcStatus = ref('idle') // idle | connecting | connected | error
const webrtcError = ref('')
let peerConnection = null

let mediaRecorder = null
let audioChunks = []
let recordingTimer = null

const buttonText = computed(() => {
  if (isRecording.value) return '停止录音'
  return '开始录音'
})

const formatTime = (seconds) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

// 初始化 WebRTC 连接，获取 sessionId
const initWebRTC = async () => {
  webrtcStatus.value = 'connecting'
  webrtcError.value = ''
  videoReady.value = false

  try {
    // 创建 RTCPeerConnection
    peerConnection = new RTCPeerConnection()

    // 监听远端媒体流，绑定到 video 元素
    peerConnection.ontrack = (event) => {
      if (event.track.kind === 'video' && videoRef.value) {
        videoRef.value.srcObject = event.streams[0]
        videoReady.value = true
        webrtcStatus.value = 'connected'
      }
    }

    // 创建 offer
    const offer = await peerConnection.createOffer({
      offerToReceiveAudio: true,
      offerToReceiveVideo: true
    })
    await peerConnection.setLocalDescription(offer)

    // 调用后端 /digital-human/offer 接口
    const response = await fetch('/digital-human/offer', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sdp: offer.sdp, type: 'offer' })
    })

    if (!response.ok) {
      throw new Error(`offer 接口请求失败: ${response.status}`)
    }

    const result = await response.json()
    console.log('/digital-human/offer 响应:', JSON.stringify(result, null, 2))

    if (!result.data) {
      throw new Error(result.message || 'offer 接口返回失败')
    }

    // 解析 data：后端已结构化返回 { sdp, type, sessionId }
    const data = result.data
    sessionId.value = data.sessionId || ''
    console.log('获取到 sessionId:', sessionId.value)

    if (!data.sdp) {
      throw new Error('offer 响应中缺少 sdp 字段')
    }

    // 设置远端 SDP answer
    await peerConnection.setRemoteDescription({
      sdp: data.sdp,
      type: data.type || 'answer'
    })

    console.log('WebRTC 连接成功, sessionId:', sessionId.value)
  } catch (err) {
    console.error('WebRTC 连接失败:', err)
    webrtcStatus.value = 'error'
    webrtcError.value = err.message || '未知错误'
  }
}

onMounted(() => {
  initWebRTC()
})

const startRecording = async () => {
  try {
    error.value = ''
    
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    
    mediaRecorder = new MediaRecorder(stream, {
      mimeType: 'audio/webm;codecs=opus'
    })
    
    audioChunks = []
    
    mediaRecorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        audioChunks.push(event.data)
      }
    }
    
    mediaRecorder.onstop = () => {
      const audioBlob = new Blob(audioChunks, { type: 'audio/webm' })
      uploadAudio(audioBlob)
      stream.getTracks().forEach(track => track.stop())
    }
    
    mediaRecorder.start()
    isRecording.value = true
    recordingTime.value = 0
    
    recordingTimer = setInterval(() => {
      recordingTime.value++
    }, 1000)
    
  } catch (err) {
    error.value = '无法访问麦克风,请确保已授予权限'
    console.error('录音错误:', err)
  }
}

const stopRecording = () => {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    mediaRecorder.stop()
    isRecording.value = false
    
    if (recordingTimer) {
      clearInterval(recordingTimer)
      recordingTimer = null
    }
  }
}

const toggleRecording = () => {
  if (isRecording.value) {
    stopRecording()
  } else {
    startRecording()
  }
}

const uploadAudio = async (audioBlob) => {
  if (!sessionId.value) {
    error.value = 'sessionId 未获取，请等待 WebRTC 连接建立后重试'
    return
  }

  try {
    const formData = new FormData()
    formData.append('file', audioBlob, 'recording.webm')
    formData.append('sessionId', sessionId.value)
    
    const response = await fetch('/upload/asr', {
      method: 'POST',
      body: formData
    })
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    console.log('录音已发送成功')
  } catch (err) {
    error.value = '上传失败,请重试'
    console.error('上传错误:', err)
  }
}

const closeWebRTC = () => {
  if (recordingTimer) {
    clearInterval(recordingTimer)
    recordingTimer = null
  }
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    mediaRecorder.stop()
  }
  if (peerConnection) {
    peerConnection.close()
    peerConnection = null
  }
  sessionId.value = ''
  videoReady.value = false
  webrtcStatus.value = 'idle'
}

// 页面关闭 / 刷新时关闭 WebRTC
window.addEventListener('beforeunload', closeWebRTC)

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', closeWebRTC)
  closeWebRTC()
})
</script>

<style scoped>
.audio-recorder {
  background: white;
  border-radius: 20px;
  padding: 30px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.video-container {
  position: relative;
  width: 100%;
  max-width: 640px;
  margin: 0 auto 20px;
  border-radius: 12px;
  overflow: hidden;
  background: #1a1a2e;
  aspect-ratio: 16 / 9;
}

.video-container video {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.video-container video.hidden {
  display: none;
}

.video-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #aaa;
  gap: 12px;
}

.connecting-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: #667eea;
}

.spinner.small {
  width: 28px;
  height: 28px;
  border: 3px solid #f3f3f3;
  border-top: 3px solid #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.error-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: #f5576c;
  font-size: 14px;
}

.retry-btn {
  padding: 6px 18px;
  font-size: 13px;
  color: white;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 20px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.retry-btn:hover {
  opacity: 0.85;
}

.waiting-tip {
  color: #666;
  font-size: 14px;
}

.controls {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 15px;
}

.record-button {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 15px 40px;
  font-size: 18px;
  font-weight: 600;
  color: white;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 50px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.record-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
}

.record-button:active:not(:disabled) {
  transform: translateY(0);
}

.record-button:disabled {
  background: #ccc;
  cursor: not-allowed;
  box-shadow: none;
}

.record-button.recording {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    box-shadow: 0 4px 15px rgba(245, 87, 108, 0.4);
  }
  50% {
    box-shadow: 0 4px 25px rgba(245, 87, 108, 0.8);
  }
}

.mic-icon {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.mic-icon svg {
  width: 100%;
  height: 100%;
}

.timer {
  font-size: 20px;
  font-weight: 600;
  color: #667eea;
  font-variant-numeric: tabular-nums;
}

.error-message {
  margin-top: 20px;
  padding: 12px;
  background: #fee;
  color: #c33;
  border-radius: 8px;
  text-align: center;
  border: 1px solid #fcc;
}

</style>
