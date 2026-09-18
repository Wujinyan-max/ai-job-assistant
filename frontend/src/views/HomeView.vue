<template>
  <div class="home">
    <!-- ======================== 顶栏 ======================== -->
    <header class="nav" :class="{ 'is-scrolled': scrolled }">
      <div class="nav-inner">
        <div class="nav-brand">
          <img class="brand-mark" src="@/assets/logo-mark.png" alt="职得 JobPath" />
          <span class="brand-name">职得 JobPath</span>
        </div>
        <nav class="nav-links">
          <a href="#features" @click.prevent="scrollTo('features')">功能</a>
          <a href="#ai" @click.prevent="scrollTo('ai')">AI 能力</a>
          <a href="#steps" @click.prevent="scrollTo('steps')">使用流程</a>
        </nav>
        <div class="nav-actions">
          <el-button text @click="goLogin">登录</el-button>
          <el-button type="primary" @click="goRegister">免费注册</el-button>
        </div>
      </div>
    </header>

    <!-- ======================== Hero ======================== -->
    <section class="hero">
      <div class="hero-inner">
        <div class="page-kicker">AI-Powered Job Hunt</div>
        <h1 class="hero-title">
          把找工作的每一步，<br />都放进同一个地方。
        </h1>
        <p class="hero-desc">
          公司、职位、简历、投递、面试、Offer —— 一条完整的求职闭环。
          接入大模型做 JD 解析、简历匹配与面试题生成，AI 结果落库可回看。
        </p>
        <div class="hero-actions">
          <el-button type="primary" size="large" @click="goRegister">
            免费开始
            <el-icon style="margin-left:4px"><Right /></el-icon>
          </el-button>
          <el-button size="large" text @click="scrollTo('features')">了解功能</el-button>
        </div>

        <!-- 界面预览 -->
        <div class="hero-preview">
          <div class="preview-frame">
            <div class="preview-bar">
              <span class="dot" /><span class="dot" /><span class="dot" />
              <span class="preview-url">jobpath.app/dashboard</span>
            </div>
            <div class="preview-body">
              <div class="pv-sider">
                <div class="pv-logo-row">
                  <img class="pv-logo-mark" src="@/assets/logo-mark.png" alt="" />
                  <span class="pv-logo-text">职得</span>
                </div>
                <div
                  v-for="item in ['数据看板', '求职看板', '投递记录', '职位管理', '公司管理', '简历管理', '面试管理', 'AI 助手', '面试题库']"
                  :key="item"
                  class="pv-menu-item"
                  :class="{ on: item === '数据看板', 'is-ai': item === 'AI 助手' }"
                >
                  <span v-if="item === 'AI 助手'" class="pv-ai-dot" />{{ item }}
                </div>
              </div>
              <div class="pv-main">
                <div class="pv-topbar">
                  <span class="pv-breadcrumb">工作台 / 数据看板</span>
                  <span class="pv-avatar">演</span>
                </div>
                <div class="pv-content">
                  <div class="pv-hero-row">
                    <div>
                      <div class="pv-greeting">晚上好，演示用户</div>
                      <div class="pv-date">9月15日 周二 · 继续向前</div>
                    </div>
                    <div class="pv-quote">"机会是留给准备充分的人。"</div>
                  </div>
                  <div class="pv-stats">
                    <div v-for="s in previewStats" :key="s.label" class="pv-stat">
                      <div class="pv-stat-label">{{ s.label }}</div>
                      <div class="pv-stat-num">{{ s.value }}</div>
                      <div class="pv-stat-sub">{{ s.sub }}</div>
                    </div>
                  </div>
                  <div class="pv-charts">
                    <div class="pv-chart-card">
                      <div class="pv-chart-title">近 30 天投递趋势</div>
                      <svg viewBox="0 0 300 90" preserveAspectRatio="none" class="pv-chart-svg">
                        <defs>
                          <linearGradient id="hg" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stop-color="#9c7c3c" stop-opacity="0.18" />
                            <stop offset="100%" stop-color="#9c7c3c" stop-opacity="0.01" />
                          </linearGradient>
                        </defs>
                        <path d="M0,78 C25,75 40,72 60,65 C80,58 95,62 115,55 C135,48 150,50 170,42 C190,34 210,36 230,26 C250,16 275,14 300,8 L300,90 L0,90 Z" fill="url(#hg)" />
                        <path d="M0,78 C25,75 40,72 60,65 C80,58 95,62 115,55 C135,48 150,50 170,42 C190,34 210,36 230,26 C250,16 275,14 300,8" fill="none" stroke="#9c7c3c" stroke-width="1.8" stroke-linecap="round" />
                      </svg>
                    </div>
                    <div class="pv-chart-card">
                      <div class="pv-chart-title">近期面试</div>
                      <div v-for="iv in previewInterviews" :key="iv.company" class="pv-interview">
                        <div class="pv-interview-dot" :style="{ background: iv.color }" />
                        <div class="pv-interview-info">
                          <div class="pv-interview-company">{{ iv.company }}</div>
                          <div class="pv-interview-job">{{ iv.job }}</div>
                        </div>
                        <div class="pv-interview-tag">{{ iv.tag }}</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ======================== 数据带 ======================== -->
    <section class="strip">
      <div class="strip-inner">
        <div v-for="s in strip" :key="s.label" class="strip-item">
          <div class="strip-num">{{ s.num }}</div>
          <div class="strip-label">{{ s.label }}</div>
        </div>
      </div>
    </section>

    <!-- ======================== 功能 ======================== -->
    <section id="features" class="section">
      <div class="section-inner">
        <div class="page-kicker">Features</div>
        <h2 class="section-title">一个闭环，而不是零散的工具</h2>
        <p class="section-desc">从收藏职位到拿到 Offer，每一步都有地方安放。</p>
        <div class="feature-grid">
          <div v-for="f in features" :key="f.title" class="feature-card">
            <div class="feature-icon"><el-icon :size="19"><component :is="f.icon" /></el-icon></div>
            <div class="feature-title">{{ f.title }}</div>
            <div class="feature-desc">{{ f.desc }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- ======================== AI ======================== -->
    <section id="ai" class="section section-tint">
      <div class="section-inner ai-inner">
        <div class="ai-text">
          <div class="page-kicker">AI Engine</div>
          <h2 class="section-title">AI 结果落库，<br />是可回看的资产</h2>
          <p class="section-desc" style="margin-bottom:22px">
            每次 JD 解析、简历匹配、面试题生成都会写入数据库，形成你自己的求职数据资产。
            没配 API Key 时自动走本地规则引擎，输出结构完全一致，开箱即用。
          </p>
          <ul class="ai-list">
            <li v-for="item in aiPoints" :key="item">
              <el-icon :size="13" color="#9c7c3c"><CircleCheckFilled /></el-icon>
              <span>{{ item }}</span>
            </li>
          </ul>
          <el-button type="primary" size="large" style="margin-top:26px" @click="goRegister">体验 AI 助手</el-button>
        </div>
        <div class="ai-mock">
          <div class="ai-card">
            <div class="ai-card-head">
              <span class="ai-dot" />
              <span class="ai-card-title">AI 助手工作流</span>
              <el-tag size="small" effect="plain">AI</el-tag>
            </div>

            <!-- Step 1: JD 解析 -->
            <div class="ai-step">
              <div class="ai-step-head">
                <span class="ai-step-num">01</span>
                <span class="ai-step-title">JD 解析</span>
              </div>
              <div class="ai-row"><span class="ai-key">职位</span><span class="ai-val">高级 Java 后端工程师</span></div>
              <div class="ai-row"><span class="ai-key">技能</span>
                <span class="ai-val">
                  <span class="ai-tag">Spring Boot</span><span class="ai-tag">MySQL</span><span class="ai-tag">Redis</span>
                </span>
              </div>
              <div class="ai-row"><span class="ai-key">经验</span><span class="ai-val">3-5 年 · 本科及以上</span></div>
            </div>

            <!-- Step 2: 简历匹配 -->
            <div class="ai-step">
              <div class="ai-step-head">
                <span class="ai-step-num">02</span>
                <span class="ai-step-title">简历匹配</span>
              </div>
              <div class="ai-match">
                <div class="ai-match-label">匹配度</div>
                <div class="ai-match-bar"><div class="ai-match-fill" /></div>
                <div class="ai-match-num">82</div>
              </div>
              <div class="ai-match-detail">
                <span class="ai-tag ai-tag-success">已掌握 Spring Boot</span>
                <span class="ai-tag ai-tag-danger">缺失 高并发</span>
              </div>
            </div>

            <!-- Step 3: 简历优化 -->
            <div class="ai-step">
              <div class="ai-step-head">
                <span class="ai-step-num">03</span>
                <span class="ai-step-title">简历优化</span>
              </div>
              <div class="ai-rewrite">
                <div class="ai-rewrite-before">
                  <span class="ai-rewrite-label">原</span>
                  <span class="ai-rewrite-text">负责订单系统开发</span>
                </div>
                <div class="ai-rewrite-arrow">→</div>
                <div class="ai-rewrite-after">
                  <span class="ai-rewrite-label">改</span>
                  <span class="ai-rewrite-text">主导订单系统重构，引入 Redis 缓存，QPS 提升 300%</span>
                </div>
              </div>
            </div>

            <!-- Step 4: AI 出题 -->
            <div class="ai-step">
              <div class="ai-step-head">
                <span class="ai-step-num">04</span>
                <span class="ai-step-title">AI 出题</span>
              </div>
              <div class="ai-question">
                <span class="ai-question-tag">Spring Boot</span>
                <span class="ai-question-text">"Redis 缓存穿透怎么解决？"</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ======================== 流程 ======================== -->
    <section id="steps" class="section">
      <div class="section-inner">
        <div class="page-kicker">Workflow</div>
        <h2 class="section-title">三步，开始有条理地找工作</h2>
        <p class="section-desc">不需要学习成本，注册之后就能用。</p>
        <div class="steps">
          <div v-for="(s, i) in steps" :key="s.title" class="step">
            <div class="step-num">{{ String(i + 1).padStart(2, '0') }}</div>
            <div class="step-title">{{ s.title }}</div>
            <div class="step-desc">{{ s.desc }}</div>
            <div v-if="i < steps.length - 1" class="step-line" />
          </div>
        </div>
      </div>
    </section>

    <!-- ======================== CTA ======================== -->
    <section class="cta">
      <div class="cta-inner">
        <h2 class="cta-title">准备好更有条理地找工作了吗？</h2>
        <p class="cta-desc">免费注册，数据只属于你自己。</p>
        <el-button type="primary" size="large" @click="goRegister">
          免费开始
          <el-icon style="margin-left:4px"><Right /></el-icon>
        </el-button>
      </div>
    </section>

    <!-- ======================== 页脚 ======================== -->
    <footer class="footer">
      <div class="footer-inner">
        <div class="nav-brand">
          <img class="brand-mark brand-mark-on-dark" src="@/assets/logo-tile.png" alt="职得 JobPath" />
          <span class="brand-name">职得 JobPath</span>
        </div>
        <div class="footer-copy">职得 JobPath · 个人求职全流程管理</div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const scrolled = ref(false)

const previewStats = [
  { label: '总投递', value: '18', sub: '本周 +3' },
  { label: '进入面试', value: '5', sub: '面试率 27.8%' },
  { label: '拿到 Offer', value: '2', sub: 'Offer 率 11.1%' },
  { label: '被拒绝', value: '4', sub: '继续投' }
]

const previewInterviews = [
  { company: '小红书', job: '社区后端开发 · 一面', tag: '视频', color: '#dc2626' },
  { company: '美团', job: 'Java 开发 · 二面', tag: '现场', color: '#f59e0b' },
  { company: '字节跳动', job: 'Java 开发 · HR面', tag: '电话', color: '#4a7c59' }
]

const strip = [
  { num: '7 状态', label: '求职看板流转' },
  { num: '3 类', label: 'AI 分析能力' },
  { num: '多版本', label: '简历管理' },
  { num: '本地', label: '数据只属于你' }
]

const features = [
  { icon: 'Tickets', title: '投递记录', desc: '每一次申请都有迹可循，渠道、状态、备注一处管理。' },
  { icon: 'Grid', title: '求职看板', desc: '7 列状态看板，收藏到 Offer 一目了然，点击即可流转。' },
  { icon: 'Document', title: '多版简历', desc: '按岗位维护不同简历版本，支持 PDF / DOCX 导入解析。' },
  { icon: 'Calendar', title: '面试管理', desc: '面试日程、轮次、复盘笔记沉淀，自动推进投递状态。' },
  { icon: 'OfficeBuilding', title: '公司与职位', desc: '目标公司档案 + 职位库，JD 一键粘贴，AI 自动解析。' },
  { icon: 'DataAnalysis', title: '数据看板', desc: '投递趋势、面试率、Offer 率，用数据校准求职节奏。' }
]

const aiPoints = [
  'JD 解析：粘贴原文，自动抽取职位、技能、经验要求',
  '简历匹配：按岗位打分，输出差距项与改写建议',
  '简历优化：项目经历逐条改写，原文 → 改写 → 理由对照',
  '面试题生成：按 JD + 简历生成针对性题目，进入题库可刷'
]

const steps = [
  { title: '注册账号', desc: '10 秒完成注册，所有数据仅自己可见。' },
  { title: '建一份简历', desc: '上传 PDF / DOCX 自动解析，或手动填写。' },
  { title: '开始投递', desc: '粘贴 JD 让 AI 解析，收藏、投递、约面一路推进。' }
]

function onScroll() {
  scrolled.value = window.scrollY > 8
}
function scrollTo(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}
function goLogin() {
  router.push({ path: '/login' })
}
function goRegister() {
  router.push({ path: '/login', query: { tab: 'register' } })
}

onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))
</script>

<style scoped>
.home {
  min-height: 100%;
  background: var(--page-bg);
  color: var(--text-primary);
  overflow-y: auto;
}

/* ======================== 顶栏 ======================== */
.nav {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(246, 245, 242, 0.86);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
  border-bottom: 1px solid transparent;
}
.nav.is-scrolled {
  border-bottom-color: var(--border-light);
  box-shadow: var(--shadow-xs);
}
.nav-inner {
  max-width: 1120px;
  margin: 0 auto;
  height: 60px;
  display: flex;
  align-items: center;
  gap: 40px;
  padding: 0 24px;
}
.nav-brand {
  display: flex;
  align-items: center;
  gap: 9px;
  flex: none;
}
.brand-mark {
  width: 28px;
  height: 28px;
  object-fit: contain;
  display: block;
  flex: none;
}
.brand-name {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.01em;
}
.nav-links {
  display: flex;
  gap: 26px;
  flex: 1;
}
.nav-links a {
  font-size: 13px;
  color: var(--text-regular);
  text-decoration: none;
  transition: color 0.15s ease;
}
.nav-links a:hover {
  color: var(--text-primary);
}
.nav-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: none;
}

/* ======================== Hero ======================== */
.hero {
  padding: 84px 24px 0;
  text-align: center;
}
.hero-inner {
  max-width: 1120px;
  margin: 0 auto;
}
.hero .page-kicker {
  text-align: center;
  display: block;
}
.hero-title {
  font-family: var(--font-display);
  font-size: 46px;
  font-weight: 700;
  letter-spacing: -1px;
  line-height: 1.25;
  margin: 14px 0 18px;
}
.hero-desc {
  max-width: 520px;
  margin: 0 auto;
  font-size: 15px;
  line-height: 1.8;
  color: var(--text-secondary);
}
.hero-actions {
  margin-top: 30px;
  display: flex;
  justify-content: center;
  gap: 6px;
}

/* 界面预览 */
.hero-preview {
  margin-top: 64px;
  perspective: 1400px;
}
.preview-frame {
  max-width: 940px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid var(--border-light);
  border-radius: 14px 14px 0 0;
  border-bottom: none;
  box-shadow: 0 30px 80px -30px rgba(26, 29, 35, 0.25);
  overflow: hidden;
  transform: rotateX(6deg);
  transform-origin: center bottom;
}
.preview-bar {
  height: 34px;
  background: #fbfaf8;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 14px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ddd9d0;
}
.preview-url {
  margin-left: 10px;
  font-size: 11px;
  color: var(--text-placeholder);
  background: #f1efe9;
  padding: 2px 12px;
  border-radius: 10px;
}
.preview-body {
  display: flex;
  height: 380px;
  text-align: left;
}
.pv-sider {
  width: 132px;
  background: #fbfaf8;
  border-right: 1px solid var(--border-light);
  padding: 14px 10px;
  flex: none;
}
.pv-logo-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 18px;
  padding: 0 4px;
}
.pv-logo-mark {
  width: 18px;
  height: 18px;
  object-fit: contain;
  display: block;
  flex: none;
}
.pv-logo-text {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-primary);
}
.pv-menu-item {
  height: 28px;
  display: flex;
  align-items: center;
  padding: 0 8px;
  border-radius: 6px;
  font-size: 11px;
  color: var(--text-placeholder);
  margin-bottom: 2px;
}
.pv-menu-item.on {
  background: var(--sider-menu-active-bg);
  color: var(--text-primary);
  font-weight: 600;
}

.pv-menu-item.is-ai {
  color: var(--brand);
}

.pv-ai-dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--brand);
  margin-right: 4px;
  flex: none;
}
.pv-main {
  flex: 1;
  background: var(--page-bg);
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.pv-topbar {
  height: 36px;
  background: #fbfaf8;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  flex: none;
}
.pv-breadcrumb {
  font-size: 10.5px;
  color: var(--text-placeholder);
}
.pv-avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #c8b89a;
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.pv-content {
  flex: 1;
  padding: 14px 16px;
  overflow: hidden;
}
.pv-hero-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid var(--border-light);
  border-radius: 8px;
}
.pv-greeting {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.3px;
}
.pv-date {
  font-size: 10px;
  color: var(--text-secondary);
  margin-top: 2px;
}
.pv-quote {
  font-family: var(--font-display);
  font-size: 10.5px;
  color: var(--brand-deep);
  background: var(--brand-softer);
  border: 1px solid var(--brand-soft);
  border-radius: 6px;
  padding: 6px 10px;
  max-width: 180px;
  line-height: 1.5;
}
.pv-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 10px;
}
.pv-stat {
  background: #fff;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 10px 12px;
}
.pv-stat-label {
  font-size: 10px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}
.pv-stat-num {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.5px;
  line-height: 1;
}
.pv-stat-sub {
  font-size: 9.5px;
  color: var(--text-placeholder);
  margin-top: 3px;
}
.pv-charts {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 8px;
  height: 148px;
}
.pv-chart-card {
  background: #fff;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 10px 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.pv-chart-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-regular);
  margin-bottom: 8px;
}
.pv-chart-svg {
  width: 100%;
  height: 100px;
  display: block;
}
.pv-interview {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  border-bottom: 1px solid var(--border-light);
}
.pv-interview:last-child {
  border-bottom: none;
}
.pv-interview-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: none;
}
.pv-interview-info {
  flex: 1;
  min-width: 0;
}
.pv-interview-company {
  font-size: 11px;
  font-weight: 600;
}
.pv-interview-job {
  font-size: 9.5px;
  color: var(--text-placeholder);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pv-interview-tag {
  font-size: 9px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 10px;
  background: var(--brand-softer);
  color: var(--brand-deep);
  flex: none;
}

/* ======================== 数据带 ======================== */
.strip {
  border-top: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
  background: #fbfaf8;
  margin-top: 0;
}
.strip-inner {
  max-width: 1120px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  padding: 30px 24px;
  gap: 16px;
}
.strip-item {
  text-align: center;
}
.strip-num {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.5px;
}
.strip-label {
  font-size: 12.5px;
  color: var(--text-secondary);
  margin-top: 4px;
}

/* ======================== 通用 section ======================== */
.section {
  padding: 96px 24px;
}
.section-tint {
  background: #fbfaf8;
  border-top: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
}
.section-inner {
  max-width: 1120px;
  margin: 0 auto;
}
.section-title {
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.6px;
  margin: 10px 0 10px;
}
.section-desc {
  font-size: 14.5px;
  color: var(--text-secondary);
  margin: 0 0 44px;
  line-height: 1.75;
}

/* ======================== 功能 ======================== */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}
.feature-card {
  background: #fff;
  border: 1px solid var(--border-light);
  border-radius: var(--radius);
  padding: 26px 24px 24px;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}
.feature-card:hover {
  box-shadow: var(--shadow);
  transform: translateY(-2px);
}
.feature-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--brand-softer);
  color: var(--brand);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}
.feature-title {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 7px;
}
.feature-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.75;
}

/* ======================== AI ======================== */
.ai-inner {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 60px;
  align-items: center;
}
.ai-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ai-list li {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  font-size: 13.5px;
  color: var(--text-regular);
  line-height: 1.6;
}
.ai-list .el-icon {
  margin-top: 3px;
  flex: none;
}
.ai-mock {
  display: flex;
  justify-content: center;
}
.ai-card {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 22px 24px;
  box-shadow: var(--shadow);
}
.ai-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-light);
}
.ai-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--brand);
}
.ai-card-title {
  font-size: 13px;
  font-weight: 700;
  flex: 1;
}
.ai-row {
  display: flex;
  gap: 14px;
  padding: 8px 0;
  font-size: 13px;
}
.ai-key {
  color: var(--text-secondary);
  flex: none;
  width: 34px;
}
.ai-val {
  color: var(--text-primary);
  font-weight: 500;
}
.ai-tag {
  display: inline-block;
  background: var(--brand-softer);
  color: var(--brand-deep);
  border-radius: 6px;
  font-size: 11.5px;
  font-weight: 600;
  padding: 2px 8px;
  margin-right: 6px;
}
.ai-match {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  gap: 12px;
}
.ai-match-label {
  font-size: 12px;
  color: var(--text-secondary);
  flex: none;
}
.ai-match-bar {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: var(--info-soft);
  overflow: hidden;
}
.ai-match-fill {
  height: 100%;
  width: 82%;
  border-radius: 3px;
  background: var(--brand);
}
.ai-match-num {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
  color: var(--brand-deep);
}

/* AI 工作流卡片 */
.ai-step {
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-light);
}
.ai-step:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}
.ai-step-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.ai-step-num {
  font-family: var(--font-display);
  font-size: 11px;
  font-weight: 700;
  color: var(--brand);
  background: var(--brand-softer);
  border-radius: 4px;
  padding: 2px 6px;
}
.ai-step-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
}
.ai-match-detail {
  margin-top: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.ai-tag-success {
  background: #f0f9eb;
  color: #67c23a;
}
.ai-tag-danger {
  background: #fef0f0;
  color: #f56c6c;
}
.ai-rewrite {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.ai-rewrite-label {
  flex: none;
  width: 20px;
  height: 20px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
}
.ai-rewrite-before .ai-rewrite-label {
  background: #f4f4f5;
  color: #909399;
}
.ai-rewrite-after .ai-rewrite-label {
  background: #f0f9eb;
  color: #67c23a;
}
.ai-rewrite-text {
  flex: 1;
  line-height: 1.4;
}
.ai-rewrite-before .ai-rewrite-text {
  color: var(--text-secondary);
  text-decoration: line-through;
}
.ai-rewrite-after .ai-rewrite-text {
  color: var(--text-primary);
  font-weight: 500;
}
.ai-rewrite-arrow {
  color: var(--brand);
  font-weight: 700;
  flex: none;
}
.ai-question {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.ai-question-tag {
  background: var(--brand-softer);
  color: var(--brand-deep);
  border-radius: 4px;
  padding: 2px 8px;
  font-weight: 600;
  flex: none;
}
.ai-question-text {
  color: var(--text-primary);
  font-style: italic;
}

/* ======================== 流程 ======================== */
.steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 40px;
}
.step {
  position: relative;
}
.step-num {
  font-family: var(--font-display);
  font-size: 42px;
  font-weight: 700;
  color: var(--brand-soft);
  line-height: 1;
  margin-bottom: 14px;
}
.step-title {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 8px;
}
.step-desc {
  font-size: 13.5px;
  color: var(--text-secondary);
  line-height: 1.75;
}
.step-line {
  position: absolute;
  top: 24px;
  right: -28px;
  width: 16px;
  height: 1px;
  background: var(--border);
}

/* ======================== CTA ======================== */
.cta {
  background: var(--sider-logo-bg);
  padding: 88px 24px;
  text-align: center;
}
.cta-title {
  font-family: var(--font-display);
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.5px;
  color: #f6f5f2;
  margin: 0 0 12px;
}
.cta-desc {
  font-size: 14px;
  color: rgba(246, 245, 242, 0.55);
  margin: 0 0 30px;
}
.cta .el-button--primary {
  --el-button-bg-color: #f6f5f2;
  --el-button-border-color: #f6f5f2;
  --el-button-text-color: #1a1d23;
  --el-button-hover-bg-color: #fff;
  --el-button-hover-border-color: #fff;
  --el-button-hover-text-color: #1a1d23;
  --el-button-active-bg-color: #e8e6e1;
  box-shadow: none;
}

/* ======================== 页脚 ======================== */
.footer {
  background: var(--sider-logo-bg);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  padding: 26px 24px 30px;
}
.footer-inner {
  max-width: 1120px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.footer .brand-name {
  color: #f6f5f2;
}
.footer .brand-mark {
  /* 页脚是深色底，换成带米白圆角底的版本，透明标记会糊在深色里 */
  border-radius: 8px;
}
.footer-copy {
  font-size: 12px;
  color: rgba(246, 245, 242, 0.4);
}

/* ======================== 响应式 ======================== */
@media (max-width: 860px) {
  .nav-links {
    display: none;
  }
  .hero {
    padding-top: 56px;
  }
  .hero-title {
    font-size: 32px;
  }
  .feature-grid,
  .steps {
    grid-template-columns: 1fr;
  }
  .ai-inner {
    grid-template-columns: 1fr;
    gap: 40px;
  }
  .strip-inner {
    grid-template-columns: repeat(2, 1fr);
  }
  .step-line {
    display: none;
  }
  .section {
    padding: 64px 24px;
  }
}
</style>
