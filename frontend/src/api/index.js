import request from './request'

/* ------------------------------- 认证 ---------------------------------- */
export const authApi = {
  register: (data) => request.post('/auth/register', data),
  login: (data) => request.post('/auth/login', data),
  me: () => request.get('/auth/me'),
  updateProfile: (data) => request.put('/auth/profile', data),
  changePassword: (data) => request.put('/auth/password', data)
}

/* ------------------------------- 公司 ---------------------------------- */
export const companyApi = {
  page: (params) => request.get('/companies', { params }),
  all: () => request.get('/companies/all'),
  detail: (id) => request.get(`/companies/${id}`),
  create: (data) => request.post('/companies', data),
  update: (id, data) => request.put(`/companies/${id}`, data),
  remove: (id) => request.delete(`/companies/${id}`)
}

/* ------------------------------- 职位 ---------------------------------- */
export const jobApi = {
  page: (params) => request.get('/jobs', { params }),
  detail: (id) => request.get(`/jobs/${id}`),
  create: (data) => request.post('/jobs', data),
  update: (id, data) => request.put(`/jobs/${id}`, data),
  remove: (id) => request.delete(`/jobs/${id}`)
}

/* ------------------------------- 简历 ---------------------------------- */
export const resumeApi = {
  page: (params) => request.get('/resumes', { params }),
  all: () => request.get('/resumes/all'),
  detail: (id) => request.get(`/resumes/${id}`),
  create: (data) => request.post('/resumes', data),
  update: (id, data) => request.put(`/resumes/${id}`, data),
  remove: (id) => request.delete(`/resumes/${id}`),
  setDefault: (id) => request.put(`/resumes/${id}/default`)
}

/* ------------------------------- 投递 ---------------------------------- */
export const applicationApi = {
  page: (params) => request.get('/applications', { params }),
  board: () => request.get('/applications/board'),
  statuses: () => request.get('/applications/statuses'),
  detail: (id) => request.get(`/applications/${id}`),
  create: (data) => request.post('/applications', data),
  update: (id, data) => request.put(`/applications/${id}`, data),
  updateStatus: (id, status) => request.patch(`/applications/${id}/status`, { status }),
  remove: (id) => request.delete(`/applications/${id}`)
}

/* ------------------------------- 面试 ---------------------------------- */
export const interviewApi = {
  page: (params) => request.get('/interviews', { params }),
  upcoming: (days = 7) => request.get('/interviews/upcoming', { params: { days } }),
  create: (data) => request.post('/interviews', data),
  update: (id, data) => request.put(`/interviews/${id}`, data),
  remove: (id) => request.delete(`/interviews/${id}`)
}

/* ------------------------------ 面试题库 -------------------------------- */
export const questionApi = {
  page: (params) => request.get('/questions', { params }),
  categories: () => request.get('/questions/categories'),
  markMastered: (id, mastered) => request.patch(`/questions/${id}/mastered`, { mastered }),
  remove: (id) => request.delete(`/questions/${id}`),
  removeBatch: (ids) => request.delete('/questions', { data: ids })
}

/* --------------------------------- AI ---------------------------------- */
export const aiApi = {
  analyzeJd: (data) => request.post('/ai/analyze-jd', data),
  matchResume: (data) => request.post('/ai/match-resume', data),
    generateQuestions: (data) => request.post('/ai/generate-questions', data),
    history: (params) => request.get('/ai/history', { params }),
    config: () => request.get('/ai/config')
  }

/* ------------------------------- 看板 ---------------------------------- */
export const dashboardApi = {
  overview: (trendDays = 30) => request.get('/dashboard', { params: { trendDays } }),
  home: (params) => request.get('/dashboard/home', { params })
}
