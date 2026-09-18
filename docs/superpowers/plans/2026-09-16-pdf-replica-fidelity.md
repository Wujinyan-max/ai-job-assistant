# PDF 原版复刻保真修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让“原版复刻”准确还原用户 PDF 的左侧竖向时间轴、圆形分区节点、单栏专业技能，以及只对原 PDF 中实际为红色的关键词进行局部高亮。

**Architecture:** 后端继续由 `ResumeStyleExtractor` 负责从 PDF 渲染像素和文字坐标中提取纯样式数据，扩展 `ResumeStyleVO` 保存轨道参数、技能栏数和强调词列表。前端使用安全的纯文本切分器渲染局部高亮，并由 `ResumeSheet` 根据样式字段选择时间轴版式；不使用 `v-html`，不把用户 PDF 内容当 HTML。

**Tech Stack:** Java 17、Spring Boot 3.2、PDFBox 3、Vue 3、Element Plus、Node test runner、CSS A4 打印样式。

**Spec:** 本计划即为已确认的短设计；源文件为 `C:\Users\wujinyan\Desktop\吴锦炎的个人简历.pdf`。

## Global Constraints

- 保留工作区内既有未提交修改；不要 reset、checkout 或覆盖用户改动。
- 源 PDF 含个人信息，禁止复制进仓库或提交；自动化测试必须使用合成 PDF。
- 只在“原版复刻”模板应用轨道、栏数和强调词，不影响其他模板。
- 所有 PDF 文本必须通过 Vue 文本插值输出，禁止 `v-html`。
- 旧 `style_json` 必须可反序列化；新增字段缺失时安全降级。
- 完成后必须重新打包并重启 8088 后端；当前运行中的 JAR 不包含本计划的半成品改动。

## 已确认的源 PDF 事实

- 共 3 页，单页约 `615 × 870 pt`。
- 首页深色页眉高度约 `197.25 pt`，占比约 `0.226`。
- 左侧轨道中心 `x = 37.5 pt`，线宽约 `0.75 pt`。
- 圆形节点尺寸约 `22.5 pt`，即 `7.9 mm`，节点内有教育、工作、技能、项目、荣誉、个人图标。
- 正文左边界约 `53.25–67.5 pt`，轨道相对正文左边界向左约 `5.6–10.6 mm`。
- 正文字号约 `9.75 pt`，标题约 `13.5 pt`。
- 强调色为 `#e60000`，只作用于部分关键词；普通正文为灰黑色。
- “专业技能”原版是单栏长列表，不是双栏。
- 数据库中简历 `id=19` 的旧 `style_json` 只有单一 `accentColor`，`sectionBadge=false`，没有轨道、高亮词和栏数字段，因此旧记录不会自动呈现新效果，完成后需重新导入源 PDF或显式回填样式。

## 当前半成品状态（接手前必读）

以下文件在当前工作区是未跟踪或未提交状态，不能当作已完成：

- `backend/src/main/java/com/jobassistant/vo/ResumeStyleVO.java`
  - 已加入 `sectionRail`、`sectionRailOffsetMm`、`skillsColumns`、`accentTerms` 字段及基础清洗。
- `backend/src/main/java/com/jobassistant/service/impl/ResumeStyleExtractor.java`
  - 已加入最多 5 页分析、竖轨检测、基于渲染像素和文字坐标提取强调词的初版实现。
  - 合成 PDF 定向测试已经通过，但尚未对真实源 PDF 打印/核对提取结果。
  - `hasAccentPixel` 当前用同一个缩放值换算 X/Y；建议把 `pageHeight` 加进 `TextFacts` 后分别计算 `scaleX`、`scaleY`。
- `backend/src/test/java/com/jobassistant/service/impl/ResumeStyleExtractorTest.java`
  - 已加入 `extractsRailColumnsAndAccentTerms` 合成 PDF 测试，定向运行曾通过。
- `frontend/src/utils/resumeHighlight.js`
  - 已创建 `splitHighlightedText(text, terms)`。
- `frontend/src/components/HighlightedText.vue`
  - 已创建安全的局部高亮组件。
- `frontend/tests/resume-highlight.test.mjs`
  - 已先观察到模块缺失的 RED；实现模块后尚未重新运行验证 GREEN。

尚未完成：`ResumeSheet.vue` 集成、轨道/节点 CSS、移除整行标红、真实 PDF 复测、全量测试、打包、重启及数据库样式刷新。

---

### Task 1: 收紧后端样式契约与坐标换算

**Files:**
- Modify: `backend/src/main/java/com/jobassistant/vo/ResumeStyleVO.java`
- Modify: `backend/src/main/java/com/jobassistant/service/impl/ResumeStyleExtractor.java`
- Test: `backend/src/test/java/com/jobassistant/service/impl/ResumeStyleExtractorTest.java`

**Interfaces:**
- Produces: `ResumeStyleVO.sectionRail(): boolean`
- Produces: `ResumeStyleVO.sectionRailOffsetMm(): double`
- Produces: `ResumeStyleVO.skillsColumns(): int`
- Produces: `ResumeStyleVO.accentTerms(): List<String>`

- [ ] **Step 1: 检查半成品构造器兼容性**

确认 `ResumeStyleVO.empty()` 和测试中的所有 `new ResumeStyleVO(...)` 都包含新增字段；构造器规则必须是：轨道偏移夹在 `0..30mm`，栏数只允许 `1/2`，强调词去空、去重、限制最多 120 个且每项最多 200 字。

- [ ] **Step 2: 修正文字坐标的纵向缩放**

将 `TextFacts` 扩展为同时保存 `pageWidth`、`pageHeight`：

```java
private record TextFacts(
        List<TextSpan> spans,
        float maxFontSize,
        float bodyFontSize,
        float pageWidth,
        float pageHeight
) { }
```

在 `hasAccentPixel` 中分别计算：

```java
double scaleX = image.getWidth() / (double) text.pageWidth();
double scaleY = image.getHeight() / (double) text.pageHeight();
```

不要继续使用 `image.getHeight() / image.getHeight() * scaleX`。

- [ ] **Step 3: 强化合成 PDF 测试断言**

`extractsRailColumnsAndAccentTerms` 必须断言：

```java
assertThat(style.sectionRail()).isTrue();
assertThat(style.sectionRailOffsetMm()).isBetween(3d, 12d);
assertThat(style.skillsColumns()).isEqualTo(1);
assertThat(style.accentTerms()).contains("Redis");
assertThat(style.accentTerms()).noneMatch(term -> term.contains("Skilled in"));
```

- [ ] **Step 4: 运行后端定向测试**

Run:

```powershell
$env:JAVA_HOME='C:\Users\wujinyan\.jdks\corretto-18.0.2'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -o -Dtest=ResumeStyleExtractorTest test
```

Expected: `7` 个样式测试全部通过（若测试数因后续增加而变化，以 `0 failures` 为准）。

---

### Task 2: 验证真实 PDF 的提取结果但不提交隐私文件

**Files:**
- Read only: `C:\Users\wujinyan\Desktop\吴锦炎的个人简历.pdf`
- Modify only if evidence requires: `backend/src/main/java/com/jobassistant/service/impl/ResumeStyleExtractor.java`

**Interfaces:**
- Consumes: Task 1 的 `ResumeStyleExtractor.extract(byte[])`
- Produces: 一份控制台核对结果，不产生仓库内 PDF 副本

- [ ] **Step 1: 临时调用提取器读取源 PDF**

使用临时 JUnit、JShell 或受控调试入口调用：

```java
byte[] pdf = Files.readAllBytes(Path.of("C:\\Users\\wujinyan\\Desktop\\吴锦炎的个人简历.pdf"));
ResumeStyleVO style = ResumeStyleExtractor.extract(pdf).style();
System.out.println(new ObjectMapper().writeValueAsString(style));
```

临时代码不得提交。

- [ ] **Step 2: 核对真实输出**

至少满足：

```text
accentColor = #e60000
sectionRail = true
sectionRailOffsetMm ≈ 5.6（允许 3..12）
skillsColumns = 1
accentTerms 包含 Redis / Jenkins / Selenium 等源 PDF 中实际红色词
accentTerms 不包含完整技能段落或普通灰色正文
```

- [ ] **Step 3: 若真实词组被错误拼接，调整分组边界**

同一词组只允许在“同页、同一基线、字符间距不超过约 1.2 个字号”时合并；遇到普通颜色字符必须立即结束当前词组。修正后重新执行 Task 1 的合成测试和本步骤真实核对。

---

### Task 3: 完成安全的局部高亮组件

**Files:**
- Create/finish: `frontend/src/utils/resumeHighlight.js`
- Create/finish: `frontend/src/components/HighlightedText.vue`
- Test: `frontend/tests/resume-highlight.test.mjs`

**Interfaces:**
- Produces: `splitHighlightedText(text: string, terms: string[]): {text: string, highlighted: boolean}[]`
- Produces: `<HighlightedText :text="..." :terms="..." />`

- [ ] **Step 1: 运行已有前端高亮测试**

Run:

```powershell
cd frontend
node --test tests/resume-highlight.test.mjs
```

Expected: 2 tests pass。若失败，修复 `splitHighlightedText`，要求重叠词优先匹配更长项。

- [ ] **Step 2: 确认组件不使用 HTML 注入**

组件必须使用：

```vue
<span :class="{ 'resume-highlight': part.highlighted }">{{ part.text }}</span>
```

不得出现 `v-html`。高亮样式：

```css
.resume-highlight {
  color: var(--sheet-accent);
  font-weight: 700;
}
```

---

### Task 4: 在 ResumeSheet 集成局部高亮

**Files:**
- Modify: `frontend/src/components/ResumeSheet.vue`
- Consume: `frontend/src/components/HighlightedText.vue`

**Interfaces:**
- Consumes: `props.style.accentTerms`
- Consumes: `<HighlightedText>` from Task 3

- [ ] **Step 1: 导入组件并建立只读高亮词**

```js
import HighlightedText from './HighlightedText.vue'

const accentTerms = computed(() =>
  isReplica.value && Array.isArray(props.style?.accentTerms)
    ? props.style.accentTerms
    : []
)
```

- [ ] **Step 2: 替换非编辑态的正文插值**

至少覆盖：个人简介、教育详情、工作 bullets、项目 summary、项目 bullets、技能、荣誉、自定义板块条目。例如：

```vue
<template v-else>
  <HighlightedText :text="bullet" :terms="accentTerms" />
</template>
```

编辑态仍使用 textarea，不做富文本编辑。

- [ ] **Step 3: 移除整行标红**

从 `frontend/src/assets/resume-templates.css` 删除或覆盖：

```css
.resume-sheet.tpl-replica .sheet-skills li,
.resume-sheet.tpl-replica .sheet-item-meta {
  color: var(--sheet-accent);
}
```

同时不要把所有列表 marker 统一设成红色；原 PDF 的编号和普通项目符号为灰黑色。红色只能来自 `<HighlightedText>`。

---

### Task 5: 还原左侧轨道、圆形节点和单栏技能

**Files:**
- Modify: `frontend/src/components/ResumeSheet.vue`
- Modify: `frontend/src/assets/resume-templates.css`

**Interfaces:**
- Consumes: `style.sectionRail`、`style.sectionRailOffsetMm`、`style.skillsColumns`

- [ ] **Step 1: 注入模板类和 CSS 变量**

在 `sheetClasses` 加：

```js
'has-section-rail': isReplica.value && !!props.style?.sectionRail
```

在 `styleVars` 加：

```js
if (Number.isFinite(style.sectionRailOffsetMm) && style.sectionRailOffsetMm > 0) {
  vars['--sheet-rail-offset'] = `${style.sectionRailOffsetMm}mm`
}
vars['--sheet-skills-columns'] = style.skillsColumns === 2 ? 2 : 1
```

- [ ] **Step 2: 给六个内置标题加入圆形节点**

教育、工作、项目、技能、荣誉、个人简介分别使用 Element Plus 的学校、手提箱、文件夹/项目、工具、奖章、用户图标。节点结构统一为：

```vue
<span v-if="isReplica && props.style?.sectionBadge" class="sheet-section-node" aria-hidden="true">
  <el-icon><School /></el-icon>
</span>
```

不要继续使用 `.sheet-title::before` 的无内容黑色方块。

- [ ] **Step 3: 绘制分段轨道**

每个 `.sheet-section` 使用相同的负向偏移定位轨道，避免增加会破坏其他模板网格的外层 wrapper：

```css
.resume-sheet.tpl-replica.has-section-rail .sheet-section {
  position: relative;
}

.resume-sheet.tpl-replica.has-section-rail .sheet-section::before {
  content: '';
  position: absolute;
  left: calc(var(--sheet-rail-offset, 6mm) * -1);
  top: 4mm;
  bottom: -14px;
  width: .3mm;
  background: var(--sheet-band);
}
```

最后一个可见板块应让轨道终止在节点中心，避免继续拖到空白页底。节点建议 `7.9mm × 7.9mm`、圆形、深色底、白色图标，中心与轨道重合。

- [ ] **Step 4: 专业技能按提取栏数展示**

```css
.resume-sheet.tpl-replica .sheet-skills {
  columns: var(--sheet-skills-columns, 1);
  column-gap: 24px;
}
```

本源 PDF 应得到单栏。

- [ ] **Step 5: 构建验证**

Run:

```powershell
cd frontend
npm run build
```

Expected: Vite build exit 0；现有大 chunk warning 可记录但不作为失败。

---

### Task 6: 全量验证、部署与数据刷新

**Files:**
- No new source files
- Runtime artifact: `backend/target/job-assistant-1.0.0.jar`

- [ ] **Step 1: 后端全量测试**

```powershell
cd backend
$env:JAVA_HOME='C:\Users\wujinyan\.jdks\corretto-18.0.2'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -o test
```

Expected: 0 failures / 0 errors。

- [ ] **Step 2: 前端相关测试与全量 UI 测试**

```powershell
cd frontend
node --test tests/resume-highlight.test.mjs
npm run test:ui
```

注意：全量 UI 套件此前已有两条与本功能无关的失效断言（侧栏仍期待 196px、首页仍期待“今日专注”）；必须区分既有失败与新增失败。

- [ ] **Step 3: 重打包并重启 8088**

先确认 8088 的命令行包含 `job-assistant-1.0.0.jar`，再停止该精确 PID；不要停止 8080 的 fitness-tracker。

```powershell
cd backend
mvn -o -DskipTests package
```

随后用 JDK 18、隐藏窗口启动新 JAR，并轮询：

```text
http://127.0.0.1:8088/api/doc.html
```

Expected: HTTP 200，监听 PID 的启动时间晚于 JAR 修改时间。

- [ ] **Step 4: 刷新旧样式数据**

旧简历 `id=19` 的 `style_json` 不包含新增字段。优先通过 UI 重新导入 `C:\Users\wujinyan\Desktop\吴锦炎的个人简历.pdf`，不要直接覆盖或删除旧简历；如需回填旧记录，必须先征得用户同意。

- [ ] **Step 5: 视觉验收**

在“AI 助手 → 简历排版 → 原版复刻”核对：

1. 左侧为细竖线和圆形图标节点，不是黑色方块。
2. 专业技能为单栏。
3. `Python+Selenium...`、`Redis`、`Jenkins` 等源 PDF 红词保持红色加粗。
4. 同一条技能中的普通文字保持灰黑色。
5. 打印预览中轨道和节点没有被裁切，分页后仍可读。

- [ ] **Step 6: 最终差异检查**

```powershell
git diff --check
git status --short
```

确认没有把源 PDF、渲染 PNG、数据库导出或个人信息测试样本加入仓库。

## 自检结论

- 覆盖了用户指出的黑色方块、单/双栏错误和整行标红三个问题。
- 后端字段名与前端消费名保持一致：`sectionRail`、`sectionRailOffsetMm`、`skillsColumns`、`accentTerms`。
- 没有要求数据库迁移，样式继续存储在 `style_json`。
- 没有提交真实 PDF；真实文件只用于本地验证。
- 没有自动删除之前生成的错误简历版本。
