import { reactive, ref } from 'vue'
import {
  createDevice,
  deleteDevice,
  getDevices,
  syncDevice,
  type CreateDevicePayload,
  type DeviceItem,
} from '@/api/modules/device'
import { readAppleHealthSnapshot } from '@/modules/home/services/appleHealthBridge'
import { useToast } from '@/composables/useToast'

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

export function useDeviceManage() {
  const { success, warning } = useToast()

  const devices = ref<DeviceItem[]>([])
  const showAddDeviceForm = ref(false)
  const creatingDevice = ref(false)
  const syncingId = ref<number | null>(null)

  const deviceForm = reactive<CreateDevicePayload>({
    name: '',
    brand: '',
    model: '',
    type: 'watch',
  })

  const loadDevices = async () => {
    devices.value = await getDevices()
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

  const updateDeviceForm = (payload: { key: keyof CreateDevicePayload; value: string }) => {
    if (payload.key === 'type') {
      deviceForm.type = payload.value as CreateDevicePayload['type']
      return
    }
    deviceForm[payload.key] = payload.value
  }

  return {
    devices,
    showAddDeviceForm,
    creatingDevice,
    syncingId,
    deviceForm,
    loadDevices,
    cancelAddDevice,
    confirmAddDevice,
    connectBluetoothDevice,
    syncAppleHealthDevice,
    syncOneDevice,
    removeOneDevice,
    updateDeviceForm,
  }
}
