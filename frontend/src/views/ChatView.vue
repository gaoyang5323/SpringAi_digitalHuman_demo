<template>
  <section class="page">
    <div class="page-head">
      <h2 class="page-title">数字人对话</h2>
      <p class="page-desc">通过语音与数字人实时对话（WebRTC 视频 + 录音 ASR）。</p>
    </div>
    <AudioRecorder />
  </section>
</template>

<script setup>
import AudioRecorder from '../components/AudioRecorder.vue'
</script>