import { http } from './http'

export type LedgerExportFormat = 'json' | 'zip'

function parseFilename(contentDisposition: string | undefined, fallback: string): string {
  if (!contentDisposition) return fallback
  const match = /filename="([^"]+)"/i.exec(contentDisposition)
  return match?.[1] ?? fallback
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

export async function downloadFullLedgerExport(format: LedgerExportFormat = 'json'): Promise<void> {
  const res = await http.get('/api/export/full', {
    params: { format },
    responseType: 'blob',
  })
  const ext = format === 'json' ? 'json' : 'zip'
  const fallback = `ledger-export.${ext}`
  const filename = parseFilename(res.headers['content-disposition'], fallback)
  triggerDownload(res.data as Blob, filename)
}
