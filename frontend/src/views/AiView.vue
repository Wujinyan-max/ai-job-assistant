<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">AI 求职助手</h2>
        <div class="page-subtitle">
          当前模式：
          <el-tag size="small" :type="aiMode.mock ? 'warning' : 'success'" effect="light">
            {{ aiMode.mock ? '本地模拟引擎（未配置 AI_API_KEY）' : `大模型 ${aiMode.model}` }}
          </el-tag>
        </div>
      </div>
    </div>

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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import { aiApi, jobApi, resumeApi } from '@/api'
import { scoreColor } from '@/utils/theme'

const route = useRoute()

const tab = ref('jd')
const jobs = ref([])
const resumes = ref([])
const aiMode = reactive({ mock: true, model: 'mock-local' })
const categoryOptions = ['Java基础', 'Spring Boot', 'MySQL', 'Redis', '项目', 'HR']

const jd = reactive({ jobId: null, text: '', loading: false, result: null })
const match = reactive({ jobId: null, resumeId: null, loading: false, result: null })
const quiz = reactive({
  jobId: null, resumeId: null, categories: [], difficulty: 'MEDIUM',
  countPerCategory: 3, save: true, loading: false, result: [], savedCount: 0
})

const difficultyLabel = (value) => ({ EASY: '简单', MEDIUM: '中等', HARD: '困难' }[value] || value)

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
  if (aiConfig) {
    aiMode.mock = aiConfig.mock
    aiMode.model = aiConfig.model
  }

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

onMounted(loadBaseData)
</script>

<style scoped>
.ai-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}

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
</style>
