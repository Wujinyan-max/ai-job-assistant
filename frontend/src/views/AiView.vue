<template>
  <div class="page">
    <div class="page-header">
      <div>
        <div class="page-kicker">AI Assistant</div>
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

    <el-alert v-if="runningNotice" class="ai-running" type="warning" :closable="false" show-icon>
      <template #title>
        <span class="ai-running-title">{{ runningNotice.title }}</span>
        <el-button link type="primary" class="ai-running-jump" @click="tab = runningNotice.focusTab">
          去「{{ runningNotice.focusLabel }}」看进度
        </el-button>
      </template>
      <div class="ai-running-detail">{{ runningNotice.detail }}</div>
    </el-alert>

    <div class="ai-workspace">
      <el-tabs v-model="tab" class="ai-tabs">
      <!-- ------------------------------ JD 解析 ------------------------------ -->
      <el-tab-pane label="JD 解析" name="jd">
        <div class="card">
          <div class="toolbar">
            <el-select v-model="jd.jobId" placeholder="选择已保存的职位（可选）" clearable filterable style="width: 320px" @change="loadJdHistory">
              <el-option v-for="item in jobs" :key="item.id" :label="`${item.jobName}（${item.companyName || '未关联公司'}）`" :value="item.id" />
            </el-select>
            <el-button type="primary" :loading="jd.loading" :icon="MagicStick" @click="runAnalyzeJd">
              开始解析
            </el-button>
          </div>
          <el-input v-model="jd.text" type="textarea" :rows="8"
                    placeholder="也可以直接把 JD 粘贴到这里，例如：要求熟悉 Java、Spring Boot、MySQL、Redis，熟悉分布式系统……" />

          <template v-if="jd.result">
            <el-alert v-if="jd.fromHistory" type="warning" :closable="false" show-icon style="margin-bottom: 12px">
              <template #title>正在展示上次解析的历史结果{{ jd.historyTime ? '（' + formatTime(jd.historyTime) + '）' : '' }}，未消耗 token；点击「开始解析」可重新分析</template>
            </el-alert>
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
            <AiUsageBar :usage="jd.usage" :mocked="jd.mocked" />
          </template>
        </div>
      </el-tab-pane>

      <!-- ----------------------------- 简历匹配 ----------------------------- -->
      <el-tab-pane label="简历匹配" name="match">
        <div class="card">
          <div class="toolbar">
            <el-select v-model="match.jobId" placeholder="选择职位" clearable filterable style="width: 280px" @change="loadMatchHistory">
              <el-option v-for="item in jobs" :key="item.id" :label="`${item.jobName}（${item.companyName || '未关联公司'}）`" :value="item.id" />
            </el-select>
            <el-select v-model="match.resumeId" placeholder="选择简历" clearable style="width: 240px" @change="loadMatchHistory">
              <el-option v-for="item in resumes" :key="item.id" :label="item.title" :value="item.id" />
            </el-select>
            <el-button type="primary" :loading="match.loading" :icon="MagicStick" @click="runMatch">分析匹配度</el-button>
          </div>

          <template v-if="match.result">
            <el-alert v-if="match.fromHistory" type="warning" :closable="false" show-icon style="margin-bottom: 12px">
              <template #title>正在展示上次匹配的历史结果{{ match.historyTime ? '（' + formatTime(match.historyTime) + '）' : '' }}，未消耗 token；点击「分析匹配度」可重新匹配</template>
            </el-alert>
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
            <AiUsageBar :usage="match.usage" :mocked="match.mocked" />
          </template>
        </div>
      </el-tab-pane>

      <!-- ----------------------------- 简历优化 ----------------------------- -->
      <el-tab-pane label="简历优化" name="optimize">
        <div class="card">
          <div class="toolbar opt-toolbar">
            <div class="field">
              <span class="field-caption">目标岗位</span>
              <el-select v-model="optimize.jobId" placeholder="选择职位（自动带入 JD）" clearable filterable style="width: 230px">
                <el-option v-for="item in jobs" :key="item.id" :label="`${item.jobName}（${item.companyName || '未关联公司'}）`" :value="item.id" />
              </el-select>
            </div>
            <div class="field">
              <span class="field-caption">带入已有简历</span>
              <el-select v-model="optimize.resumeId" placeholder="可选，自动填入正文" clearable style="width: 190px" @change="applyOptimizeResume">
                <el-option v-for="item in resumes" :key="item.id" :label="item.title" :value="item.id" />
              </el-select>
            </div>
            <div class="field">
              <span class="field-caption">优化重点</span>
              <el-input v-model="optimize.focus" placeholder="可选，如：突出高并发与架构能力" style="width: 220px" />
            </div>
            <el-button type="primary" :loading="optimize.loading" :icon="MagicStick" @click="runOptimize">
              开始优化
            </el-button>
          </div>

          <div class="opt-grid">
            <div class="opt-field">
              <span class="field-caption">岗位 JD {{ optimize.jobId ? '（已选职位，可留空）' : '（没保存职位就粘贴到这里）' }}</span>
              <el-input v-model="optimize.jdText" type="textarea" :rows="8"
                        placeholder="粘贴职位描述，例如：负责交易系统后端开发，要求熟悉 Java、Spring Boot、MySQL、Redis，有高并发项目经验" />
            </div>
            <div class="opt-field">
              <span class="field-caption">原始素材（项目经历 / 工作内容 / 技能）</span>
              <el-input v-model="optimize.raw" type="textarea" :rows="8"
                        placeholder="把零散的素材直接贴进来，AI 会按目标岗位逐条改写：&#10;项目经历&#10;1. 负责订单系统的重构，引入 Redis 缓存&#10;2. 参与支付链路的稳定性治理&#10;技能&#10;Java、Spring Boot、MySQL" />
            </div>
          </div>

          <!-- 缺口补全：与简历匹配联动 -->
          <div class="gap-section">
            <div class="section-title with-action">
              <span>缺口补全（与简历匹配联动）</span>
              <el-button size="small" :loading="optimize.gapLoading" @click="importMatchGaps">
                从匹配结果导入缺口
              </el-button>
            </div>
            <div v-if="optimize.gaps.length" class="gap-list">
              <div v-for="skill in optimize.gaps" :key="skill" class="gap-item">
                <div class="gap-skill">
                  <el-tag type="danger" effect="light">{{ skill }}</el-tag>
                </div>
                <el-input
                  v-model="optimize.supplements[skill]"
                  type="textarea"
                  :rows="2"
                  :placeholder="'可选：补充一段与「' + skill + '」相关的真实项目经历，留空则由 AI 生成'"
                />
              </div>
            </div>
            <div v-else class="muted">点击上方按钮，从「简历匹配」结果中导入缺失技能，逐项补充真实项目经历；没填的缺口由 AI 补全。</div>
            <el-checkbox v-model="optimize.allowFabrication" class="gap-fabrication">
              允许 AI 为未补充的缺口生成贴合 JD 的项目经历（会在结果里标记为「AI 补全」）
            </el-checkbox>
          </div>

          <div class="opt-foot">
            <el-checkbox v-model="optimize.save">优化后另存为新简历版本（不覆盖原简历）</el-checkbox>
            <span class="muted">素材 {{ optimize.raw.length }} 字</span>
          </div>

          <template v-if="optimize.result">
            <div class="section-title">
              关键词覆盖 · 命中 {{ optimize.result.matchedKeywords.length }} 个 / 缺失 {{ optimize.result.missingKeywords.length }} 个
            </div>
            <div>
              <el-tag v-for="word in optimize.result.matchedKeywords" :key="word" class="tag" type="success" effect="light">{{ word }}</el-tag>
              <el-tag v-for="word in optimize.result.missingKeywords" :key="word" class="tag" type="danger" effect="light">{{ word }}</el-tag>
              <span v-if="!optimize.result.matchedKeywords.length && !optimize.result.missingKeywords.length" class="muted">没有识别到明确的关键词</span>
            </div>

            <div class="section-title">逐条专项改写（{{ optimize.result.rewrites.length }} 条）</div>
            <div v-if="optimize.result.rewrites.length" class="rewrite-list">
              <div v-for="(item, index) in optimize.result.rewrites" :key="index" class="rewrite-item">
                <div class="rewrite-head">
                  <el-tag size="small" effect="plain" type="info">{{ item.section || '其他' }}</el-tag>
                  <span class="rewrite-reason">{{ item.reason }}</span>
                </div>
                <div class="rewrite-row">
                  <span class="rewrite-label">原</span>
                  <div class="rewrite-before">{{ item.original }}</div>
                </div>
                <div class="rewrite-row">
                  <span class="rewrite-label is-after">改</span>
                  <div class="rewrite-after">{{ item.optimized }}</div>
                </div>
              </div>
            </div>
            <div v-else class="muted">没有识别到可改写的经历描述，先补充项目细节再试一次。</div>

            <template v-if="optimize.result.newProjects && optimize.result.newProjects.length">
              <div class="section-title">AI 补全的项目经历（{{ optimize.result.newProjects.length }} 个）</div>
              <div class="rewrite-list">
                <div v-for="(project, index) in optimize.result.newProjects" :key="index" class="rewrite-item">
                  <div class="rewrite-head">
                    <el-tag size="small" type="warning" effect="light">AI 补全</el-tag>
                    <span class="rewrite-reason">{{ project.title }} · {{ project.role }}</span>
                  </div>
                  <div class="rewrite-row">
                    <span class="rewrite-label">景</span>
                    <div class="rewrite-before">{{ project.description }}</div>
                  </div>
                  <div class="rewrite-row">
                    <span class="rewrite-label is-after">技</span>
                    <div class="rewrite-after">{{ (project.techStack || []).join('、') }}</div>
                  </div>
                  <div class="rewrite-row" v-for="(bullet, bIndex) in project.bullets" :key="bIndex">
                    <span class="rewrite-label is-after">行</span>
                    <div class="rewrite-after">{{ bullet }}</div>
                  </div>
                </div>
              </div>
            </template>

            <div class="section-title with-action">
              <span>完整优化稿</span>
              <el-button link type="primary" :icon="DocumentCopy" @click="copyOptimized">复制全文</el-button>
            </div>
            <el-input :model-value="optimize.result.optimizedContent" type="textarea" :rows="12" readonly />

            <div class="section-title">还需要你补充</div>
            <el-timeline>
              <el-timeline-item v-for="(item, index) in optimize.result.suggestions" :key="index" type="primary">
                {{ item }}
              </el-timeline-item>
            </el-timeline>

            <el-alert :title="optimize.result.comment" type="info" :closable="false" show-icon />
            <div v-if="optimize.result.savedResumeId" class="opt-saved">
              <el-icon><CircleCheck /></el-icon>
              已另存为新简历版本，原简历没有被修改
              <el-button link type="primary" @click="router.push('/resumes')">去简历管理查看</el-button>
            </div>
            <AiUsageBar :usage="optimize.usage" :mocked="optimize.mocked" />
          </template>
        </div>
      </el-tab-pane>

      <!-- ----------------------------- 简历排版 ---------------------------- -->
      <el-tab-pane label="简历排版" name="structure">
        <div class="card">
          <div class="toolbar opt-toolbar">
            <div class="field">
              <span class="field-caption">选择简历</span>
              <el-select v-model="structure.resumeId" placeholder="选择简历（自动带入正文）" clearable style="width: 220px" @change="applyStructureResume">
                <el-option v-for="item in resumes" :key="item.id" :label="item.title" :value="item.id" />
              </el-select>
            </div>
            <el-button type="primary" :loading="structure.loading" :icon="MagicStick" @click="runStructure">
              开始排版
            </el-button>
            <template v-if="structure.result">
              <el-button v-if="!structure.editing" :icon="EditPen" @click="startEditStructure">编辑模板</el-button>
              <el-button v-else type="primary" :icon="Check" :loading="structure.saving" @click="saveStructureEdit">
                保存修改
              </el-button>
              <el-button v-if="structure.editing" :icon="Close" @click="cancelEditStructure">放弃修改</el-button>
              <el-button :icon="Printer" :disabled="structure.editing" @click="printResume">打印 / 导出 PDF</el-button>
            </template>
          </div>

          <div class="opt-field">
            <span class="field-caption">简历正文（选简历自动带入，也可以直接粘贴）</span>
            <el-input v-model="structure.raw" type="textarea" :rows="6"
                      placeholder="粘贴 PDF 复制出来的简历文本即可，板块标题排在内容前面或后面都能识别" />
          </div>

          <div class="opt-foot">
            <span class="muted">正文 {{ structure.raw.length }} 字 · 识别结果会写回这份简历；进入编辑后可按模块修改内容</span>
          </div>

          <template v-if="structure.result">
            <div class="section-title with-action">
              <span>{{ structure.editing ? '排版预览（A4）· 下方修改，上方实时预览' : '排版预览（A4）' }}</span>
              <span class="muted">{{ structure.usage ? (structure.mocked ? '本地规则解析' : 'AI 识别') : '上次识别的结果' }}：导出 PDF 时在打印对话框里选「另存为 PDF」</span>
            </div>
            <div class="sheet-toolbar">
              <div class="field">
                <span class="field-caption">排版模板</span>
                <el-select v-model="structure.template" style="width: 168px" @change="applyTemplate">
                  <el-option v-for="item in RESUME_TEMPLATES" :key="item.id" :label="item.name" :value="item.id" />
                  <el-option v-if="canReplica" :label="REPLICA_TEMPLATE.name" :value="REPLICA_TEMPLATE.id" />
                </el-select>
              </div>
              <!-- 原版复刻：头像能换，配色能改，改完跟着简历存 -->
              <template v-if="structure.template === REPLICA_TEMPLATE.id">
                <div class="field">
                  <span class="field-caption">头像</span>
                  <el-upload :show-file-list="false" accept="image/*" :before-upload="onAvatarPick">
                    <el-button :icon="Picture">更换头像</el-button>
                  </el-upload>
                </div>
                <div class="field">
                  <span class="field-caption">强调色</span>
                  <el-color-picker v-model="structure.style.accentColor" @change="onAccentChange" />
                </div>
              </template>
              <span class="muted sheet-toolbar-hint">
                {{ activeTemplate.hint }}<template v-if="activeTemplate.source"> · 版式参考 {{ activeTemplate.source.label }}（{{ activeTemplate.source.license }} 许可）</template>
              </span>
            </div>
            <div class="sheet-wrap">
              <ResumeSheet :structure="structure.editing ? structure.draft : structure.result"
                           :template="structure.template"
                           :avatar="structure.avatar"
                           :style="structure.style" />
            </div>
            <ResumeEditor v-if="structure.editing" :structure="structure.draft" />
            <AiUsageBar v-if="structure.usage" :usage="structure.usage" :mocked="structure.mocked" />
          </template>
        </div>
      </el-tab-pane>
      <!-- ----------------------------- 面试题生成 ---------------------------- -->
      <el-tab-pane label="AI 出题" name="question">
        <div class="card">
          <div class="toolbar quiz-toolbar">
            <div class="field">
              <span class="field-caption">参考职位</span>
              <el-select v-model="quiz.jobId" placeholder="选它的 JD 当出题背景" clearable filterable style="width: 230px">
                <el-option v-for="item in jobs" :key="item.id" :label="`${item.jobName}（${item.companyName || '未关联公司'}）`" :value="item.id" />
              </el-select>
            </div>
            <div class="field">
              <span class="field-caption">参考简历</span>
              <el-select v-model="quiz.resumeId" placeholder="选简历会出项目相关的题" clearable style="width: 210px">
                <el-option v-for="item in resumes" :key="item.id" :label="item.title" :value="item.id" />
              </el-select>
            </div>
            <div class="field">
              <span class="field-caption">出题范围</span>
              <el-select v-model="quiz.categories" multiple collapse-tags placeholder="不选则由 AI 按岗位决定"
                         style="width: 220px" clearable>
                <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </div>
            <div class="field">
              <span class="field-caption">难度</span>
              <el-select v-model="quiz.difficulty" placeholder="难度" style="width: 104px">
                <el-option label="简单" value="EASY" />
                <el-option label="中等" value="MEDIUM" />
                <el-option label="困难" value="HARD" />
              </el-select>
            </div>
            <div class="field">
              <span class="field-caption">每个分类题数</span>
              <el-input-number v-model="quiz.countPerCategory" :min="1" :max="5" :step="1"
                               controls-position="right" style="width: 110px" />
            </div>
            <div class="field">
              <span class="field-caption">生成后</span>
              <el-checkbox v-model="quiz.save">保存到题库</el-checkbox>
            </div>
            <el-button type="primary" :loading="quiz.loading" :icon="MagicStick" @click="runGenerate">生成面试题</el-button>
          </div>
          <div class="field-hint quiz-hint">
            题目总数 = 分类数 × 每个分类题数；不选出题范围时由 AI 按岗位决定分类（最多 5 个），
            所以总数会有浮动。生成后点题目可展开看参考答案。
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
          <AiUsageBar :usage="quiz.usage" :mocked="quiz.mocked" />
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
          <li><span>03</span>按目标岗位逐条改写简历素材</li>
          <li><span>04</span>生成个性化面试练习题</li>
          <li><span>05</span>把结果沉淀到题库持续复习</li>
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
          <div class="field-hint">
            只填到 /v1 就行，末尾不用带 /chat/completions 或 /models（多写了后端会自动去掉）。
            <template v-if="configForm.provider === 'OPENAI'">
              OpenAI 官方地址在国内直连不通，需要本机代理，或改用 DeepSeek 等兼容厂商。
            </template>
          </div>
        </el-form-item>
        <el-form-item label="模型名称">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-select v-model="configForm.model" filterable allow-create default-first-option
                       placeholder="点右侧按钮拉取，或直接输入模型名" style="flex: 1">
              <el-option v-for="item in modelOptions" :key="item" :label="item" :value="item" />
            </el-select>
            <el-button :loading="loadingModels" @click="loadModels()">拉取模型</el-button>
          </div>
          <div class="field-hint">{{ modelHint }}</div>
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="configForm.apiKey" type="password" show-password
                    :placeholder="aiMode.hasApiKey ? `已保存 ${aiMode.apiKeyMasked}，留空则保持不变` : '请输入 API Key'" />
          <div class="field-hint">密钥只会发送到你的后端，不会出现在配置查询结果或日志中。</div>
        </el-form-item>
        <el-form-item label="思考模式">
          <el-radio-group v-model="configForm.thinkingMode" class="mode-picker">
            <el-radio-button value="DEFAULT">跟随厂商</el-radio-button>
            <el-radio-button value="OFF">关闭</el-radio-button>
            <el-radio-button value="ON">开启</el-radio-button>
          </el-radio-group>
          <div class="field-hint">
            仅部分厂商支持（DeepSeek 支持）。开启会输出思维链、token 明显变多；关闭则按非思考模式回答。
          </div>
        </el-form-item>
        <el-form-item label="单价（元 / 百万 tokens，可选）">
          <div class="price-grid">
            <el-input-number v-model="configForm.inputPrice" :min="0" :controls="false" placeholder="输入" />
            <el-input-number v-model="configForm.cachePrice" :min="0" :controls="false" placeholder="缓存命中" />
            <el-input-number v-model="configForm.outputPrice" :min="0" :controls="false" placeholder="输出" />
          </div>
          <div class="field-hint">
            按厂商价目表填写，填了才会在结果下方显示预估费用；留空只显示 token 数。
          </div>
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
import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, CircleCheck, Close, DocumentCopy, EditPen, MagicStick, Picture, Printer, Setting } from '@element-plus/icons-vue'
import { aiApi, jobApi, questionApi, resumeApi } from '@/api'
import { scoreColor } from '@/utils/theme'
import AiUsageBar from '@/components/AiUsageBar.vue'
import ResumeEditor from '@/components/ResumeEditor.vue'
import ResumeSheet from '@/components/ResumeSheet.vue'
import { DEFAULT_RESUME_TEMPLATE, REPLICA_TEMPLATE, RESUME_TEMPLATES, findResumeTemplate } from '@/components/resumeTemplates'
import { ensureLocalAiNotice } from '@/utils/aiLocalNotice'
import { buildRunningNotice } from '@/utils/aiRunningNotice'

const route = useRoute()
const router = useRouter()

const tab = ref('jd')
const jobs = ref([])
const resumes = ref([])
const aiMode = reactive({ mock: true, model: 'mock-local', provider: 'OPENAI', apiMode: 'CHAT_COMPLETIONS', hasApiKey: false, apiKeyMasked: '' })
const configDrawer = ref(false)
const savingConfig = ref(false)
const testingConfig = ref(false)
const modelOptions = ref([])
const loadingModels = ref(false)
const providers = [
  { label: 'OpenAI', value: 'OPENAI', baseUrl: 'https://api.openai.com/v1', model: 'gpt-4.1-mini' },
  { label: 'DeepSeek', value: 'DEEPSEEK', baseUrl: 'https://api.deepseek.com/v1', model: 'deepseek-chat' },
  { label: '通义千问', value: 'QWEN', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', model: 'qwen-plus' },
  { label: 'Moonshot', value: 'MOONSHOT', baseUrl: 'https://api.moonshot.cn/v1', model: 'moonshot-v1-8k' },
  { label: 'Ollama', value: 'OLLAMA', baseUrl: 'http://localhost:11434/v1', model: 'qwen2.5' },
  { label: '其他兼容厂商', value: 'CUSTOM', baseUrl: '', model: '' }
]
const configForm = reactive({
  provider: 'OPENAI', apiMode: 'CHAT_COMPLETIONS', baseUrl: 'https://api.openai.com/v1',
  model: 'gpt-4.1-mini', apiKey: '', enabled: true,
  thinkingMode: 'DEFAULT', inputPrice: null, cachePrice: null, outputPrice: null
})
// 分类由后端统一下发，保证出题分类和题库里的固定分类一致
const categoryOptions = ref([])

const jd = reactive({ jobId: null, text: '', loading: false, result: null, usage: null, mocked: false, history: [], fromHistory: false, historyTime: '' })
const match = reactive({ jobId: null, resumeId: null, loading: false, result: null, usage: null, mocked: false, history: [], fromHistory: false, historyTime: '' })
const optimize = reactive({
  jobId: null, resumeId: null, jdText: '', raw: '', focus: '',
  save: true, loading: false, result: null, usage: null, mocked: false,
  gapLoading: false,
  gaps: [],
  supplements: {},
  allowFabrication: false
})
const structure = reactive({
  resumeId: null, raw: '', loading: false, result: null, usage: null, mocked: false,
  // 编辑态：下方结构化表单直接修改草稿，上方 A4 只负责实时预览
  editing: false, saving: false, draft: null,
  // A4 版式，存进 content_json.template，下次打开还是这套
  template: DEFAULT_RESUME_TEMPLATE,
  // 头像与导入 PDF 时提取到的版式，用于「原版复刻」
  avatar: '', style: null
})

const activeTemplate = computed(() => findResumeTemplate(structure.template))

/** 这份简历有没有可复刻的版式数据（从 PDF 导入的才有） */
const canReplica = computed(() => !!structure.style)

const quiz = reactive({
  jobId: null, resumeId: null, categories: [], difficulty: 'MEDIUM',
  countPerCategory: 3, save: true, loading: false, result: [], savedCount: 0, usage: null, mocked: false
})

const difficultyLabel = (value) => ({ EASY: '简单', MEDIUM: '中等', HARD: '困难' }[value] || value)
/** 历史记录时间格式化：2026-09-16T10:00:00 → 2026-09-16 10:00 */
const formatTime = (value) => (value || '').replace('T', ' ').slice(0, 16)
const providerLabel = (value) => providers.find((item) => item.value === value)?.label || value
const modeLabel = (value) => value === 'RESPONSES' ? 'Responses' : 'Chat'

function applyProviderPreset(value) {
  const preset = providers.find((item) => item.value === value)
  if (preset && value !== 'CUSTOM') {
    configForm.baseUrl = preset.baseUrl
    configForm.model = preset.model
    modelOptions.value = []
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
    enabled: value.enabled !== false,
    thinkingMode: value.thinkingMode || 'DEFAULT',
    inputPrice: value.inputPrice ?? null,
    cachePrice: value.cachePrice ?? null,
    outputPrice: value.outputPrice ?? null
  })
}

/** el-input-number 清空后可能是 '' 或 undefined，统一转成 null，后端按「不估算费用」处理 */
const priceOrNull = (value) => (value === '' || value === undefined || value === null ? null : Number(value))

function openConfig() {
  configDrawer.value = true
  // 已保存过 API Key 时，打开抽屉就直接把模型列表拉出来
  if (!modelOptions.value.length) {
    loadModels(true)
  }
}

// 其他页面选择「去配置 API Key」时，即使本页被 keep-alive 缓存，也要重新打开配置抽屉。
watch(() => route.query.configure, (value) => {
  if (value !== '1') return
  openConfig()
  const query = { ...route.query }
  delete query.configure
  router.replace({ query })
}, { immediate: true })

const beforeAiAction = () => ensureLocalAiNotice({ config: aiMode, onConfigure: openConfig })

/**
 * 正在跑的 AI 任务。首页顶部的提示条和离开页面的二次确认都用它，
 * 免得用户跑了一半切走，回来发现结果没了（优化 / 排版 / 出题不会自动回填）。
 */
const runningNotice = computed(() => buildRunningNotice({
  jd: jd.loading,
  match: match.loading,
  gap: optimize.gapLoading,
  optimize: optimize.loading,
  structure: structure.loading,
  quiz: quiz.loading
}))

// 离开本页前拦一下：没事就直接放行，有任务在跑才弹确认
onBeforeRouteLeave(async () => {
  if (!runningNotice.value) {
    return true
  }
  try {
    await ElMessageBox.confirm(runningNotice.value.leaveMessage, runningNotice.value.leaveTitle, {
      confirmButtonText: '离开页面',
      cancelButtonText: '留下等结果',
      distinguishCancelAndClose: true,
      type: 'warning'
    })
    return true
  } catch (action) {
    // 点「留下等结果」或者直接关掉弹窗，都留在本页
    return false
  }
})

const modelHint = computed(() => modelOptions.value.length
  ? `已获取 ${modelOptions.value.length} 个模型，可直接搜索选择`
  : '填好 Base URL 和 API Key 后点「拉取模型」，也可以直接输入模型名')

/** 拉模型列表：silent=true 用于自动拉取，失败不打扰用户（错误提示由 request 拦截器统一给出）。 */
async function loadModels(silent = false) {
  const baseUrl = configForm.baseUrl.trim()
  const hasKey = Boolean(configForm.apiKey.trim() || aiMode.hasApiKey)
  if (!baseUrl || !hasKey) {
    if (!silent) {
      ElMessage.warning(baseUrl ? '请先填写 API Key' : '请先填写 Base URL')
    }
    return
  }
  loadingModels.value = true
  try {
    const list = await aiApi.models({
      provider: configForm.provider,
      apiMode: configForm.apiMode,
      baseUrl,
      apiKey: configForm.apiKey.trim()
    })
    modelOptions.value = list || []
    if (!silent && modelOptions.value.length) {
      ElMessage.success(`已获取 ${modelOptions.value.length} 个模型，可搜索筛选`)
    }
  } catch (error) {
    // 拦截器已经提示过具体原因，这里不再重复弹窗
  } finally {
    loadingModels.value = false
  }
}

async function saveConfig(closeDrawer = false) {
  if (!configForm.baseUrl.trim() || !configForm.model.trim()) {
    ElMessage.warning('请填写 Base URL 和模型名称')
    return false
  }
  savingConfig.value = true
  try {
    const value = await aiApi.saveConfig({
      provider: configForm.provider,
      apiMode: configForm.apiMode,
      baseUrl: configForm.baseUrl,
      model: configForm.model,
      apiKey: configForm.apiKey,
      enabled: configForm.enabled,
      thinkingMode: configForm.thinkingMode,
      inputPrice: priceOrNull(configForm.inputPrice),
      cachePrice: priceOrNull(configForm.cachePrice),
      outputPrice: priceOrNull(configForm.outputPrice)
    })
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

/** 加载 JD 解析历史：选了职位后自动拉取最近一次结果 */
async function loadJdHistory() {
  // 切换职位时先清空旧结果，避免残留
  jd.result = null
  jd.usage = null
  jd.fromHistory = false
  jd.historyTime = ''
  jd.history = []
  if (!jd.jobId) {
    return
  }
  try {
    const list = await aiApi.history({ type: 'JD_ANALYZE', jobId: jd.jobId, limit: 5 })
    jd.history = list || []
    if (jd.history.length) {
      // 自动回填最近一次结果，不消耗 token
      const latest = jd.history[0]
      jd.result = typeof latest.result === 'string' ? JSON.parse(latest.result) : latest.result
      jd.fromHistory = true
      jd.historyTime = latest.createdAt || ''
      jd.usage = null
    }
  } catch (error) {
    // 历史记录加载失败不影响主流程
  }
}

async function runAnalyzeJd() {
  if (!jd.jobId && !jd.text.trim()) {
    ElMessage.warning('请选择职位或粘贴 JD 文本')
    return
  }
  if (!await beforeAiAction()) return
  jd.loading = true
  try {
    const result = await aiApi.analyzeJd({ jobId: jd.jobId, jobDescription: jd.text })
    jd.result = result.data
    jd.usage = result.usage
    jd.mocked = result.mocked
    jd.fromHistory = false
    ElMessage.success('解析完成')
    loadJdHistory()
  } finally {
    jd.loading = false
  }
}

/** 加载简历匹配历史：选了职位+简历后自动拉取最近一次结果 */
async function loadMatchHistory() {
  // 切换职位或简历时先清空旧结果，避免残留
  match.result = null
  match.usage = null
  match.fromHistory = false
  match.historyTime = ''
  match.history = []
  if (!match.jobId || !match.resumeId) {
    return
  }
  try {
    const list = await aiApi.history({ type: 'RESUME_MATCH', jobId: match.jobId, resumeId: match.resumeId, limit: 5 })
    match.history = list || []
    if (match.history.length) {
      const latest = match.history[0]
      match.result = typeof latest.result === 'string' ? JSON.parse(latest.result) : latest.result
      match.fromHistory = true
      match.historyTime = latest.createdAt || ''
      match.usage = null
    }
  } catch (error) {
    // 历史记录加载失败不影响主流程
  }
}

async function runMatch() {
  if (!match.jobId || !match.resumeId) {
    ElMessage.warning('请同时选择职位和简历')
    return
  }
  if (!await beforeAiAction()) return
  match.loading = true
  try {
    const result = await aiApi.matchResume({ jobId: match.jobId, resumeId: match.resumeId })
    match.result = result.data
    match.usage = result.usage
    match.mocked = result.mocked
    match.fromHistory = false
    ElMessage.success('分析完成')
    loadMatchHistory()
  } finally {
    match.loading = false
  }
}

/** 「简历排版」：选中简历就把正文带进来，识别结果会写回这份简历的 contentJson */
/** 取上次识别的结构化结果：有就先把预览显示出来，省一次 AI 调用 */
function parseSavedStructure(contentJson) {
  if (!contentJson) {
    return null
  }
  try {
    const parsed = typeof contentJson === 'string' ? JSON.parse(contentJson) : contentJson
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

/** 版式 JSON 由后端生成，但仍要防住手工改坏的脏数据，解析失败就当没有 */
function parseSavedStyle(styleJson) {
  const parsed = parseSavedStructure(styleJson)
  return parsed && typeof parsed === 'object' ? parsed : null
}

function applyStructureResume(resumeId) {
  if (!resumeId) {
    return
  }
  const resume = resumes.value.find((item) => item.id === resumeId)
  const text = (resume?.content || '').trim() || resumeToRawText(resume)
  if (!text) {
    ElMessage.warning('这份简历还没有正文，请把简历文本粘贴到下面')
  }
  structure.raw = text || ''
  structure.result = parseSavedStructure(resume?.contentJson)
  // 头像和版式跟着简历走：导入 PDF 时存下来的，这里取出来就能还原外观
  structure.avatar = resume?.avatar || ''
  structure.style = parseSavedStyle(resume?.styleJson)
  // 有版式数据就默认用「原版复刻」，否则退回上次选的模板
  structure.template = structure.style
    ? REPLICA_TEMPLATE.id
    : (structure.result?.template || DEFAULT_RESUME_TEMPLATE)
  structure.usage = null
  structure.mocked = false
  if (structure.editing) {
    ElMessage.warning('已放弃未保存的修改')
  }
  cancelEditStructure()
}

async function runStructure() {
  if (!structure.resumeId && !structure.raw.trim()) {
    ElMessage.warning('请选择简历，或粘贴简历文本')
    return
  }
  if (!await beforeAiAction()) return
  structure.loading = true
  try {
    const result = await aiApi.structureResume({
      resumeId: structure.resumeId || null,
      content: structure.raw.trim() || null
    })
    structure.result = result.data
    // 后端只回结构化内容，版式是用户自己选的，重新解析时不能丢
    structure.result.template = structure.template
    structure.usage = result.usage
    structure.mocked = result.mocked
    cancelEditStructure()
    ElMessage.success('排版完成，可以按模块编辑，也可以打印导出 PDF')
  } finally {
    structure.loading = false
  }
}

/** 导出 PDF 走浏览器打印（文字可选、ATS 友好），比后端生成 PDF 更稳 */
function printResume() {
  window.print()
}

/** 结构里缺字段时补齐，省得编辑时点「添加」报错 */
function normalizeStructure(source) {
  const draft = JSON.parse(JSON.stringify(source || {}))
  const lists = ['education', 'work', 'projects', 'skills', 'honors', 'sections']
  draft.basics = draft.basics || {}
  lists.forEach((key) => {
    if (!Array.isArray(draft[key])) {
      draft[key] = []
    }
  })
  draft.work.concat(draft.projects).forEach((item) => {
    if (!Array.isArray(item.bullets)) {
      item.bullets = []
    }
  })
  // 自定义板块：标题 + 若干条内容
  draft.sections = draft.sections.filter((item) => item && typeof item === 'object')
  draft.sections.forEach((section) => {
    section.title = section.title || ''
    if (!Array.isArray(section.items)) {
      section.items = []
    }
  })
  return draft
}

/** 进入编辑态：先深拷贝一份草稿，改坏了还能「放弃修改」 */
function startEditStructure() {
  if (!structure.result) {
    return
  }
  structure.draft = normalizeStructure(structure.result)
  structure.editing = true
  ElMessage.info('请在预览下方按模块修改，A4 预览会实时更新')
}

/** 退出编辑态并丢掉草稿 */
function cancelEditStructure() {
  structure.editing = false
  structure.draft = null
}
/** 写回简历：结构化 JSON 进 content_json，基本信息顺手同步到简历字段 */
async function persistStructure(source) {
  const resume = resumes.value.find((item) => item.id === structure.resumeId)
  if (!resume) {
    return null
  }
  const draft = normalizeStructure(source)
  draft.template = structure.template
  const basics = draft.basics || {}
  await resumeApi.update(structure.resumeId, {
    title: resume.title,
    name: basics.name || resume.name,
    phone: basics.phone || resume.phone,
    email: basics.email || resume.email,
    education: basics.degree || resume.education,
    workYears: resume.workYears ?? 0,
    skills: resume.skills,
    summary: basics.summary || resume.summary,
    content: resume.content,
    isDefault: resume.isDefault === 1,
    contentJson: JSON.stringify(draft),
    // 头像和版式原样带回，否则排版一次就把导入时提取的数据覆盖没了
    avatar: structure.avatar || '',
    styleJson: structure.style ? JSON.stringify(structure.style) : ''
  })
  return draft
}

/** 换 A4 版式：不在编辑态就直接存下来，在编辑态则跟着「保存修改」一起落库 */
async function applyTemplate(id) {
  structure.template = id
  const target = structure.editing ? structure.draft : structure.result
  if (target) {
    target.template = id
  }
  if (id === REPLICA_TEMPLATE.id && !structure.style) {
    // 原版复刻依赖导入时提取的版式，没有数据时不拦着，只提示一下
    ElMessage.info('这份简历没有导入时的版式数据，可以先用其他模板；从 PDF 导入的简历才有')
    return
  }
  if (structure.editing || !structure.result) {
    return
  }
  if (!structure.resumeId) {
    ElMessage.info(`已换成「${findResumeTemplate(id).name}」，选一份简历后才会保存`)
    return
  }
  const saved = await persistStructure(structure.result)
  if (saved) {
    structure.result = saved
    ElMessage.success(`已换成「${findResumeTemplate(id).name}」，下次打开还是这套`)
  } else {
    ElMessage.warning('版式没存上，点「编辑模板 → 保存修改」可以再存一次')
  }
}

/** 保存：结构化 JSON 写回 contentJson，基本信息和简历字段顺手同步 */
async function saveStructureEdit() {
  if (!structure.resumeId) {
    ElMessage.warning('请先在上方选择要写回的简历')
    return
  }
  structure.saving = true
  try {
    const saved = await persistStructure(structure.draft)
    if (!saved) {
      ElMessage.warning('没找到这份简历，请重新选择')
      return
    }
    structure.result = saved
    cancelEditStructure()
    await refreshOptions()
    ElMessage.success('已保存到这份简历，下次打开就是改过的版本')
  } finally {
    structure.saving = false
  }
}

/** 把简历的结构化字段拼成一段素材文本，「简历优化」直接拿它当输入 */
function resumeToRawText(resume) {
  return [resume?.skills && `技能：${resume.skills}`, resume?.summary, resume?.content]
    .filter(Boolean)
    .join('\n')
    .trim()
}

/** 头像大小上限：data URL 落库，过大既撑爆字段也没必要（打印出来就几厘米） */
const AVATAR_MAX_BYTES = 400 * 1024
/** 统一裁成正方形，和模板里 object-fit: cover 的预期一致 */
const AVATAR_EDGE = 400

/**
 * 用户换头像：本地读成 data URL 前先缩到 400px 方图。
 *
 * <p>不直接存原图：手机拍的照片动辄几 MB，转成 base64 还要再涨三分之一，
 * 存进数据库既慢又没必要——A4 纸上头像只有两三厘米，400px 已经远超打印精度。</p>
 */
function onAvatarPick(file) {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return false
  }
  const reader = new FileReader()
  reader.onload = () => {
    const image = new Image()
    image.onload = () => {
      const canvas = document.createElement('canvas')
      canvas.width = AVATAR_EDGE
      canvas.height = AVATAR_EDGE
      const ctx = canvas.getContext('2d')
      // 取原图中间的正方形再缩放，避免人像被拉扁
      const edge = Math.min(image.width, image.height)
      const sx = (image.width - edge) / 2
      const sy = (image.height - edge) / 2
      ctx.drawImage(image, sx, sy, edge, edge, 0, 0, AVATAR_EDGE, AVATAR_EDGE)
      const dataUrl = canvas.toDataURL('image/png')
      if (dataUrl.length > AVATAR_MAX_BYTES) {
        ElMessage.warning('图片太大了，换一张小一点的')
        return
      }
      structure.avatar = dataUrl
      saveStructureExtra()
    }
    image.src = reader.result
  }
  reader.readAsDataURL(file)
  // 交给 FileReader 处理，不用 el-upload 自己上传
  return false
}

/** 强调色改完立即落到简历上，不用用户再点一次保存 */
function onAccentChange() {
  saveStructureExtra()
}

/** 把头像与版式写回当前简历；没选简历时只留在本次会话里 */
async function saveStructureExtra() {
  if (!structure.resumeId) {
    ElMessage.info('选一份简历后才会保存')
    return
  }
  if (structure.result) {
    await persistStructure(structure.result)
  }
}

/** 从简历管理页跳过来时静默带入素材 */
function fillOptimizeRaw(resumeId) {
  const text = resumeToRawText(resumes.value.find((item) => item.id === resumeId))
  if (text) {
    optimize.raw = text
  }
}

/** 从匹配结果导入缺失技能：把 match.result.missingSkills 灌进 optimize.gaps */
async function importMatchGaps() {
  if (!optimize.jobId || !optimize.resumeId) {
    ElMessage.warning('请先选择目标岗位和简历')
    return
  }
  if (!await beforeAiAction()) return
  optimize.gapLoading = true
  try {
    const result = await aiApi.matchResume({ jobId: optimize.jobId, resumeId: optimize.resumeId })
    const gaps = result.data?.missingSkills || []
    optimize.gaps = gaps
    optimize.supplements = gaps.reduce((acc, skill) => ({ ...acc, [skill]: '' }), {})
    ElMessage.success(gaps.length ? `导入了 ${gaps.length} 个缺口，可逐项补充项目经历` : '当前没有识别到缺口')
  } catch (error) {
    // 拦截器已经提示过具体原因
  } finally {
    optimize.gapLoading = false
  }
}

/** 手动换简历时先确认，避免把用户已经写好的素材直接冲掉 */
function applyOptimizeResume(resumeId) {
  if (!resumeId) {
    return
  }
  const text = resumeToRawText(resumes.value.find((item) => item.id === resumeId))
  if (!text) {
    ElMessage.warning('这份简历还没有正文，请在素材框里手动填写')
    return
  }
  if (optimize.raw.trim() && optimize.raw.trim() !== text) {
    ElMessageBox.confirm('素材框里已经有内容，替换为这份简历的正文吗？', '提示', { type: 'warning' })
      .then(() => { optimize.raw = text })
      .catch(() => {})
    return
  }
  optimize.raw = text
}

async function runOptimize() {
  if (!optimize.jobId && !optimize.jdText.trim()) {
    ElMessage.warning('请选择目标岗位，或粘贴职位描述')
    return
  }
  if (!optimize.raw.trim()) {
    ElMessage.warning('请把项目经历等素材粘贴到「原始素材」里')
    return
  }
  if (!await beforeAiAction()) return
  optimize.loading = true
  try {
    const result = await aiApi.optimizeResume({
      jobId: optimize.jobId,
      resumeId: optimize.resumeId,
      jobDescription: optimize.jdText,
      rawContent: optimize.raw,
      focus: optimize.focus,
      save: optimize.save,
      supplementalProjects: optimize.supplements,
      allowFabrication: optimize.allowFabrication
    })
    optimize.result = result.data
    optimize.usage = result.usage
    optimize.mocked = result.mocked
    ElMessage.success(`完成 ${optimize.result?.rewrites?.length || 0} 条专项改写`)
  } finally {
    optimize.loading = false
  }
}

async function copyOptimized() {
  const text = optimize.result?.optimizedContent
  if (!text) {
    ElMessage.warning('还没有可以复制的正文')
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制优化稿')
  } catch (error) {
    ElMessage.warning('当前浏览器不允许自动复制，请手动选中正文复制')
  }
}

async function runGenerate() {
  if (!quiz.jobId && !quiz.resumeId) {
    ElMessage.warning('至少选择一个职位或一份简历')
    return
  }
  if (!await beforeAiAction()) return
  quiz.loading = true
  try {
    const result = await aiApi.generateQuestions({
      jobId: quiz.jobId,
      resumeId: quiz.resumeId,
      categories: quiz.categories,
      difficulty: quiz.difficulty,
      countPerCategory: quiz.countPerCategory,
      save: quiz.save
    })
    quiz.result = result.data?.questions || []
    quiz.savedCount = result.data?.savedCount || 0
    quiz.usage = result.usage
    quiz.mocked = result.mocked
    ElMessage.success(`生成 ${quiz.result.length} 道题`)
  } finally {
    quiz.loading = false
  }
}

async function loadBaseData() {
  const [jobPage, resumeList, aiConfig, categories] = await Promise.all([
    jobApi.page({ pageNum: 1, pageSize: 100 }),
    resumeApi.all(),
    aiApi.config().catch(() => null),
    questionApi.categoryOptions().catch(() => [])
  ])
  jobs.value = jobPage.records
  resumes.value = resumeList
  categoryOptions.value = categories
  fillConfig(aiConfig)

  const defaultResume = resumes.value.find((item) => item.isDefault === 1) || resumes.value[0]
  if (defaultResume) {
    match.resumeId = defaultResume.id
    quiz.resumeId = defaultResume.id
  }
  if (route.query.resumeId) {
    match.resumeId = Number(route.query.resumeId)
    quiz.resumeId = Number(route.query.resumeId)
    optimize.resumeId = Number(route.query.resumeId)
    fillOptimizeRaw(Number(route.query.resumeId))
    structure.resumeId = Number(route.query.resumeId)
    applyStructureResume(Number(route.query.resumeId))
  }
  // 简历管理页的「按岗位优化」会带上 tab=optimize，直接落到这个页签
  if (route.query.tab) {
    tab.value = String(route.query.tab)
  }
  if (jobs.value.length) {
    match.jobId = jobs.value[0].id
    quiz.jobId = jobs.value[0].id
    jd.jobId = jobs.value[0].id
    optimize.jobId = jobs.value[0].id
  }
  // 直接赋值不会触发 el-select 的 change 事件，这里手动加载一次历史记录
  await Promise.all([loadJdHistory(), loadMatchHistory()])
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

.ai-running { margin-bottom: 14px; }
.ai-running-title { font-weight: 600; }
.ai-running-jump { margin-left: 10px; font-size: 12px; }
.ai-running-detail { margin-top: 4px; font-size: 12px; line-height: 1.6; }

.config-intro { display: flex; gap: 12px; margin-bottom: 22px; padding: 14px; border-radius: 10px; background: var(--brand-softer); }
.config-intro-icon { display: grid; place-items: center; width: 34px; height: 34px; flex: none; border-radius: 9px; background: var(--brand); color: #fff; }
.config-intro strong { font-size: 14px; }
.config-intro p { margin: 4px 0 0; color: var(--text-secondary); font-size: 12px; line-height: 1.55; }
.config-form :deep(.el-form-item) { margin-bottom: 20px; }
.mode-picker { width: 100%; }
.quiz-toolbar { align-items: flex-end; }
.quiz-toolbar .field { display: flex; flex-direction: column; gap: 6px; }
.field-caption { color: var(--text-secondary); font-size: 12px; line-height: 1.2; }
.quiz-hint { margin: 0 0 14px; }

.mode-picker :deep(.el-radio-button) { flex: 1; }
.mode-picker :deep(.el-radio-button__inner) { width: 100%; }
.field-hint { margin-top: 7px; color: var(--text-secondary); font-size: 11px; line-height: 1.5; }
.price-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; width: 100%; }
.price-grid :deep(.el-input-number) { width: 100%; }
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
  border: 1px solid var(--brand-soft);
  border-radius: var(--radius);
  background: var(--brand-softer);
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
  box-shadow: 0 4px 12px rgba(156,124,60,.25);
}

.guide-eyebrow { color: var(--brand); font-size: 11px; font-weight: 700; letter-spacing: 1px; }
.ai-guide h3 { margin: 7px 0 18px; font-size: 17px; line-height: 1.45; }
.ai-guide ul { display: grid; gap: 14px; margin: 0; padding: 0; list-style: none; }
.ai-guide li { display: flex; gap: 9px; color: var(--text-regular); font-size: 12px; line-height: 1.55; }
.ai-guide li span { color: var(--brand); font-size: 10px; font-weight: 700; }
.guide-tip { margin-top: 22px; padding-top: 14px; border-top: 1px solid var(--brand-soft); color: var(--text-secondary); font-size: 11px; line-height: 1.6; }

.section-title {
  font-weight: 600;
  margin: 18px 0 10px;
  font-size: 14px;
}

.section-title.with-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* -------------------------------------------------------------- 简历优化 */
.opt-toolbar { flex-wrap: wrap; }
.opt-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 12px; }
.opt-field { display: flex; flex-direction: column; gap: 6px; }
.opt-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; }

.rewrite-list { display: grid; gap: 10px; }
.rewrite-item { padding: 12px 14px; border: 1px solid var(--border-light); border-radius: 10px; background: #fdfcfa; }
.rewrite-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.rewrite-reason { color: var(--text-secondary); font-size: 12px; }
.rewrite-row { display: flex; gap: 8px; align-items: flex-start; }
.rewrite-row + .rewrite-row { margin-top: 6px; }
.rewrite-label { display: grid; place-items: center; flex: none; width: 18px; height: 18px; margin-top: 2px; border-radius: 5px; background: var(--info-soft); color: var(--text-secondary); font-size: 11px; }
.rewrite-label.is-after { background: var(--brand-soft); color: var(--brand-deep); }
.rewrite-before { color: var(--text-secondary); font-size: 13px; line-height: 1.7; }
.rewrite-after { color: var(--text-primary); font-size: 13px; font-weight: 500; line-height: 1.7; }
.opt-saved { display: flex; align-items: center; gap: 6px; margin-top: 12px; color: var(--success); font-size: 13px; }

.tag {
  margin: 0 8px 8px 0;
}

.duty-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.9;
  color: var(--text-regular);
  font-size: 13px;
}

/* 排版预览：A4 纸居中展示，窄屏时横向滚动，避免把卡片撑变形 */
.sheet-toolbar { display: flex; align-items: flex-end; gap: 14px; margin-top: 12px; }
.sheet-toolbar .field { display: flex; flex-direction: column; gap: 6px; }
.sheet-toolbar-hint { padding-bottom: 6px; }

.sheet-wrap {
  display: flex;
  justify-content: center;
  margin-top: 12px;
  padding: 18px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--page-bg);
  overflow: auto;
}
@media (max-width: 1100px) {
  .ai-workspace { grid-template-columns: 1fr; }
  .ai-guide { position: static; }
}

@media (max-width: 900px) {
  .opt-grid { grid-template-columns: 1fr; }
}
</style>
