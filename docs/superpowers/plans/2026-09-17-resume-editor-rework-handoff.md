# 交接文档：简历编辑器重做（全民简历式布局）

> 生成时间：2026-09-17
> 来源会话：`ai-job-assistant / kimi k3`（thread `01a0a98a-6495-7622-b9ca-49400ae2effa`）
> 中断原因：模型输出工具调用时被截断（非代码问题），见文末「为什么卡住」

## 一、这次要解决什么

用户原话：

> 我对简历模板那块编辑不满意，你具体是去看看全民简历这个网站是怎么样的，看看能不能这样子实现出来

> 开始动手吧

也就是：**当前「AI 助手 → 简历排版 → 编辑模板」的编辑体验太难用，要按「全民简历」（www.qmjianli.com）的做法重做。**

## 二、问题现状：为什么现在的编辑难用

现在是在 **A4 纸面上直接戳输入框**（WYSIWYG 内联编辑）：

- 入口：`frontend/src/views/AiView.vue` 第 288 行「编辑模板」按钮
- 实现：`frontend/src/components/ResumeSheet.vue`（813 行）用 `editable` 属性切换，把标题、学校、专业、时间、正文全渲染成 `<input>` / `<textarea>`，直接铺在 A4 版式里
  - 关键类名：`sheet-input`、`sheet-area`、`sheet-add`、`sheet-remove`、`sheet-handle`（拖拽排序）
- 状态：`AiView.vue` 里 `structure.editing` / `structure.draft`
  - `startEditStructure()`（933 行）复制 `structure.result` 到 `draft`
  - `saveStructureEdit()`（1004 行）调 `persistStructure(draft)` 回写
  - `cancelEditStructure()`（943 行）放弃

**为什么难用（已确认）**：纸面宽度只有 A4 那么窄，输入框被版式挤得很小；字段没有标签，只能靠 placeholder 猜；列表型内容（教育/工作/项目/技能）全部平铺，条目多了根本找不到；排序要靠拖拽小把手。版式一变（比如切到 sidebar 模板）输入框位置还会被重新排布。

## 三、目标设计：全民简历的做法

已实地扒过 `www.qmjianli.com/cv/edit/`，结构是：

```
┌─────────────────────────────────────────────┐
│  上方：A4 实时预览（只读，不承载输入）        │
└─────────────────────────────────────────────┘
┌─────────────────────────────────────────────┐
│  中间：模块页签                              │
│  基本信息 | 教育背景 | 工作经历 | 项目经验    │
│  技能特长 | 荣誉证书 | 自我评价 ...           │
└─────────────────────────────────────────────┘
┌─────────────────────────────────────────────┐
│  下方：当前模块的表单                        │
│   基本信息 → 字段平铺成网格                   │
│   列表型模块 → 左边条目列表 / 右边字段表单     │
└─────────────────────────────────────────────
```

抓到的参考信息（2026-09-16 实测）：

- 页签：`基本信息 / 求职意向 / 报考信息 / 教育背景 / 工作经历 / 技能特长 / 项目经验 / 实习经验 / 校园经历 / 荣誉证书 / 自我评价 / 兴趣爱好 / 自定义...`
- 顶部有「间距设置 / 字体语言 / 皮肤设置 / 标题设置 / 封面设置 / 自存信 / 更换模板」「收藏 / 导入 / 分享 / 发邮箱 / 下载」
- 提示文案：「填写的信息会自动排版在简历上，不需要的信息可以选择不填或删除内容。」→ **空模块可以不填，不强制**
- 各模块字段（用 placeholder 抓的）：
  - 基本信息：输入您的姓名 / 请选择性别 / 选择年月 / 请选择工作年限 / 输入联系电话 / 输入联系邮箱 / 请选择婚姻状况 / 身高 / 体重 / 请输入民族 / 请输入籍贯 / 请选择政治面貌
  - 教育背景：请输入学校名称 / 请输入所学专业 / 请选择学历 / 入学年月 / 毕业年月
  - 工作经历、项目经验：请输入公司名称 / 请输入职位名称 / 入职时间 / 离职时间
  - 技能特长、荣誉证书：请输入自定义名称（一条一句话）
- 右上角还有「简历智能助手」：一键优化简历 / AI 代写简历 / 深度诊断简历 / 人工优化

参考截图：`C:\Users\wujinyan\AppData\Local\Temp\codex-clipboard-87ec7d55-531b-4b4e-a79b-b67c05cc69dd.png`

## 四、已经写了一半的代码（重要）

**断点就在这一步**：正要新建 `frontend/src/components/ResumeEditor.vue`，补丁写到 7457 字符时 JSON 被截断，**文件没有落盘**。

结论：**`frontend/src/components/ResumeEditor.vue` 目前不存在，也没有任何地方引用它**（已用 `rg` 确认）。下面的模板内容是从被截断的补丁里恢复出来的，可以直接接着用：

```vue
<template>
  <div class="resume-editor">
    <!-- 模块页签。填过内容的模块带个数字角标，一眼能看出哪块还空着 -->
    <div class="re-tabs">
      <button v-for="item in MODULES" :key="item.key" type="button" class="re-tab"
              :class="{ 'is-active': active === item.key }" @click="select(item.key)">
        {{ item.label }}
        <span v-if="countOf(item.key)" class="re-count">{{ countOf(item.key) }}</span>
      </button>
    </div>

    <!-- 基本信息：字段平铺，没有列表 -->
    <div v-if="active === 'basics'" class="re-panel">
      <div class="re-grid">
        <label class="re-field">
          <span class="re-label">姓名</span>
          <el-input v-model="basics.name" placeholder="例如：张三" />
        </label>
        <label class="re-field">
          <span class="re-label">求职意向</span>
          <el-input v-model="basics.label" placeholder="例如：Java 后端开发" />
        </label>
        <label class="re-field">
          <span class="re-label">联系电话</span>
          <el-input v-model="basics.phone" placeholder="例如：13800000000" />
        </label>
        <label class="re-field">
          <span class="re-label">联系邮箱</span>
          <el-input v-model="basics.email" placeholder="例如：zhangsan@example.com" />
        </label>
        <label class="re-field">
          <span class="re-label">所在城市</span>
          <el-input v-model="basics.city" placeholder="例如：广州" />
        </label>
        <label class="re-field">
          <span class="re-label">工作年限</span>
          <el-input v-model="basics.workYears" placeholder="例如：3 年" />
        </label>
      </div>
      <label class="re-field re-field-wide">
        <span class="re-label">个人简介</span>
        <el-input v-model="basics.summary" type="textarea" :rows="3"
                  placeholder="一句话说明你能做什么、做过什么，排版后显示在简历开头" />
      </label>
    </div>

    <!-- 列表型模块：左边挑条目，右边改字段 -->
    <div v-else class="re-panel re-split">
      <aside class="re-list">
        <button v-for="(item, index) in items" :key="index" type="button"
                class="re-list-item" :class="{ 'is-active': index === currentIndex }"
                @click="currentIndex = index">
          <strong>{{ primaryText(item) }}</strong>
          <span v-if="secondaryText(item)">{{ secondaryText(item) }}</span>
        </button>
        <button type="button" class="re-list-add" @click="addEntry">
          + 新增{{ activeModule.entryLabel }}
        </button>
      </aside>

      <div class="re-form">
        <el-empty v-if="!items.length" :image-size="80"
                  :description="`还没有内容，点左边「新增${activeModule.entryLabel}」加一条`" />

        <template v-else>
          <div class="re-form-head">
            <span class="re-form-title">{{ activeModule.label }} {{ currentIndex + 1 }}</span>
            <span class="re-form-actions">
              <el-button link :disabled="currentIndex === 0" @click="moveEntry(-1)">上移</el-button>
              <el-button link :disabled="currentIndex === items.length - 1" @click="moveEntry(1)">下移</el-button>
              <el-button link type="danger" @click="removeEntry">删除这条</el-button>
            </span>
          </div>

          <!-- 教育背景 -->
          <div v-if="active === 'education'" class="re-grid">
            <label class="re-field">
              <span class="re-label">学校名称</span>
              <el-input v-model="current.school" placeholder="例如：广东科技学院" />
            </label>
            <label class="re-field">
              <span class="re-label">所学专业</span>
              <el-input v-model="current.major" placeholder="例如：软件工程" />
            </label>
            <label class="re-field">
              <span class="re-label">学历</span>
              <el-select v-model="current.degree" clearable placeholder="请选择学历" style="width: 100%">
                <el-option v-for="item in DEGREES" :key="item" :label="item" :value="item" />
              </el-select>
            </label>
            <label class="re-field">
              <span class="re-label">起止时间</span>
              <el-input v-model="current.period" placeholder="例如：2022.09 - 2026.07" />
            </label>
            <label class="re-field re-field-wide">
              <span class="re-label">成绩 / 主修课程</span>
              <el-input v-model="current.detail" type="textarea" :rows="2"
                        placeholder="例如：GPA 3.6/4.0（专业前 5%），主修 Java、Spring Boot、MySQL" />
            </label>
          </div>

          <!-- 工作经历 -->
          <div v-else-if="active === 'work'" class="re-grid">
            <label class="re-field">
              <span class="re-label">公司名称</span>
              <el-input v-model="current.company" placeholder="例如：某某科技有限公司" />
            </label>
            <label class="re-field">
              <span class="re-label">职位名称</span>
              <el-input v-model="current.position" placeholder="例如：软件测试实习生" />
            </label>
            <label class="re-field re-field-wide">
              <span class="re-label">起止时间</span>
              <el-input v-model="current.period" placeholder="例如：2025.09 - 2026.01，在职就写「至今」" />
            </label>
          </div>

          <!-- 项目经历 -->
          <div v-else-if="active === 'projects'" class="re-grid">
            <label class="re-field">
              <span class="re-label">项目名称</span>
              <el-input v-model="current.name" placeholder="例如：电商后台管理系统" />
            </label>
            <label class="re-field">
              <span class="re-label">担任角色</span>
              <el-input v-model="current.role" placeholder="例如：后端开发" />
            </label>
            <label class="re-field re-field-wide">
              <span class="re-label">起止时间</span>
              <el-input v-model="current.period" placeholder="例如：2024.03 - 2024.09" />
            </label>
            <label class="re-field re-field-wide">
              <span class="re-label">项目描述 / 技术架构</span>
              <el-input v-model="current.summary" type="textarea" :rows="2"
                        placeholder="例如：Spring Boot + MySQL + Redis，日活 2 万" />
            </label>
          </div>

          <!-- 技能特长 / 荣誉证书：一条就是一句话 -->
          <div v-else-if="active === 'skills' || active === 'honors' || true" class="re-grid">
            <label class="re-field re-field-wide">
              <span class="re-label">{{ activeModule.entryLabel }}</span>
              <el-input v-model="items[currentIndex]" type="textarea" :rows="3"
                        :placeholder="activeModule.itemPlaceholder" />
            </label>
          </div>
        </template>
      </div>
    </div>
```

**注意**：这段只有 `<template>`，`<script setup>` 和 `<style>` 都还没写。而且最后那个 `|| true` 是草稿遗留（会让所有非 education/work/projects 的模块都走这个分支），要改成显式判断。

## 五、下一步要做的（建议顺序）

### 1. 补完 `ResumeEditor.vue` 的 script 和 style

需要实现：

- `MODULES`：模块清单，含 `key` / `label` / `entryLabel` / `itemPlaceholder`
  - 建议：`basics / education / work / projects / skills / honors / summary`
- `active`：当前页签，默认 `basics`
- `props`：`structure`（结构化简历对象）+ `editable`（是否可编辑）
- `items`：当前模块的列表（`education` / `work` / `projects` / `skills` / `honors`）
  - 注意 `skills` / `honors` 是**字符串数组**，其余是对象数组
- `current` / `currentIndex`：当前选中条目
- `primaryText(item)` / `secondaryText(item)`：左侧列表的主副标题
  - 教育：学校 / 专业·学历；工作：公司 / 职位；项目：项目名 / 角色；技能：直接显示字符串（截断）
- `countOf(key)`：页签角标数字
- `addEntry()` / `removeEntry()` / `moveEntry(delta)` / `select(key)`
- `DEGREES`：学历选项（与后端 `ResumeStructureParser.DEGREE` 保持一致：博士后/博士/硕士/研究生/本科/学士/大专/专科/中专/高中）
- **双向绑定**：直接改 `props.structure` 里的数组/对象（沿用现在 `ResumeSheet` 的做法，父组件用 `draft` 承接），不要另拷一份，否则保存时会丢改
- 样式前缀统一用 `re-`，不要污染 `resume-templates.css` 里已有的 `sheet-*` 类

### 2. 接进 `AiView.vue`

- 编辑态把 `ResumeSheet` 换成「只读预览 + ResumeEditor」上下排布
  - 现在：`<ResumeSheet :structure="structure.editing ? structure.draft : structure.result" :editable="structure.editing" ... />`（338 行）
  - 目标：`ResumeSheet` 恒定 `:editable="false"`；编辑态在其下方渲染 `<ResumeEditor :structure="structure.draft" />`
- 「点纸上的文字就能改」的提示文案（309 行）要改掉

### 3. 清理 `ResumeSheet.vue` 的内联编辑

- 813 行里大量 `v-if="editable"` 分支（`sheet-input` / `sheet-add` / `sheet-remove` / `sheet-handle`）在新方案下不再需要
- `resume-templates.css` 里对应的样式也要一并清
- **风险**：动刀前先确认 `resume-templates.css` 里还有哪些 `sheet-*` 类是被**只读预览**用到的（如 `sheet-title-bar` / `sheet-section` / `sheet-item-meta`），别误删

### 4. 补测试

现有前端测试是源码断言式（`node --test`，读 `.vue` 源码做 `assert.match`），沿用同样的风格加到 `frontend/tests/prototype-ui.test.mjs` 或新建一个：

```powershell
cd frontend
node --test tests/*.test.mjs
```

### 5. 验证

```powershell
cd frontend
npm run build
npm run test:ui
```

浏览器实测：进「AI 助手 → 简历排版 → 编辑模板」，确认上方预览只读、下方页码可切、条目可增删排序、保存后回写正确。

## 六、为什么卡住（避免重蹈覆辙）

报错原文：

```
CC Switch local proxy failed while handling Codex endpoint /responses.
Provider: 公司01; model: aliy/deepseek-v4.1-flash;
cause: 无效的请求: Invalid function_call arguments for 'exec_command':
EOF while parsing a string at line 1 column 8143
```

- **不是项目代码的问题**，也不是 CC Switch 本身坏了。
- 是模型吐出的 `exec_command` 参数（里面内嵌了一个 7.5KB 的 `apply_patch` 补丁）**在 8143 字符处被截断**，JSON 字符串没闭合 → 解析失败 → 整个工具调用被拒。
- **规避**：大文件分多次小补丁写。比如 `ResumeEditor.vue` 可以拆成「先写 template 骨架」→「追加 script」→「追加 style」三次 `apply_patch`，每次控制在 2–3KB。

另外，这个会话里还踩过 **PowerShell 原生参数传递吞双引号**的坑，当时的解法是在补丁脚本开头加：

```powershell
$PSNativeCommandArgumentPassing='Standard'
```

## 七、顺带未完成的两项（上一个会话遗留，与本次无强关联）

来自会话 `01a0a43d-...`（「优化一下前端界面」）的收尾说明：

1. **职位管理页「AI 解析」和面试管理页「一键入题库」没有加「任务进行中」提示**，因为那两处是弹窗式短操作，当时判断切页风险小。要的话可以补。
2. **简历优化 / 简历排版 / 出题切页后找不回结果**，目前只做了「提醒你别切」（`frontend/src/utils/aiRunningNotice.js`），没做「切了也能找回」。可以考虑给这三个也加历史回填。

## 八、相关文件清单

**要改的**
- `frontend/src/components/ResumeEditor.vue`（新建，上面已给出 template 草稿）
- `frontend/src/views/AiView.vue`（接入编辑器，去掉内联编辑）
- `frontend/src/components/ResumeSheet.vue`（拆掉内联编辑）
- `frontend/src/assets/resume-templates.css`（清理 `sheet-input` 等编辑态样式）
- `frontend/tests/prototype-ui.test.mjs`（补断言）

**只读参考**
- `frontend/src/components/resumeTemplates.js`（模板清单、`REPLICA_TEMPLATE`）
- `frontend/src/utils/resumeHighlight.js`、`frontend/src/components/HighlightedText.vue`（局部高亮，复用）
- `docs/superpowers/plans/2026-09-16-pdf-replica-fidelity.md`（PDF 复刻计划，**已完成**）

**参考截图**
- `C:\Users\wujinyan\AppData\Local\Temp\codex-clipboard-87ec7d55-531b-4b4e-a79b-b67c05cc69dd.png`

## 九、当前仓库状态速览

- 工作区有大量未提交改动（`git status --short` 约 177 个文件），**含用户既有改动，不要 reset / checkout / 覆盖**。
- `git diff --check` 无空白错误（只有 CRLF 换行提示，可忽略）。
- 后端：`mvn -o test` = **115 passed / 0 failed**。
- 前端：`resume-highlight` 2/2 过、`ai-running-notice` 6/6 过；`prototype-ui` 有 **2 条已知既有失败**（与本次无关）：
  - `公共布局采用原型的紧凑工作台比例`：断言 `--sider-width: 196px`，实际 `frontend/src/assets/main.css:55` 是 `208px`
  - `首页包含原型中的问候与日期信息区`：断言 `今日专注`，但该模块已从首页移除
- 后端 8088 正在跑（PID 30308，JAR 时间 2026-09-16 18:45，**晚于** `ResumeStyleExtractor.java` 的 18:44，即最近一次后端改动已包含在运行版里）。
