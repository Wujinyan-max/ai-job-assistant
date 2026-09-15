<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">AI 求职助手</h2>
        <div class="page-subtitle">
          当前模式：
          <el-tag size="small" :type="aiMode.mock ? 'warning' : 'success'" effect="light">
            {{ aiMode.mock ? '本地模拟引擎（请配置 API Key）' : `${providerLabel(aiMode.provider)} · ${aiMode.model} · ${modeLabel(aiMode.apiMode)}` }}
          </el-tag>
        </div>
      </div>
      <el-button :icon="Setting" @click="openConfig">配置模型</el-button>
    </div>

    <div class="ai-workspace">
      <el-tabs v-model="tab" class="ai-tabs">
      <!-- ------------------------------ JD 解析 ------------------------------ -->
      <el-tab-pane label="JD 解析" name="jd">
        <div class="card">
          <div class="toolbar">
            <el-select v-model="jd.jobId" placeholder="选择已保存的职位（可选）" clearable filterable style="width: 320px">
              <el-option v-for="item in jobs" :key="item.id" :label="`${item.jobName}（${item.companyName || '未关联公司'}）`" :value="item.id" />
            </el-select>
            <el-button type="primary" :loading="jd.loading" :icon="MagicStick" @click="runAnalyzeJd">
              开始解析
            </el-button>
          </div>
          <el-input v-model="jd.text" type="textarea" :rows="8"
                    placeholder="也可以直接把 JD 粘贴到这里，例如：要求熟悉 Java、Spring Boot、MySQL、Redis，熟悉分布式系统……" />

          <template v-if="jd.result">
            <el-descriptions :column="3" border size="small" style="margin: 18px 0">
              <el-descriptions-item label="经验要求">{{ jd.result.experience }}</el-descriptions-item>
              <el-descriptions-item label="学历要求">{{ jd.result.education }}</el-descriptions-item>
              <el-descriptions-item label="职级判断">{{ jd.result.seniority }}</el-descriptions-item>
            </el-descriptions>
            <div class="section-title">技术要求</div>
            <el-tag v-for="skill in jd.result.skills" :key="skill" class="tag" type="primary" effect="light">{{ skill }}</el-tag>
            <div class="section-title">关键词 / 加分项</div>
            <el-tag v-for="word in jd.result.keywords" :key="word" class="tag" type="warning" effect="light">{{ word }}</el-tag>
            <div class="section-title">主要职责</div>
            <ul class="duty-list">
              <li v-for="(item, index) in jd.result.responsibilities" :key="index">{{ item }}</li>
            </ul>
            <div class="section-title">AI 总结</div>
            <el-alert :title="jd.result.summary" type="info" :closable="false" show-icon />
          </template>
        </div>
      </el-tab-pane>

      <!-- ----------------------------- 简历匹配 ----------------------------- -->
      <el-tab-pane label="简历匹配" name="match">
        <div class="card">
          <div class="toolbar">
            <el-select v-model="match.jobId" placeholder="选择职位" clearable filterable style="width: 280px">
              <el-option v-for="item in jobs" :key="item.id" :label="`${item.jobName}（${item.companyName || '未关联公司'}）`" :value="item.id" />
            </el-select>
            <el-select v-model="match.resumeId" placeholder="选择简历" clearable style="width: 240px">
              <el-option v-for="item in resumes" :key="item.id" :label="item.title" :value="item.id" />
            </el-select>
            <el-button type="primary" :loading="match.loading" :icon="MagicStick" @click="runMatch">分析匹配度</el-button>
          </div>

          <template v-if="match.result">
            <el-row :gutter="20">
              <el-col :span="7" style="text-align: center">
                <el-progress type="dashboard" :percentage="match.result.score" :width="150"
                             :color="scoreColor(match.result.score)">
                  <template #default>
                    <div style="font-size: 26px; font-weight: 600">{{ match.result.score }}</div>
                    <div class="muted" style="font-size: 12px">匹配度</div>
                  </template>
                </el-progress>
              </el-col>
              <el-col :span="17">
                <el-alert :title="match.result.comment" type="info" :closable="false" show-icon />
                <div class="section-title">简历亮点</div>
                <ul class="duty-list">
                  <li v-for="(item, index) in match.result.strengths" :key="index">{{ item }}</li>
                </ul>
              </el-col>
            </el-row>

            <el-row :gutter="20" style="margin-top: 12px">
              <el-col :span="12">
                <div class="section-title">已匹配技能（{{ match.result.matchedSkills.length }}）</div>
                <el-tag v-for="skill in match.result.matchedSkills" :key="skill" class="tag" type="success" effect="light">{{ skill }}</el-tag>
                <div v-if="!match.result.matchedSkills.length" class="muted">暂无</div>
              </el-col>
              <el-col :span="12">
                <div class="section-title">缺失技能（{{ match.result.missingSkills.length }}）</div>
                <el-tag v-for="skill in match.result.missingSkills" :key="skill" class="tag" type="danger" effect="light">{{ skill }}</el-tag>
                <div v-if="!match.result.missingSkills.length" class="muted">没有明显缺失，可以放心投</div>
              </el-col>
            </el-row>

            <div class="section-title">优化建议</div>
            <el-timeline>
              <el-timeline-item v-for="(item, index) in match.result.suggestions" :key="index" type="primary">
                {{ item }}
              </el-timeline-item>
            </el-timeline>
          </template>
        </div>
      </el-tab-pane>

      <!-- ----------------------------- 面试题生成 ---------------------------- -->
      <el-tab-pane label="AI 出题" name="question">
        <div class="card">
          <div class="toolbar">
            <el-select v-model="quiz.jobId" placeholder="选择职位" clearable filterable style="width: 260px">
              <el-option v-for="item in jobs" :key="item.id" :label="`${item.jobName}（${item.companyName || '未关联公司'}）`" :value="item.id" />
            </el-select>
            <el-select v-model="quiz.resumeId" placeholder="选择简历" clearable style="width: 220px">
              <el-option v-for="item in resumes" :key="item.id" :label="item.title" :value="item.id" />
            </el-select>
            <el-select v-model="quiz.categories" multiple collapse-tags placeholder="出题范围（可多选）"
                       style="width: 280px" clearable>
              <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="quiz.difficulty" style="width: 120px">
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
              <el-option label="困难" value="HARD" />
            </el-select>
            <el-input-number v-model="quiz.countPerCategory" :min="1" :max="5" controls-position="right" style="width: 120px" />
            <el-checkbox v-model="quiz.save">保存到题库</el-checkbox>
            <el-button type="primary" :loading="quiz.loading" :icon="MagicStick" @click="runGenerate">生成面试题</el-button>
          </div>

          <el-alert v-if="quiz.savedCount" type="success" :closable="false" show-icon style="margin-bottom: 12px"
                    :title="`本次生成 ${quiz.result.length} 道题，已保存 ${quiz.savedCount} 道到题库`" />

          <el-collapse v-if="quiz.result.length">
            <el-collapse-item v-for="(item, index) in quiz.result" :key="index"
                              :title="`${index + 1}. [${item.category}] ${item.question}`">
              <div class="pre-wrap">{{ item.answer }}</div>
              <el-tag size="small" style="margin-top: 8px" effect="plain">{{ difficultyLabel(item.difficulty) }}</el-tag>
            </el-collapse-item>
          </el-collapse>
          <el-empty v-else description="选择职位和简历后点击「生成面试题」" :image-size="90" />
        </div>
      </el-tab-pane>
      </el-tabs>

      <aside class="ai-guide">
        <div class="guide-mark"><el-icon><MagicStick /></el-icon></div>
        <div class="guide-eyebrow">使用示例</div>
        <h3>让 AI 成为你的求职副驾</h3>
        <ul>
          <li><span>01</span>解析职位 JD，提取关键能力</li>
          <li><span>02</span>分析简历与岗位的匹配度</li>
          <li><span>03</span>生成个性化面试练习题</li>
          <li><span>04</span>把结果沉淀到题库持续复习</li>
        </ul>
        <div class="guide-tip">先选择已保存的职位，系统会自动带入对应 JD。</div>
      </aside>
    </div>

    <el-drawer v-model="configDrawer" title="模型服务配置" size="480px" class="config-drawer">
      <div class="config-intro">
        <div class="config-intro-icon"><el-icon><Setting /></el-icon></div>
        <div><strong>使用你自己的模型服务</strong><p>配置仅对当前账号生效，API Key 加密保存且不会再次明文展示。</p></div>
      </div>

      <el-form label-position="top" class="config-form">
        <el-form-item label="模型厂商">
          <el-select v-model="configForm.provider" style="width: 100%" @change="applyProviderPreset">
            <el-option v-for="item in providers" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="API 模式">
          <el-radio-group v-model="configForm.apiMode" class="mode-picker">
            <el-radio-button value="CHAT_COMPLETIONS">Chat Completions</el-radio-button>
            <el-radio-button value="RESPONSES">Responses</el-radio-button>
          </el-radio-group>
          <div class="field-hint">
            请求端点：{{ configForm.apiMode === 'RESPONSES' ? '/responses' : '/chat/completions' }}
          </div>
        </el-form-item>

        <el-form-item label="Base URL">
          <el-input v-model="configForm.baseUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="configForm.model" placeholder="例如 gpt-4.1-mini、deepseek-chat" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="configForm.apiKey" type="password" show-password
                    :placeholder="aiMode.hasApiKey ? `已保存 ${aiMode.apiKeyMasked}，留空则保持不变` : '请输入 API Key'" />
          <div class="field-hint">密钥只会发送到你的后端，不会出现在配置查询结果或日志中。</div>
        </el-form-item>
        <el-form-item label="启用该配置">
          <el-switch v-model="configForm.enabled" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="config-actions">
          <el-button :loading="testingConfig" @click="testConnection">测试连接</el-button>
          <el-button type="primary" :loading="savingConfig" @click="saveConfig(true)">保存配置</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onActivated, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MagicStick, Setting } from '@element-plus/icons-vue'
import { aiApi, jobApi, resumeApi } from '@/api'
import { scoreColor } from '@/utils/theme'

const route = useRoute()

const tab = ref('jd')
const jobs = ref([])
const resumes = ref([])
const aiMode = reactive({ mock: true, model: 'mock-local', provider: 'OPENAI', apiMode: 'CHAT_COMPLETIONS', hasApiKey: false, apiKeyMasked: '' })
const configDrawer = ref(false)
const savingConfig = ref(false)
const testingConfig = ref(false)
const providers = [
  { label: 'OpenAI', value: 'OPENAI', baseUrl: 'https://api.openai.com/v1', model: 'gpt-4.1-mini' },
  { label: 'DeepSeek', value: 'DEEPSEEK', baseUrl: 'https://api.deepseek.com/v1', model: 'deepseek-chat' },
  { label: '通义千问', value: 'QWEN', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', model: 'qwen-plus' },
  { label: 'Moonshot', value: 'MOONSHOT', baseUrl: 'https://api.moonshot.cn/v1', model: 'moonshot-v1-8k' },
  { label: 'Ollama', value: 'OLLAMA', baseUrl: 'http://localhost:11434/v1', model: 'qwen2.5' },
  { label: '其他兼容厂商', value: 'CUSTOM', baseUrl: '', model: '' }
]
const configForm = reactive({ provider: 'OPENAI', apiMode: 'CHAT_COMPLETIONS', baseUrl: 'https://api.openai.com/v1', model: 'gpt-4.1-mini', apiKey: '', enabled: true })
const categoryOptions = ['Java基础', 'Spring Boot', 'MySQL', 'Redis', '项目', 'HR']

const jd = reactive({ jobId: null, text: '', loading: false, result: null })
const match = reactive({ jobId: null, resumeId: null, loading: false, result: null })
const quiz = reactive({
  jobId: null, resumeId: null, categories: [], difficulty: 'MEDIUM',
  countPerCategory: 3, save: true, loading: false, result: [], savedCount: 0
})

const difficultyLabel = (value) => ({ EASY: '简单', MEDIUM: '中等', HARD: '困难' }[value] || value)
const providerLabel = (value) => providers.find((item) => item.value === value)?.label || value
const modeLabel = (value) => value === 'RESPONSES' ? 'Responses' : 'Chat'

function applyProviderPreset(value) {
  const preset = providers.find((item) => item.value === value)
  if (preset && value !== 'CUSTOM') {
    configForm.baseUrl = preset.baseUrl
    configForm.model = preset.model
  }
}

function fillConfig(value) {
  if (!value) return
  Object.assign(aiMode, value)
  Object.assign(configForm, {
    provider: value.provider || 'OPENAI',
    apiMode: value.apiMode || 'CHAT_COMPLETIONS',
    baseUrl: value.baseUrl || 'https://api.openai.com/v1',
    model: value.model === 'mock-local' ? 'gpt-4.1-mini' : value.model,
    apiKey: '',
    enabled: value.enabled !== false
  })
}

function openConfig() {
  configDrawer.value = true
}

async function saveConfig(closeDrawer = false) {
  if (!configForm.baseUrl.trim() || !configForm.model.trim()) {
    ElMessage.warning('请填写 Base URL 和模型名称')
    return false
  }
  savingConfig.value = true
  try {
    const value = await aiApi.saveConfig({ ...configForm })
    fillConfig(value)
    ElMessage.success('模型配置已安全保存')
    if (closeDrawer) configDrawer.value = false
    return true
  } finally {
    savingConfig.value = false
  }
}

async function testConnection() {
  testingConfig.value = true
  try {
    const saved = await saveConfig(false)
    if (!saved) return
    const message = await aiApi.testConfig()
    ElMessage.success(message)
  } finally {
    testingConfig.value = false
  }
}

async function runAnalyzeJd() {
  if (!jd.jobId && !jd.text.trim()) {
    ElMessage.warning('请选择职位或粘贴 JD 文本')
    return
  }
  jd.loading = true
  try {
    jd.result = await aiApi.analyzeJd({ jobId: jd.jobId, jobDescription: jd.text })
    ElMessage.success('解析完成')
  } finally {
    jd.loading = false
  }
}

async function runMatch() {
  if (!match.jobId || !match.resumeId) {
    ElMessage.warning('请同时选择职位和简历')
    return
  }
  match.loading = true
  try {
    match.result = await aiApi.matchResume({ jobId: match.jobId, resumeId: match.resumeId })
    ElMessage.success('分析完成')
  } finally {
    match.loading = false
  }
}

async function runGenerate() {
  if (!quiz.jobId && !quiz.resumeId) {
    ElMessage.warning('至少选择一个职位或一份简历')
    return
  }
  quiz.loading = true
  try {
    const data = await aiApi.generateQuestions({
      jobId: quiz.jobId,
      resumeId: quiz.resumeId,
      categories: quiz.categories,
      difficulty: quiz.difficulty,
      countPerCategory: quiz.countPerCategory,
      save: quiz.save
    })
    quiz.result = data.questions || []
    quiz.savedCount = data.savedCount || 0
    ElMessage.success(`生成 ${quiz.result.length} 道题`)
  } finally {
    quiz.loading = false
  }
}

async function loadBaseData() {
  const [jobPage, resumeList, aiConfig] = await Promise.all([
    jobApi.page({ pageNum: 1, pageSize: 100 }),
    resumeApi.all(),
    aiApi.config().catch(() => null)
  ])
  jobs.value = jobPage.records
  resumes.value = resumeList
  fillConfig(aiConfig)

  const defaultResume = resumes.value.find((item) => item.isDefault === 1) || resumes.value[0]
  if (defaultResume) {
    match.resumeId = defaultResume.id
    quiz.resumeId = defaultResume.id
  }
  if (route.query.resumeId) {
    match.resumeId = Number(route.query.resumeId)
    quiz.resumeId = Number(route.query.resumeId)
  }
  if (jobs.value.length) {
    match.jobId = jobs.value[0].id
    quiz.jobId = jobs.value[0].id
    jd.jobId = jobs.value[0].id
  }
}

// keep-alive 缓存后再次进入不会触发 onMounted：这里只刷新职位/简历下拉数据，
// 不重跑 loadBaseData，避免把用户已选中的职位或简历重置掉
async function refreshOptions() {
  const [jobPage, resumeList] = await Promise.all([
    jobApi.page({ pageNum: 1, pageSize: 100 }),
    resumeApi.all()
  ])
  jobs.value = jobPage.records
  resumes.value = resumeList
}

onMounted(loadBaseData)
onActivated(refreshOptions)
</script>

<style scoped>
.ai-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 268px;
  gap: 14px;
  align-items: start;
}

.config-intro { display: flex; gap: 12px; margin-bottom: 22px; padding: 14px; border-radius: 10px; background: var(--brand-softer); }
.config-intro-icon { display: grid; place-items: center; width: 34px; height: 34px; flex: none; border-radius: 9px; background: var(--brand); color: #fff; }
.config-intro strong { font-size: 14px; }
.config-intro p { margin: 4px 0 0; color: var(--text-secondary); font-size: 12px; line-height: 1.55; }
.config-form :deep(.el-form-item) { margin-bottom: 20px; }
.mode-picker { width: 100%; }
.mode-picker :deep(.el-radio-button) { flex: 1; }
.mode-picker :deep(.el-radio-button__inner) { width: 100%; }
.field-hint { margin-top: 7px; color: var(--text-secondary); font-size: 11px; line-height: 1.5; }
.config-actions { display: flex; justify-content: flex-end; gap: 8px; }

.ai-tabs { min-width: 0; }

.ai-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}

.ai-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; background: var(--border); }
.ai-tabs :deep(.toolbar) { padding: 0; border: 0; box-shadow: none; background: none; }

.ai-guide {
  position: sticky;
  top: 18px;
  overflow: hidden;
  padding: 22px 20px;
  border: 1px solid #dfe8ff;
  border-radius: var(--radius);
  background:
    radial-gradient(circle at 100% 0, rgba(59,114,245,.14), transparent 42%),
    linear-gradient(145deg, #f8faff, #eef3ff);
  color: var(--text-primary);
}

.guide-mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  margin-bottom: 30px;
  border-radius: 10px;
  background: var(--brand);
  color: white;
  box-shadow: 0 8px 18px rgba(59,114,245,.24);
}

.guide-eyebrow { color: var(--brand); font-size: 11px; font-weight: 700; letter-spacing: 1px; }
.ai-guide h3 { margin: 7px 0 18px; font-size: 17px; line-height: 1.45; }
.ai-guide ul { display: grid; gap: 14px; margin: 0; padding: 0; list-style: none; }
.ai-guide li { display: flex; gap: 9px; color: var(--text-regular); font-size: 12px; line-height: 1.55; }
.ai-guide li span { color: var(--brand); font-size: 10px; font-weight: 700; }
.guide-tip { margin-top: 22px; padding-top: 14px; border-top: 1px solid #dce6fb; color: var(--text-secondary); font-size: 11px; line-height: 1.6; }

.section-title {
  font-weight: 600;
  margin: 18px 0 10px;
  font-size: 14px;
}

.tag {
  margin: 0 8px 8px 0;
}

.duty-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.9;
  color: #4b5568;
  font-size: 13px;
}

@media (max-width: 1100px) {
  .ai-workspace { grid-template-columns: 1fr; }
  .ai-guide { position: static; }
}
</style>
