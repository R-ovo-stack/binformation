/** Parse / serialize endpoint.attrs JSON by endpoint type. */

export type AttrField = {
  key: string
  label: string
  placeholder?: string
}

const FIELDS_BY_TYPE: Record<string, AttrField[]> = {
  KAFKA_TOPIC: [{ key: 'topicName', label: '主题名', placeholder: '如 topic-A' }],
  ROCKETMQ_TOPIC: [{ key: 'topicName', label: '主题名', placeholder: '如 file-incr-notify' }],
  DIRECTORY: [{ key: 'dirPath', label: '目录路径', placeholder: '如 /data/d/' }],
  HOST: [
    { key: 'hostname', label: '主机名', placeholder: '如 idc301' },
    { key: 'role', label: '角色', placeholder: '如 kafka-broker,app' },
  ],
  HTTP_API: [
    { key: 'url', label: 'URL', placeholder: 'https://...' },
    { key: 'method', label: '方法', placeholder: 'GET / POST' },
  ],
  OBJECT_BUCKET: [{ key: 'bucketName', label: '桶名', placeholder: 'bucket-name' }],
  OBJECT_PREFIX: [{ key: 'prefix', label: '前缀', placeholder: 'inbound/cust/' }],
}

export function attrFieldsForType(type: string): AttrField[] {
  return FIELDS_BY_TYPE[type] ?? []
}

export function hasTypedAttrs(type: string): boolean {
  return (FIELDS_BY_TYPE[type]?.length ?? 0) > 0
}

export function parseAttrsJson(raw: string | null | undefined): Record<string, string> {
  if (!raw?.trim()) return {}
  try {
    const parsed = JSON.parse(raw) as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    const out: Record<string, string> = {}
    for (const [k, v] of Object.entries(parsed as Record<string, unknown>)) {
      if (v == null) continue
      out[k] = typeof v === 'string' ? v : String(v)
    }
    return out
  } catch {
    return {}
  }
}

export function serializeAttrs(
  type: string,
  fields: Record<string, string>,
  extraRaw?: string | null,
): string | null {
  const known = attrFieldsForType(type)
  const obj: Record<string, string> = {}

  if (known.length) {
    for (const f of known) {
      const v = fields[f.key]?.trim()
      if (v) obj[f.key] = v
    }
  }

  // Preserve unknown keys from original / advanced JSON
  const extra = parseAttrsJson(extraRaw)
  for (const [k, v] of Object.entries(extra)) {
    if (known.some((f) => f.key === k)) continue
    if (v?.trim()) obj[k] = v.trim()
  }

  if (!Object.keys(obj).length) return null
  return JSON.stringify(obj)
}

/** Suggest display name from primary attr when name is empty. */
export function suggestNameFromAttrs(type: string, fields: Record<string, string>): string {
  const primary =
    type === 'DIRECTORY'
      ? fields.dirPath
      : type === 'HOST'
        ? fields.hostname
        : type === 'HTTP_API'
          ? fields.url
          : type === 'OBJECT_BUCKET'
            ? fields.bucketName
            : type === 'OBJECT_PREFIX'
              ? fields.prefix
              : fields.topicName
  return primary?.trim() ?? ''
}

/** Short attrs preview for list/tree rows. */
export function attrsSummary(type: string, raw: string | null | undefined): string {
  const parsed = parseAttrsJson(raw)
  const defs = attrFieldsForType(type)
  if (!defs.length) return ''
  const parts = defs
    .map((d) => parsed[d.key])
    .filter((v): v is string => Boolean(v?.trim()))
  return parts.join(' · ')
}
