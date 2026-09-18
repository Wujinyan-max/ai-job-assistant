/**
 * 生产环境静态服务器 + API 反向代理
 *
 * 作用：把前端构建产物（dist）和 /api 接口放在同一个端口上，
 *       这样内网穿透只需要暴露一个端口，前端和 API 天然同源，不会有跨域问题。
 *
 * 用法：node serve.cjs [端口]
 */
const http = require('node:http')
const fs = require('node:fs')
const path = require('node:path')

const PORT = Number(process.argv[2]) || 8080
const API_TARGET = process.env.API_TARGET || 'http://127.0.0.1:8088'
const DIST = path.join(__dirname, 'dist')

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.txt': 'text/plain; charset=utf-8'
}

/** 把请求代理到后端 */
function proxy(req, res) {
  const target = new URL(API_TARGET)
  const options = {
    hostname: target.hostname,
    port: target.port || 80,
    path: req.url,
    method: req.method,
    headers: { ...req.headers, host: target.host }
  }

  const upstream = http.request(options, (upstreamRes) => {
    res.writeHead(upstreamRes.statusCode, upstreamRes.headers)
    upstreamRes.pipe(res)
  })

  upstream.on('error', (err) => {
    console.error('[proxy error]', err.message)
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
    }
    res.end(JSON.stringify({ code: 502, message: '后端服务暂时不可用' }))
  })

  req.pipe(upstream)
}

/** 返回静态文件，找不到就回退到 index.html（前端路由需要） */
function serveStatic(req, res) {
  const urlPath = decodeURIComponent(req.url.split('?')[0])
  let filePath = path.join(DIST, urlPath === '/' ? 'index.html' : urlPath)

  // 防目录穿越
  if (!filePath.startsWith(DIST)) {
    res.writeHead(403)
    return res.end('Forbidden')
  }

  fs.stat(filePath, (err, stat) => {
    if (err || !stat.isFile()) {
      filePath = path.join(DIST, 'index.html')
    }
    const ext = path.extname(filePath).toLowerCase()
    fs.readFile(filePath, (readErr, data) => {
      if (readErr) {
        res.writeHead(404)
        return res.end('Not Found')
      }
      const headers = { 'Content-Type': MIME[ext] || 'application/octet-stream' }
      // 带 hash 的静态资源可以长期缓存
      if (filePath.includes(`${path.sep}assets${path.sep}`)) {
        headers['Cache-Control'] = 'public, max-age=604800'
      }
      res.writeHead(200, headers)
      res.end(data)
    })
  })
}

const server = http.createServer((req, res) => {
  if (req.url.startsWith('/api')) {
    proxy(req, res)
  } else {
    serveStatic(req, res)
  }
})

server.listen(PORT, () => {
  console.log(`前端 + API 网关已启动：http://localhost:${PORT}`)
  console.log(`  - 静态资源目录：${DIST}`)
  console.log(`  - API 代理目标：${API_TARGET}`)
})
