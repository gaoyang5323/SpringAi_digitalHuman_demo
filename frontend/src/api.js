const BASE = '/rag/vectors'
const ORDER_BASE = '/rag/order'

async function call(base, path, options = {}) {
  let res
  try {
    res = await fetch(base + path, options)
  } catch {
    throw new Error('无法连接后端服务，请确认 Spring Boot 已启动（localhost:8080）')
  }
  let body = null
  try {
    body = await res.json()
  } catch {
    /* 非 JSON 响应 */
  }
  if (!res.ok || !body || body.success !== true) {
    throw new Error((body && body.message) || `请求失败 (HTTP ${res.status})`)
  }
  return body.data
}

function request(path, options) {
  return call(BASE, path, options)
}

function json(method, payload) {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  }
}

export const api = {
  page(params) {
    const qs = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v !== '' && v !== null && v !== undefined) qs.set(k, v)
    })
    return request('/page?' + qs.toString())
  },

  remove(ids) {
    return request('', json('DELETE', ids))
  },

  removeLineage(lineageId) {
    return request('/lineage/' + encodeURIComponent(lineageId), { method: 'DELETE' })
  },

  orderChat(msg, sessionId) {
    return call(ORDER_BASE, '/chat', json('POST', { msg, sessionId }))
  },

  orderApproval(payload) {
    return call(ORDER_BASE, '/approval', json('POST', payload))
  },

  upload(file, { lineageId, contentColumn } = {}, onProgress) {
    return new Promise((resolve, reject) => {
      const form = new FormData()
      form.append('file', file)
      if (lineageId) form.append('lineageId', lineageId)
      if (contentColumn) form.append('contentColumn', contentColumn)
      const xhr = new XMLHttpRequest()
      xhr.open('POST', BASE + '/upload')
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable && onProgress) onProgress(Math.round((e.loaded / e.total) * 100))
      }
      xhr.onload = () => {
        let body = null
        try {
          body = JSON.parse(xhr.responseText)
        } catch {
          /* 非 JSON 响应 */
        }
        if (xhr.status >= 200 && xhr.status < 300 && body && body.success === true) {
          resolve(body.data)
        } else {
          reject(new Error((body && body.message) || `上传失败 (HTTP ${xhr.status})`))
        }
      }
      xhr.onerror = () => reject(new Error('网络错误，上传失败'))
      xhr.send(form)
    })
  }
}