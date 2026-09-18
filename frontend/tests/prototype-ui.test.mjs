import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('公共布局采用原型的紧凑工作台比例', async () => {
  const css = await read('src/assets/main.css')
  assert.match(css, /--sider-width:\s*196px/)
  assert.match(css, /--content-max:\s*1440px/)
  assert.match(css, /\.page-enter/)
})

test('首页包含原型中的问候与日期信息区', async () => {
  const dashboard = await read('src/views/DashboardView.vue')
  assert.match(dashboard, /dashboard-hero/)
  assert.match(dashboard, /下午好/)
  assert.match(dashboard, /今日专注/)
})

test('品牌标识统一使用职得 JobPath logo 图片', async () => {
  const layout = await read('src/components/AppLayout.vue')
  assert.match(layout, /class="logo-mark" src="@\/assets\/logo-mark\.png"/)

  const login = await read('src/views/LoginView.vue')
  assert.match(login, /class="brand-mark" src="@\/assets\/logo-mark\.png"/)

  // 深色底用带底色的版本，透明图在深色背景上会发灰
  const home = await read('src/views/HomeView.vue')
  assert.match(home, /class="brand-mark brand-mark-on-dark" src="@\/assets\/logo-tile\.png"/)

  const html = await read('index.html')
  assert.match(html, /href="\/favicon-32\.png"/)
  assert.match(html, /href="\/apple-touch-icon\.png"/)
})

test('管理页在内容区展示明确的页面标题', async () => {
  // 卡片现在都放在 grid / flex 容器里，间距由容器 gap 统一控制。
  // 一旦再出现 `.card + .card { margin-top }`，同一行第 1 张卡会被撑高、后面的卡下沉。
  const css = await read('src/assets/main.css')
  assert.doesNotMatch(css, /\.card\s*\+\s*\.card\s*\{/)

  const pages = [
    ['CompanyView.vue', '公司管理'],
    ['JobView.vue', '职位管理'],
    ['ApplicationView.vue', '投递记录'],
    ['InterviewView.vue', '面试管理'],
    ['QuestionView.vue', '面试题库']
  ]

  for (const [file, title] of pages) {
    const source = await read(`src/views/${file}`)
    assert.match(source, new RegExp(`<h2 class="page-title">${title}</h2>`), file)
  }
})

test('题库首页按分类平铺，点进分类才渲染题目列表', async () => {
  const questions = await read('src/views/QuestionView.vue')

  // 一层：一个分类一个框，整张卡片可点
  assert.match(questions, /class="category-grid"/)
  assert.match(questions, /class="card category-card"/)
  assert.match(questions, /@click="openCategory\(item\.category\)"/)
  assert.match(questions, /questionApi\.categoryStats\(\)/)
  assert.match(questions, /已掌握 \{\{ item\.mastered \}\}/)

  // 当前的分类放在 URL 上，刷新和浏览器前进后退都能回到原来的分类
  assert.match(questions, /router\.push\(\{ query: \{ category \} \}\)/)
  assert.match(questions, /watch\(\(\) => route\.query\.category, sync\)/)

  // 二层：题目列表必须在 v-else 分支里，没选分类时不会渲染出来
  const gridIndex = questions.indexOf('class="category-grid"')
  const elseIndex = questions.indexOf('<template v-else>')
  const listIndex = questions.indexOf('class="question-list"')
  assert.ok(gridIndex > -1, '分类卡片区不见了')
  assert.ok(elseIndex > gridIndex, '题目列表没有放在分类总览之后的 v-else 分支')
  assert.ok(listIndex > elseIndex, '题目列表被渲染到了分类总览里')
})

test('AI 助手提供用户级厂商和双协议配置入口', async () => {
  const ai = await read('src/views/AiView.vue')
  assert.match(ai, /配置模型/)
  assert.match(ai, /CHAT_COMPLETIONS/)
  assert.match(ai, /RESPONSES/)
  assert.match(ai, /API Key/)
  assert.match(ai, /测试连接/)
})

test('首次使用本地 AI 时统一提示，并覆盖所有实际 AI 调用入口', async () => {
  const notice = await read('src/utils/aiLocalNotice.js')
  assert.match(notice, /本地规则引擎/)
  assert.match(notice, /更好的 AI 效果/)
  assert.match(notice, /去配置 API Key/)
  assert.match(notice, /localStorage/)

  const ai = await read('src/views/AiView.vue')
  assert.match(ai, /const beforeAiAction = \(\) => ensureLocalAiNotice/)
  assert.match(ai, /watch\(\(\) => route\.query\.configure/)
  assert.match(ai, /delete query\.configure/)
  for (const action of ['runAnalyzeJd', 'runMatch', 'runStructure', 'importMatchGaps', 'runOptimize', 'runGenerate']) {
    assert.match(ai, new RegExp(`async function ${action}\\(\\)[\\s\\S]*?beforeAiAction`), action)
  }

  const jobs = await read('src/views/JobView.vue')
  assert.match(jobs, /ensureLocalAiNotice/)
  const interviews = await read('src/views/InterviewView.vue')
  assert.match(interviews, /ensureLocalAiNotice/)
})

test('AI 助手提供按岗位的简历专项优化入口', async () => {
  const ai = await read('src/views/AiView.vue')
  assert.match(ai, /label="简历优化"/)
  assert.match(ai, /优化后另存为新简历版本/)
  assert.match(ai, /optimizeResume/)

  // 简历管理页要能带着简历跳到优化页签
  const resume = await read('src/views/ResumeView.vue')
  assert.match(resume, /tab: 'optimize'/)
})

test('AI 助手提供简历 A4 排版入口', async () => {
  const ai = await read('src/views/AiView.vue')
  assert.match(ai, /label="简历排版"/)
  assert.match(ai, /structureResume/)
  assert.match(ai, /ResumeSheet/)

  // 简历管理页要能带着简历跳到排版页签
  const resume = await read('src/views/ResumeView.vue')
  assert.match(resume, /tab: 'structure'/)
})

test('排版结果使用只读 A4 预览与独立的模块化编辑器', async () => {
  const ai = await read('src/views/AiView.vue')
  assert.match(ai, /编辑模板/)
  assert.match(ai, /saveStructureEdit/)
  assert.match(ai, /<ResumeEditor v-if="structure\.editing" :structure="structure\.draft"/)
  assert.match(ai, /<ResumeSheet :structure="structure\.editing \? structure\.draft : structure\.result"/)
  assert.doesNotMatch(ai, /:editable="structure\.editing"/)

  const sheet = await read('src/components/ResumeSheet.vue')
  assert.doesNotMatch(sheet, /editable: \{ type: Boolean/)
  assert.doesNotMatch(sheet, /sheet-input|sheet-add|sheet-remove|sheet-handle/)
})

test('模块化编辑器支持分区录入、增删与按钮排序', async () => {
  const editor = await read('src/components/ResumeEditor.vue')
  assert.match(editor, /基本信息/)
  assert.match(editor, /教育背景/)
  assert.match(editor, /工作经历/)
  assert.match(editor, /项目经验/)
  assert.match(editor, /技能特长/)
  assert.match(editor, /荣誉证书/)
  assert.match(editor, /自我评价/)
  assert.match(editor, /@click="addEntry"/)
  assert.match(editor, /@click="removeEntry"/)
  assert.match(editor, /@click="moveEntry\(-1\)"/)
  assert.match(editor, /@click="moveEntry\(1\)"/)
  assert.match(editor, /v-model="basics\.summaryHtml"/)
  assert.doesNotMatch(editor, /\|\| true/)

  // 顺序直接写回 draft，保存时仍持久化完整结构
  const ai = await read('src/views/AiView.vue')
  assert.match(ai, /'education', 'work', 'projects', 'skills', 'honors', 'sections'/)
})

test('项目经历提供日期区间与富文本详情编辑区', async () => {
  const editor = await read('src/components/ResumeEditor.vue')
  assert.match(editor, /项目名称/)
  assert.match(editor, /参与角色/)
  assert.match(editor, /开始时间/)
  assert.match(editor, /结束时间/)
  assert.match(editor, /至今/)
  assert.match(editor, /<ResumeRichTextEditor v-model="current\.contentHtml"/)

  const richText = await read('src/components/ResumeRichTextEditor.vue')
  assert.match(richText, /contenteditable/)
  for (const command of ['undo', 'redo', 'foreColor', 'bold', 'italic', 'underline', 'insertOrderedList', 'insertUnorderedList', 'justifyLeft', 'justifyCenter', 'justifyRight', 'insertImage', 'createLink', 'removeFormat']) {
    assert.match(richText, new RegExp(`['"]${command}['"]`), command)
  }

  const sheet = await read('src/components/ResumeSheet.vue')
  assert.match(sheet, /v-html="safeProjectHtml\(item\)"/)
})

test('富文本色板保留选区并覆盖所有内容模块', async () => {
  const richText = await read('src/components/ResumeRichTextEditor.vue')
  assert.match(richText, /COLOR_PALETTE/)
  assert.match(richText, /class="rrt-palette"/)
  assert.match(richText, /cloneRange\(\)/)
  assert.match(richText, /restoreSelection/)
  assert.match(richText, /applyColor/)
  assert.doesNotMatch(richText, /input type="color"/)

  const editor = await read('src/components/ResumeEditor.vue')
  for (const model of [
    'basics.summaryHtml',
    'current.detailHtml',
    'current.contentHtml',
    'items[currentIndex]'
  ]) {
    assert.match(editor, new RegExp(`ResumeRichTextEditor[^>]+${model.replace(/[.\[\]]/g, '\\$&')}`), model)
  }

  const sheet = await read('src/components/ResumeSheet.vue')
  for (const name of ['safeSummaryHtml', 'safeEducationHtml', 'safeWorkHtml', 'safeProjectHtml', 'safeListItemHtml']) {
    assert.match(sheet, new RegExp(name), name)
  }
})

test('从 PDF 导入的简历可以原版复刻版式与头像', async () => {
  // 复刻模板不进常规下拉框，只能通过 isResumeTemplate 白名单被认出来，
  // 否则存进 content_json 后重开会被 findResumeTemplate 退回到默认模板。
  const templates = await read('src/components/resumeTemplates.js')
  assert.match(templates, /id: 'replica'/)
  assert.match(templates, /id === REPLICA_TEMPLATE\.id/)
  assert.doesNotMatch(templates, /RESUME_TEMPLATES = \[[^\]]*'replica'/)

  // 版式变量全部走 CSS 自定义属性，颜色必须在前端再校验一次
  const sheet = await read('src/components/ResumeSheet.vue')
  assert.match(sheet, /avatar: \{ type: String/)
  assert.match(sheet, /style: \{ type: Object/)
  assert.match(sheet, /class="sheet-avatar"/)
  assert.match(sheet, /--sheet-font-size/)
  assert.match(sheet, /#\[0-9a-fA-F\]\{6\}/)

  const css = await read('src/assets/resume-templates.css')
  assert.match(css, /\.resume-sheet\.tpl-replica/)
  assert.match(css, /\.resume-sheet\.tpl-replica\.has-header-band \.sheet-header/)

  // 排版页：有版式数据时自动选复刻模板，并且能换头像、改强调色
  const ai = await read('src/views/AiView.vue')
  assert.match(ai, /const canReplica = computed/)
  assert.match(ai, /:before-upload="onAvatarPick"/)
  assert.match(ai, /@change="onAccentChange"/)
  assert.match(ai, /REPLICA_TEMPLATE\.id/)

  // 保存时必须把头像和版式带回去，否则排版一次就把导入的数据覆盖没了
  assert.match(ai, /avatar: structure\.avatar \|\| ''/)
  assert.match(ai, /styleJson: structure\.style \? JSON\.stringify\(structure\.style\) : ''/)

  // 导入弹窗要告诉用户读到了什么
  const resume = await read('src/views/ResumeView.vue')
  assert.match(resume, /class="import-style-tip"/)
  assert.match(resume, /styleJson: data\.style \? JSON\.stringify\(data\.style\) : ''/)
})

test('打印导出与预览共用同一套 A4 盒模型', async () => {
  const sheet = await read('src/components/ResumeSheet.vue')
  const print = sheet.slice(sheet.indexOf('@media print'))

  // 打印态必须锁定 A4 宽：写成 100% 会跟着浏览器窗口变成 1600px，版式整体走形
  assert.match(print, /width: 210mm !important/)
  assert.doesNotMatch(print, /width: 100% !important/)

  // 模板自身的 padding 是页边距来源，清零会把页眉的负边距出血一并做没
  assert.doesNotMatch(print, /padding: 0 !important/)

  // 页边距交给模板 padding，@page 再留一份会叠成双份
  assert.match(sheet, /@page\s*\{[^}]*margin: 0;/)

  // 深色页眉、徽章、强调色全靠背景色，默认的 economy 模式会把它们丢光
  assert.match(print, /-webkit-print-color-adjust: exact/)
  assert.match(print, /print-color-adjust: exact/)

  // 分页后每页都要补回上下内边距，否则续页文字顶到纸边
  assert.match(print, /box-decoration-break: clone/)
})
test('AI 出题工具栏为每个输入项标注用途', async () => {
  const ai = await read('src/views/AiView.vue')

  for (const caption of ['参考职位', '参考简历', '出题范围', '难度', '每个分类题数', '生成后']) {
    assert.match(ai, new RegExp(`<span class="field-caption">${caption}</span>`), caption)
  }

  // 必须说明「题目数量」是怎么算出来的，否则用户会以为调了没效果
  assert.match(ai, /题目总数 = 分类数 × 每个分类题数/)
  assert.match(ai, /不选则由 AI 按岗位决定/)
})

const formControls = [
  'el-input', 'el-select', 'el-date-picker', 'el-input-number',
  'el-cascader', 'el-autocomplete', 'el-tree-select'
]

/** 收集某个 .vue 文件里没有文案的表单控件（没有 placeholder，也没有可见标签） */
function findUndocumentedControls(source, file) {
  const controlRe = new RegExp(`<(${formControls.join('|')})\\b[^>]*?/?>`, 'g')
  const undocumented = []
  let match
  while ((match = controlRe.exec(source)) !== null) {
    const tag = match[0]

    // 往回多看两行，覆盖 <el-form-item label="..."> 换行包裹控件的写法
    let cursor = source.lastIndexOf('\n', match.index) + 1
    for (let hop = 0; hop < 2 && cursor > 0; hop += 1) {
      cursor = source.lastIndexOf('\n', cursor - 2) + 1
    }
    const context = source.slice(cursor, match.index + tag.length)

    const documented = /placeholder=/.test(tag)
      || /label=/.test(context)
      || /field-caption/.test(context)
      || /:model-value=/.test(tag)
      || /\bdisabled\b/.test(tag)

    if (!documented) {
      const line = source.slice(0, match.index).split('\n').length
      undocumented.push(`${file}:${line} ${match[1]}`)
    }
  }
  return undocumented
}

test('表单控件都带有占位提示或可见标签', async () => {
  const files = [
    'src/App.vue',
    'src/components/AppLayout.vue',
    'src/views/AiView.vue',
    'src/views/ApplicationView.vue',
    'src/views/BoardView.vue',
    'src/views/CompanyView.vue',
    'src/views/DashboardView.vue',
    'src/views/InterviewView.vue',
    'src/views/JobView.vue',
    'src/views/LoginView.vue',
    'src/views/ProfileView.vue',
    'src/views/QuestionView.vue',
    'src/views/ResumeView.vue'
  ]

  const undocumented = []
  for (const file of files) {
    undocumented.push(...findUndocumentedControls(await read(file), file))
  }
  assert.deepEqual(undocumented, [], `这些控件缺少提示文案：\n${undocumented.join('\n')}`)
})
