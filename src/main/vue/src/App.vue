<template>
  <div class="app-container">
    <h1>语音识别系统</h1>
    <AudioRecorder />
  </div>
</template>

<script setup>
import AudioRecorder from './components/AudioRecorder.vue'
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  min-height: 100vh;
}

.app-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 40px 20px;
}

h1 {
  color: white;
  text-align: center;
  margin-bottom: 40px;
  font-size: 2.5rem;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.2);
}
</style>
