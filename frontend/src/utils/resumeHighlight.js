/** 把普通文本按原 PDF 中提取到的强调词切成安全的文本片段。 */
export function splitHighlightedText(text, terms) {
  const source = typeof text === 'string' ? text : ''
  const candidates = [...new Set((Array.isArray(terms) ? terms : [])
    .filter((term) => typeof term === 'string' && term.length)
    .filter((term) => source.includes(term)))]
    .sort((a, b) => b.length - a.length)
  if (!source || !candidates.length) {
    return source ? [{ text: source, highlighted: false }] : []
  }

  const parts = []
  let cursor = 0
  while (cursor < source.length) {
    let nextIndex = source.length
    let nextTerm = ''
    for (const term of candidates) {
      const index = source.indexOf(term, cursor)
      if (index >= 0 && (index < nextIndex || (index === nextIndex && term.length > nextTerm.length))) {
        nextIndex = index
        nextTerm = term
      }
    }
    if (!nextTerm) {
      parts.push({ text: source.slice(cursor), highlighted: false })
      break
    }
    if (nextIndex > cursor) {
      parts.push({ text: source.slice(cursor, nextIndex), highlighted: false })
    }
    parts.push({ text: nextTerm, highlighted: true })
    cursor = nextIndex + nextTerm.length
  }
  return parts
}
