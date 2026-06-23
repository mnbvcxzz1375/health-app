import { computed, reactive, ref } from 'vue'
import { askConsultQuestion, type ConsultResponse } from '@/api/modules/consult'
import {
  createDevice,
  deleteDevice,
  getDevices,
  syncDevice,
  type CreateDevicePayload,
  type DeviceItem,
} from '@/api/modules/device'
import { getHomeSummary, type HomeSummary } from '@/api/modules/home'
import { getMonitorLatest } from '@/api/modules/monitor'
import { getRehabPlan, type RehabPlan } from '@/api/modules/rehab'
import { discardAnalyzeReport, getSavedAnalyzeReports, type SavedAnalyzeReport } from '@/api/modules/upload'
import { readAppleHealthSnapshot } from '@/modules/home/services/appleHealthBridge'
import { buildInsightFromMonitor, type HealthInsight } from '@/modules/home/services/healthInsightService'
import { getHealthScore, type HealthScoreResponse } from '@/api/modules/healthScore'
import { getContextSnapshot, type ContextSnapshot } from '@/api/modules/context'
import { useToast } from '@/composables/useToast'
import type { AsyncViewState } from '@/shared/types/ui'

type BluetoothCharacteristicValue = {
  getUint8: (offset: number) => number
  getUint16: (offset: number, littleEndian?: boolean) => number
}

type BluetoothCharacteristic = {
  readValue: () => Promise<BluetoothCharacteristicValue>
}

type BluetoothService = {
  getCharacteristic: (characteristic: string) => Promise<BluetoothCharacteristic>
}

type BluetoothGattServer = {
  getPrimaryService: (service: string) => Promise<BluetoothService>
}

type BluetoothDeviceLike = {
  name?: string
  gatt?: {
    connect: () => Promise<BluetoothGattServer>
  }
}

type BluetoothNavigator = Navigator & {
  bluetooth?: {
    requestDevice: (options: {
      acceptAllDevices: boolean
      optionalServices?: string[]
    }) => Promise<BluetoothDeviceLike>
  }
}

export function useHomeDashboard() {
  const { success, warning, error } = useToast()

  const viewState = ref<AsyncViewState>('loading')
  const summary = ref<HomeSummary | null>(null)
  const rehabPlan = ref<RehabPlan | null>(null)
  const devices = ref<DeviceItem[]>([])
  const savedReports = ref<SavedAnalyzeReport[]>([])
  const viewError = ref('')
  const healthInsight = ref<HealthInsight | null>(null)
  const healthScore = ref<HealthScoreResponse | null>(null)
  const contextSnapshot = ref<ContextSnapshot | null>(null)

  const consultQuestion = ref('')
  const consultResponse = ref<ConsultResponse | null>(null)
  const consultLoading = ref(false)
  const consultChips = ref([
    '帮我看看今天的健康状态',
    '根据数据给我一份恢复建议',
    '生成明天的健康行动计划',
  ])

  const showAddDeviceForm = ref(false)
  const creatingDevice = ref(false)
  const syncingId = ref<number | null>(null)

  const deviceForm = reactive<CreateDevicePayload>({
    name: '',
    brand: '',
    model: '',
    type: 'watch',
  })

  const hasClinicalSummary = computed(() => {
    return Boolean(
      summary.value && (summary.value.keyMetrics.length || summary.value.suggestions.length || savedReports.value.length),
    )
  })

  const loadDashboard = async () => {
    viewState.value = 'loading'
    viewError.value = ''

    try {
      const [homeData, rehabData, deviceData, reportData, latestMonitor] = await Promise.all([
        getHomeSummary(),
        getRehabPlan(),
        getDevices(),
        getSavedAnalyzeReports(),
        getMonitorLatest(),
      ])

      summary.value = homeData
      rehabPlan.value = rehabData
      devices.value = deviceData
      savedReports.value = reportData

      const [scoreResult, contextResult] = await Promise.allSettled([
        getHealthScore(),
        getContextSnapshot(),
      ])

      if (scoreResult.status === 'fulfilled') healthScore.value = scoreResult.value
      if (contextResult.status === 'fulfilled') contextSnapshot.value = contextResult.value

      const insight = buildInsightFromMonitor({
        hr: latestMonitor.hr,
        sleep: latestMonitor.sleep,
        stress: latestMonitor.stress,
        stepsNow: homeData.stepsNow,
      })

      healthInsight.value = insight
      consultChips.value = insight.consultChips
      consultQuestion.value = insight.consultQuestion
      viewState.value = hasClinicalSummary.value ? 'success' : 'empty'
    } catch (err) {
      viewError.value = err instanceof Error ? err.message : '首页加载失败'
      viewState.value = 'error'
    }
  }

  const loadDevices = async () => {
    devices.value = await getDevices()
  }

  const submitConsult = async () => {
    const question = consultQuestion.value.trim()
    if (!question) {
      warning('请输入问题', '填写后再获取建议。')
      return
    }

    consultLoading.value = true
    try {
      consultResponse.value = await askConsultQuestion({
        question,
        scene: 'home_overview',
      })
    } catch (err) {
      warning('咨询失败', err instanceof Error ? err.message : '请稍后重试。')
    } finally {
      consultLoading.value = false
    }
  }

  const resetDeviceForm = () => {
    deviceForm.name = ''
    deviceForm.brand = ''
    deviceForm.model = ''
    deviceForm.type = 'watch'
  }

  const cancelAddDevice = () => {
    showAddDeviceForm.value = false
    resetDeviceForm()
  }

  const confirmAddDevice = async () => {
    if (!deviceForm.brand.trim() && !deviceForm.model.trim() && !deviceForm.name.trim()) {
      warning('请补充设备信息', '至少填写设备名称或品牌型号。')
      return
    }

    creatingDevice.value = true
    try {
      await createDevice({
        name: deviceForm.name,
        brand: deviceForm.brand,
        model: deviceForm.model,
        type: deviceForm.type,
      })
      await loadDevices()
      success('设备已添加', '设备列表已更新。')
      cancelAddDevice()
    } catch (err) {
      warning('添加失败', err instanceof Error ? err.message : '请稍后重试。')
    } finally {
      creatingDevice.value = false
    }
  }

  const readHeartRateFromServer = async (server: BluetoothGattServer) => {
    try {
      const service = await server.getPrimaryService('heart_rate')
      const characteristic = await service.getCharacteristic('heart_rate_measurement')
      const value = await characteristic.readValue()
      const flags = value.getUint8(0)
      return flags & 0x01 ? value.getUint16(1, true) : value.getUint8(1)
    } catch {
      return null
    }
  }

  const readBatteryLevel = async (server: BluetoothGattServer) => {
    try {
      const service = await server.getPrimaryService('battery_service')
      const characteristic = await service.getCharacteristic('battery_level')
      const value = await characteristic.readValue()
      return value.getUint8(0)
    } catch {
      return null
    }
  }

  const connectBluetoothDevice = async () => {
    const bluetoothNavigator = typeof navigator === 'undefined' ? null : (navigator as BluetoothNavigator)
    if (!bluetoothNavigator?.bluetooth?.requestDevice) {
      warning('当前环境不支持蓝牙连接', '请使用支持 Web Bluetooth 的浏览器。')
      return
    }

    try {
      const device = await bluetoothNavigator.bluetooth.requestDevice({
        acceptAllDevices: true,
        optionalServices: ['heart_rate', 'battery_service'],
      })
      const server = await device.gatt?.connect()
      if (!server) {
        throw new Error('未能建立蓝牙连接')
      }

      const heartRate = await readHeartRateFromServer(server)
      const battery = await readBatteryLevel(server)
      const deviceName = device.name?.trim() || '蓝牙设备'

      await createDevice({
        name: deviceName,
        brand: 'Bluetooth',
        model: 'BLE Device',
        type: 'watch',
      })
      await loadDevices()

      const detailParts = [
        heartRate ? `当前心率 ${heartRate} bpm` : '设备已连接',
        battery !== null ? `电量 ${battery}%` : '',
      ].filter(Boolean)

      success('设备连接成功', detailParts.join('；') || '已读取可用数据。')
    } catch (err) {
      warning('蓝牙连接失败', err instanceof Error ? err.message : '请检查权限后重试。')
    }
  }

  const syncAppleHealthDevice = async () => {
    try {
      const snapshot = await readAppleHealthSnapshot()
      await createDevice({
        name: 'Apple Health',
        brand: 'Apple',
        model: snapshot.source === 'apple_health' ? 'HealthKit' : 'Mock',
        type: 'watch',
      })
      await loadDevices()

      const detailParts = [
        snapshot.heartRate?.avgBpm ? `心率 ${snapshot.heartRate.avgBpm} bpm` : '',
        snapshot.bodyTemperature?.celsius ? `体温 ${snapshot.bodyTemperature.celsius.toFixed(1)}°C` : '',
        snapshot.stepsToday ? `步数 ${snapshot.stepsToday}` : '',
      ].filter(Boolean)

      success('健康数据同步成功', detailParts.join('；') || '已读取可用健康数据。')
    } catch (err) {
      warning('健康数据同步失败', err instanceof Error ? err.message : '请检查 Apple Health 接入。')
    }
  }

  const syncOneDevice = async (id: number) => {
    syncingId.value = id
    try {
      await syncDevice(id)
      await loadDevices()
      success('同步成功', '设备数据已更新。')
    } catch (err) {
      warning('同步失败', err instanceof Error ? err.message : '请稍后重试。')
    } finally {
      syncingId.value = null
    }
  }

  const removeOneDevice = async (id: number) => {
    try {
      await deleteDevice(id)
      await loadDevices()
      success('设备已移除')
    } catch (err) {
      warning('移除失败', err instanceof Error ? err.message : '请稍后重试。')
    }
  }

  const removeSavedReport = async (taskId: string) => {
    try {
      await discardAnalyzeReport(taskId)
      savedReports.value = savedReports.value.filter((item) => item.taskId !== taskId)
      viewState.value = hasClinicalSummary.value ? 'success' : 'empty'
      success('报告已删除')
    } catch (err) {
      error('删除失败', err instanceof Error ? err.message : '请稍后再试。')
    }
  }

  return {
    viewState,
    summary,
    rehabPlan,
    devices,
    savedReports,
    viewError,
    healthInsight,
    healthScore,
    contextSnapshot,
    consultQuestion,
    consultResponse,
    consultLoading,
    consultChips,
    showAddDeviceForm,
    creatingDevice,
    syncingId,
    deviceForm,
    loadDashboard,
    submitConsult,
    cancelAddDevice,
    confirmAddDevice,
    connectBluetoothDevice,
    syncAppleHealthDevice,
    syncOneDevice,
    removeOneDevice,
    removeSavedReport,
  }
}
