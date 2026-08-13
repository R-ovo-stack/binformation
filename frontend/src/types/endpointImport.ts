export interface EndpointImportRowError {
  row: number
  name: string
  message: string
}

export interface EndpointImportResult {
  totalRows: number
  created: number
  skipped: number
  errors: EndpointImportRowError[]
}
