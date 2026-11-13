<template>
  <section class="page">
    <div class="page-head">
      <h2 class="page-title">向量知识库</h2>
      <p class="page-desc">上传 Excel 批量向量化，分页筛选与删除 vector_store 中的向量记录。</p>
    </div>

    <!-- 上传 -->
    <div class="card">
      <div class="card-head">
        <span class="card-title">上传 Excel 向量化</span>
        <span class="badge">.xlsx / .xls</span>
      </div>
      <div class="card-body">
        <div class="upload-grid">
          <div class="field">
            <label class="field-label">选择文件</label>
            <input
              ref="fileInput"
              class="file-input"
              type="file"
              accept=".xlsx,.xls"
              @change="onFileChange"
            />
          </div>
          <div class="field">
            <label class="field-label">血缘批次 ID（可选）</label>
            <input class="input" v-model="uploadForm.lineageId" placeholder="默认自动生成 UUID" />
          </div>
          <div class="field">
            <label class="field-label">内容列（可选）</label>
            <input class="input" v-model="uploadForm.contentColumn" placeholder="默认整行拼接" />
          </div>
          <button class="btn btn-primary" :disabled="uploading || !uploadFile" @click="doUpload">
            <span v-if="uploading" class="spinner spinner-sm"></span>
            {{ uploading ? '上传中' : '上传' }}
          </button>
        </div>
        <div v-if="uploading" class="progress" style="margin-top: 14px">
          <div class="progress-bar" :style="{ width: uploadProgress + '%' }"></div>
        </div>
        <p class="upload-hint">首行为表头；上传成功后可按血缘批次追溯与整批删除。</p>
      </div>
    </div>

    <!-- 列表 -->
    <div class="card">
      <div class="card-head">
        <span class="card-title">向量记录</span>
        <div class="row-gap">
          <span class="badge">共 {{ formatNumber(total) }} 条</span>
          <button class="btn btn-ghost btn-sm" :disabled="loading" @click="load">刷新</button>
        </div>
      </div>

      <div class="card-body">
        <div class="filters">
          <div class="field">
            <label class="field-label">原文关键词</label>
            <input
              class="input"
              v-model="filters.keyword"
              placeholder="模糊搜索原文…"
              @input="debouncedSearch"
              @keyup.enter="search"
            />
          </div>
          <div class="field">
            <label class="field-label">血缘批次 ID</label>
            <input class="input" v-model="filters.lineageId" placeholder="精确匹配" @keyup.enter="search" />
          </div>
          <div class="field">
            <label class="field-label">文件名</label>
            <input class="input" v-model="filters.fileName" placeholder="精确匹配" @keyup.enter="search" />
          </div>
          <div class="row-gap">
            <button class="btn btn-primary" @click="search">查询</button>
            <button class="btn btn-ghost" @click="resetFilters">重置</button>
          </div>
        </div>

        <div class="toolbar" style="margin-top: 16px">
          <button class="btn btn-ghost btn-sm" @click="toggleSort">
            入库时间 {{ sort === 'desc' ? '↓ 最新' : '↑ 最早' }}
          </button>
          <div class="spacer"></div>
          <span v-if="selected.length" class="muted">已选 {{ selected.length }} 条</span>
          <button class="btn btn-danger btn-sm" :disabled="!selected.length" @click="deleteSelected">
            批量删除
          </button>
        </div>
      </div>

      <div v-if="loading" class="state">
        <div class="spinner"></div>
        <span>加载中…</span>
      </div>
      <div v-else-if="!rows.length" class="state">
        <div class="state-title">暂无向量记录</div>
        <span class="muted">上传 Excel 或调整筛选条件后重试。</span>
      </div>
      <div v-else class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th class="col-check">
                <input type="checkbox" :checked="allSelected" @change="toggleSelectAll" />
              </th>
              <th>内容</th>
              <th>来源文件</th>
              <th>血缘批次</th>
              <th class="col-time">入库时间</th>
              <th class="col-op">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="r in rows"
              :key="r.id"
              :class="{ 'row-selected': selected.includes(r.id) }"
            >
              <td class="col-check">
                <input type="checkbox" :checked="selected.includes(r.id)" @change="toggleSelect(r.id)" />
              </td>
              <td class="content-cell">
                <div
                  :class="['content-text', { expanded: expanded.has(r.id) }]"
                  :title="r.content"
                  @click="toggleExpand(r.id)"
                >
                  {{ r.content }}
                </div>
                <div class="cell-id" :title="r.id">ID {{ shortId(r.id, 12) }}</div>
              </td>
              <td>
                <button
                  v-if="meta(r, 'fileName')"
                  class="link-cell"
                  @click="filterByFile(meta(r, 'fileName'))"
                >
                  {{ meta(r, 'fileName') }}
                </button>
                <span v-else class="dim">—</span>
                <div class="cell-sub" v-if="meta(r, 'sheetName') || meta(r, 'rowIndex') != null">
                  {{ meta(r, 'sheetName') || '—'
                  }}<template v-if="meta(r, 'rowIndex') != null"> · 第 {{ meta(r, 'rowIndex') }} 行</template>
                </div>
              </td>
              <td>
                <button
                  v-if="meta(r, 'lineageId')"
                  class="chip"
                  :title="meta(r, 'lineageId')"
                  @click="filterByLineage(meta(r, 'lineageId'))"
                >
                  {{ shortId(meta(r, 'lineageId'), 10) }}
                </button>
                <span v-else class="dim">—</span>
                <div v-if="meta(r, 'lineageId')" style="margin-top: 6px">
                  <button class="btn btn-ghost btn-sm" @click="deleteLineage(meta(r, 'lineageId'))">
                    整批删除
                  </button>
                </div>
              </td>
              <td class="col-time">{{ formatTime(meta(r, 'createdAt')) }}</td>
              <td class="col-op">
                <button class="btn btn-danger btn-sm" @click="deleteOne(r)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="!loading && total > 0" class="pagination">
        <span class="page-info">第 {{ pageNum }} / {{ totalPages }} 页</span>
        <select class="select page-size" v-model.number="pageSize" @change="changePageSize">
          <option :value="10">10 条/页</option>
          <option :value="20">20 条/页</option>
          <option :value="50">50 条/页</option>
          <option :value="100">100 条/页</option>
        </select>
        <div class="page-nav">
          <button class="page-btn" :disabled="pageNum <= 1" @click="goPage(pageNum - 1)">上一页</button>
          <template v-for="(p, i) in pageWindow" :key="i">
            <span v-if="p === '…'" class="page-ellipsis">…</span>
            <button v-else :class="['page-btn', { active: p === pageNum }]" @click="goPage(p)">
              {{ p }}
            </button>
          </template>
          <button class="page-btn" :disabled="pageNum >= totalPages" @click="goPage(pageNum + 1)">
            下一页
          </button>
        </div>
      </div>
    </div>

    <!-- 删除确认 -->
    <transition name="modal">
      <div v-if="confirmState.open" class="modal-overlay" @click.self="closeConfirm">
        <div class="modal">
          <div class="modal-title">{{ confirmState.title }}</div>
          <div class="modal-body">{{ confirmState.message }}</div>
          <div class="modal-actions">
            <button class="btn btn-ghost" @click="closeConfirm">取消</button>
            <button class="btn btn-danger" @click="runConfirm">确认删除</button>
          </div>
        </div>
      </div>
    </transition>
  </section>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { api } from '../api'
import { toast } from '../toast'
import { formatTime, shortId, formatNumber, debounce } from '../utils'

const loading = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)

const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const sort = ref('desc')
const filters = reactive({ keyword: '', lineageId: '', fileName: '' })

const selected = ref([])
const expanded = ref(new Set())

const fileInput = ref(null)
const uploadFile = ref(null)
const uploadForm = reactive({ lineageId: '', contentColumn: '' })

const confirmState = reactive({ open: false, title: '', message: '', onConfirm: null })

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const allSelected = computed(
  () => rows.value.length > 0 && rows.value.every((r) => selected.value.includes(r.id))
)
const pageWindow = computed(() => {
  const tp = totalPages.value
  const cur = pageNum.value
  if (tp <= 7) return Array.from({ length: tp }, (_, i) => i + 1)
  const set = new Set([1, 2, tp - 1, tp, cur - 1, cur, cur + 1])
  const arr = [...set].filter((p) => p >= 1 && p <= tp).sort((a, b) => a - b)
  const out = []
  let prev = 0
  for (const p of arr) {
    if (prev && p - prev > 1) out.push('…')
    out.push(p)
    prev = p
  }
  return out
})

const meta = (r, k) => (r.metadata && r.metadata[k] != null ? r.metadata[k] : null)

async function load() {
  loading.value = true
  try {
    const data = await api.page({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      sort: sort.value,
      keyword: filters.keyword,
      lineageId: filters.lineageId,
      fileName: filters.fileName
    })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
    selected.value = []
    if (pageNum.value > totalPages.value) {
      pageNum.value = totalPages.value
      await load()
      return
    }
  } catch (e) {
    rows.value = []
    total.value = 0
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}
const debouncedSearch = debounce(search, 350)

function resetFilters() {
  filters.keyword = ''
  filters.lineageId = ''
  filters.fileName = ''
  sort.value = 'desc'
  search()
}
function goPage(p) {
  if (p < 1 || p > totalPages.value || p === pageNum.value) return
  pageNum.value = p
  load()
}
function changePageSize() {
  pageNum.value = 1
  load()
}
function toggleSort() {
  sort.value = sort.value === 'desc' ? 'asc' : 'desc'
  load()
}
function toggleSelectAll() {
  selected.value = allSelected.value ? [] : rows.value.map((r) => r.id)
}
function toggleSelect(id) {
  const i = selected.value.indexOf(id)
  if (i >= 0) selected.value.splice(i, 1)
  else selected.value.push(id)
}
function toggleExpand(id) {
  const s = new Set(expanded.value)
  s.has(id) ? s.delete(id) : s.add(id)
  expanded.value = s
}
function filterByLineage(lineageId) {
  filters.lineageId = lineageId
  search()
}
function filterByFile(fileName) {
  filters.fileName = fileName
  search()
}

function onFileChange(e) {
  uploadFile.value = e.target.files?.[0] || null
}
async function doUpload() {
  if (!uploadFile.value) {
    toast.error('请先选择 Excel 文件')
    return
  }
  uploading.value = true
  uploadProgress.value = 0
  try {
    const res = await api.upload(
      uploadFile.value,
      { lineageId: uploadForm.lineageId, contentColumn: uploadForm.contentColumn },
      (p) => (uploadProgress.value = p)
    )
    toast.success(`上传成功：插入 ${res.inserted} 条，跳过 ${res.skipped} 行`, {
      action: { label: '查看批次', onClick: () => filterByLineage(res.lineageId) }
    })
    uploadFile.value = null
    if (fileInput.value) fileInput.value.value = ''
    uploadForm.lineageId = ''
    uploadForm.contentColumn = ''
    search()
  } catch (e) {
    toast.error(e.message)
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

function askConfirm(title, message, onConfirm) {
  confirmState.title = title
  confirmState.message = message
  confirmState.onConfirm = onConfirm
  confirmState.open = true
}
function closeConfirm() {
  confirmState.open = false
  confirmState.onConfirm = null
}
async function runConfirm() {
  const fn = confirmState.onConfirm
  closeConfirm()
  if (fn) await fn()
}

function deleteOne(row) {
  askConfirm('删除向量', '确认删除该条向量？此操作不可撤销。', async () => {
    try {
      const r = await api.remove([row.id])
      toast.success(`已删除 ${r.deletedCount} 条`)
      load()
    } catch (e) {
      toast.error(e.message)
    }
  })
}
function deleteSelected() {
  if (!selected.value.length) return
  const ids = [...selected.value]
  askConfirm('批量删除', `确认删除选中的 ${ids.length} 条向量？此操作不可撤销。`, async () => {
    try {
      const r = await api.remove(ids)
      toast.success(`已删除 ${r.deletedCount} 条`)
      selected.value = []
      load()
    } catch (e) {
      toast.error(e.message)
    }
  })
}
function deleteLineage(lineageId) {
  askConfirm(
    '按血缘整批删除',
    `确认删除批次 ${shortId(lineageId, 12)} 的全部向量？此操作不可撤销。`,
    async () => {
      try {
        const r = await api.removeLineage(lineageId)
        toast.success(`已删除 ${r.deletedCount} 条`)
        if (filters.lineageId === lineageId) filters.lineageId = ''
        load()
      } catch (e) {
        toast.error(e.message)
      }
    }
  )
}

onMounted(load)
</script>