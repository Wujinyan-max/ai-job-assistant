const ALLOWED_TAGS = new Set([
  'p', 'div', 'br', 'strong', 'b', 'em', 'i', 'u', 's',
  'ol', 'ul', 'li', 'a', 'font', 'img'
])

function escapeText(value) {
  return String(value ?? '')
    .replace(/&(?!#\d+;|#x[\da-f]+;|[a-z]+;)/gi, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function safeHref(attributes) {
  const match = attributes.match(/\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/i)
  const href = match?.[1] ?? match?.[2] ?? match?.[3] ?? ''
  return /^(https?:\/\/|mailto:)/i.test(href) ? href.replace(/"/g, '&quot;') : ''
}

function safeAttribute(attributes, name) {
  const match = attributes.match(new RegExp(`\\b${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)'|([^\\s>]+))`, 'i'))
  return match?.[1] ?? match?.[2] ?? match?.[3] ?? ''
}

function escapeAttribute(value) {
  return String(value ?? '').replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;')
}

/** 只保留简历排版需要的标签，所有事件和危险链接都会被移除。 */
export function sanitizeResumeHtml(html) {
  return String(html ?? '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .split(/(<[^>]*>)/g)
    .map((part) => {
      if (!part.startsWith('<')) return escapeText(part)
      const match = part.match(/^<\s*(\/?)\s*([a-z0-9]+)([^>]*)>$/i)
      if (!match) return escapeText(part)
      const closing = !!match[1]
      const tag = match[2].toLowerCase()
      const attributes = match[3] || ''
      if (!ALLOWED_TAGS.has(tag)) return ''
      if (tag === 'br') return '<br>'
      if (closing) return `</${tag}>`
      if (tag === 'img') {
        const src = safeAttribute(attributes, 'src')
        if (!/^https:\/\//i.test(src)) return '<img>'
        const alt = safeAttribute(attributes, 'alt')
        return `<img src="${escapeAttribute(src)}"${alt ? ` alt="${escapeAttribute(alt)}"` : ''}>`
      }
      if (tag === 'a') {
        const href = safeHref(attributes)
        return href ? `<a href="${href}" target="_blank" rel="noopener noreferrer">` : '<a>'
      }
      if (tag === 'font') {
        const size = attributes.match(/\bsize\s*=\s*["']?([1-7])/i)?.[1]
        const color = safeAttribute(attributes, 'color')
        const safeColor = /^(#[0-9a-f]{3,8}|rgb\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}\s*\))$/i.test(color) ? color : ''
        return `<font${size ? ` size="${size}"` : ''}${safeColor ? ` color="${safeColor}"` : ''}>`
      }
      if (tag === 'p' || tag === 'div') {
        const style = safeAttribute(attributes, 'style')
        const align = style.match(/(?:^|;)\s*text-align\s*:\s*(left|center|right|justify)\s*(?:;|$)/i)?.[1]
        return align ? `<${tag} style="text-align:${align.toLowerCase()}">` : `<${tag}>`
      }
      return `<${tag}>`
    })
    .join('')
}

export function textToHtml(text) {
  const value = String(text ?? '').trim()
  return value ? `<p>${escapeText(value).replace(/\r?\n/g, '<br>')}</p>` : ''
}

export function htmlToPlainText(html) {
  return sanitizeResumeHtml(html)
    .replace(/<br>/gi, '\n')
    .replace(/<\/li>/gi, '\n')
    .replace(/<\/(p|div|ol|ul)>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/\n{2,}/g, '\n')
    .trim()
}

/** 老数据仍是 summary + bullets；首次编辑时无损合并进统一正文。 */
export function projectToHtml(project) {
  if (typeof project?.contentHtml === 'string' && project.contentHtml.trim()) {
    return sanitizeResumeHtml(project.contentHtml)
  }
  const summary = typeof project?.summary === 'string' ? project.summary.trim() : ''
  const bullets = Array.isArray(project?.bullets)
    ? project.bullets.filter((item) => typeof item === 'string' && item.trim()).map((item) => item.trim())
    : []
  const paragraph = textToHtml(summary)
  const list = bullets.length ? `<ol>${bullets.map((item) => `<li>${escapeText(item)}</li>`).join('')}</ol>` : ''
  return paragraph + list
}
