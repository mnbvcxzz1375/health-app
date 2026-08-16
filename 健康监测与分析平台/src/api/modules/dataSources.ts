import { http } from '@/api/http'
import { env } from '@/config/env'
import { withMockFallback } from '@/dev/mockApi'

/** 单个数据来源元信息。 */
export type DataSourceItem = {
  id: number
  sourceName: string
  sourceType: 'open' | 'academic' | 'manual' | 'api'
  targetTable: string
  recordCount: number
  license: string
  referenceUrl: string
  citation: string
  lastUpdatedAt: string | null
}

/** 按类型分组的数量。 */
export type CountsByType = {
  open: number
  academic: number
  manual: number
  api: number
}

/** 数据来源汇总统计。 */
export type DataSourceSummary = {
  totalSources: number
  totalRecords: number
  lastUpdatedAt: string | null
  byType: CountsByType
}

/** 列出所有数据来源。 */
export async function listDataSources(): Promise<DataSourceItem[]> {
  return withMockFallback(
    async () => {
      const resp = await http.get<DataSourceItem[]>('/data-sources')
      return resp.data
    },
    () => mockDataSources(),
    true,
  )
}

/** 获取数据来源汇总统计。 */
export async function getDataSourceSummary(): Promise<DataSourceSummary> {
  return withMockFallback(
    async () => {
      const resp = await http.get<DataSourceSummary>('/data-sources/summary')
      return resp.data
    },
    () => mockSummary(mockDataSources()),
    true,
  )
}

// ===== Mock 数据（开发环境或后端不可用时使用） =====

function mockDataSources(): DataSourceItem[] {
  const now = new Date().toISOString()
  return [
    {
      id: 1, sourceName: 'Wikidata', sourceType: 'open', targetTable: 'tcm_herbs',
      recordCount: 30, license: 'CC-BY-SA', referenceUrl: 'https://www.wikidata.org/wiki/Q188854',
      citation: 'Wikidata 中药条目，通过 SPARQL 端点查询', lastUpdatedAt: now,
    },
    {
      id: 2, sourceName: 'TCMSP', sourceType: 'open', targetTable: 'tcm_herbs',
      recordCount: 10, license: '学术开放', referenceUrl: 'https://tcmsp-e.com/',
      citation: 'TCMSP 中药系统药理学数据库', lastUpdatedAt: now,
    },
    {
      id: 3, sourceName: '国家基本药物目录', sourceType: 'open', targetTable: 'drug_clinical_info',
      recordCount: 100, license: '政府公开数据', referenceUrl: 'https://www.nmpa.gov.cn/',
      citation: '国家基本药物目录 2018 版', lastUpdatedAt: now,
    },
    {
      id: 4, sourceName: 'HERB 本草组鉴', sourceType: 'academic', targetTable: 'tcm_herbs',
      recordCount: 100, license: '学术开放', referenceUrl: 'http://herb.ac.cn/',
      citation: 'HERB 本草组鉴（NAR 2022）', lastUpdatedAt: now,
    },
    {
      id: 5, sourceName: 'ETCM 中药百科', sourceType: 'academic', targetTable: 'tcm_herbs',
      recordCount: 50, license: '学术开放', referenceUrl: 'http://www.nrc.ac.cn:9090/ETCM/',
      citation: 'ETCM 中药百科数据库（NAR 2018）', lastUpdatedAt: now,
    },
    {
      id: 6, sourceName: 'PubMed Central', sourceType: 'academic', targetTable: 'ddi_knowledge',
      recordCount: 10, license: '学术开放', referenceUrl: 'https://www.ncbi.nlm.nih.gov/pmc/',
      citation: 'PubMed Central 学术文献', lastUpdatedAt: now,
    },
    {
      id: 7, sourceName: '十八反十九畏', sourceType: 'manual', targetTable: 'tcm_incompatibility',
      recordCount: 30, license: '公共医学常识', referenceUrl: '',
      citation: '《本草经集注》《珍珠囊补遗药性赋》整理', lastUpdatedAt: now,
    },
    {
      id: 8, sourceName: '中西药交互 Top 100', sourceType: 'manual', targetTable: 'tcm_wm_interaction',
      recordCount: 100, license: '公共医学常识', referenceUrl: '',
      citation: '基于 TCMBank、临床指南、药理研究人工核对', lastUpdatedAt: now,
    },
    {
      id: 9, sourceName: '中药忌口', sourceType: 'manual', targetTable: 'drug_food_interaction',
      recordCount: 80, license: '公共医学常识', referenceUrl: '',
      citation: '《中药学》教材 + 临床经验 + LLM 补充', lastUpdatedAt: now,
    },
    {
      id: 10, sourceName: '健身动作库', sourceType: 'manual', targetTable: 'rehab_exercises',
      recordCount: 43, license: '公共医学常识', referenceUrl: '',
      citation: '健身动作百科 + 运动训练学', lastUpdatedAt: now,
    },
    {
      id: 11, sourceName: '中国食物成分表', sourceType: 'manual', targetTable: 'food_items',
      recordCount: 62, license: '版权数据', referenceUrl: '',
      citation: '《中国食物成分表》第 6 版', lastUpdatedAt: now,
    },
    {
      id: 12, sourceName: '国家基本药物目录（西药）', sourceType: 'manual', targetTable: 'drug_clinical_info',
      recordCount: 40, license: '政府公开数据', referenceUrl: '',
      citation: '基于国家基本药物目录 + 药品说明书人工整理', lastUpdatedAt: now,
    },
  ]
}

function mockSummary(items: DataSourceItem[]): DataSourceSummary {
  const byType = items.reduce(
    (acc, item) => {
      acc[item.sourceType] += 1
      return acc
    },
    { open: 0, academic: 0, manual: 0, api: 0 } as CountsByType,
  )
  const totalRecords = items.reduce((sum, item) => sum + item.recordCount, 0)
  const lastUpdatedAt = items
    .map((i) => i.lastUpdatedAt)
    .filter((v): v is string => !!v)
    .sort()
    .at(-1) ?? null
  return {
    totalSources: items.length,
    totalRecords,
    lastUpdatedAt,
    byType,
  }
}

// 防止 env 未使用警告（保留 import 用于未来扩展）
void env
