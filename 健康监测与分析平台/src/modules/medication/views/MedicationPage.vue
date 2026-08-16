<template>
  <div class="apple-medication pb-6" :class="{ 'elder-mode': elderMode }">
    <!-- Page Header -->
    <div class="mx-auto max-w-[420px] px-4 pt-6">
      <div class="flex items-start justify-between">
        <div>
          <h1 class="text-[28px] font-semibold tracking-[-0.02em]" style="color: var(--foreground);">用药管理</h1>
          <p class="mt-0.5 text-[14px]" style="color: var(--muted-foreground);">
            今日 {{ todaySchedule.completedCount }}/{{ todaySchedule.totalCount }}
          </p>
        </div>
        <button
          type="button"
          class="flex h-8 items-center gap-1.5 rounded-full px-3 text-[12px] font-medium transition active:scale-[0.98]"
          :style="elderMode
            ? { background: 'var(--brand-500)', color: 'var(--primary-foreground)' }
            : { background: 'var(--secondary)', color: 'var(--muted-foreground)' }"
          @click="elderMode = !elderMode"
        >
          <iconify-icon :icon="elderMode ? 'solar:eye-bold' : 'solar:eye-outline'" width="14" height="14" />
          {{ elderMode ? '老人模式' : '标准' }}
        </button>
      </div>

      <!-- Apple-style segmented control -->
      <div class="mt-4 flex gap-1 p-1 rounded-[10px]" style="background: var(--secondary);">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          class="flex flex-1 items-center justify-center gap-1.5 rounded-[8px] py-1.5 text-[13px] font-medium transition active:scale-[0.98]"
          :style="activeTab === tab.value
            ? { background: 'var(--card)', color: 'var(--foreground)', boxShadow: 'var(--shadow-xs)' }
            : { color: 'var(--muted-foreground)' }"
          @click="activeTab = tab.value"
        >
          <iconify-icon :icon="tab.icon" width="14" height="14" />
          {{ tab.label }}
        </button>
      </div>
    </div>

    <!-- Tab: Today's Medication (default, Apple timeline) -->
    <div v-if="activeTab === 'today'" class="mx-auto mt-4 max-w-[420px] space-y-3 px-4">
      <!-- Today's Schedule Timeline Card -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">今日服药</h2>
          <span class="text-[13px]" style="color: var(--muted-foreground);">{{ todayProgressLabel }}</span>
        </div>

        <ClinicalStateNotice
          v-if="!todaySchedule.items.length"
          tone="empty"
          title="今日无用药计划"
          description="请先在「用药闹钟」中添加闹钟。"
        />

        <!-- Timeline items -->
        <div v-else>
          <div
            v-for="(item, idx) in todaySchedule.items"
            :key="item.alarmId"
            class="flex items-center py-[14px]"
            :class="{ 'border-b': idx < todaySchedule.items.length - 1 }"
            style="border-color: var(--border);"
          >
            <span
              class="w-[60px] shrink-0 text-[15px] font-semibold tabular-nums"
              style="color: var(--muted-foreground);"
            >{{ item.time }}</span>

            <div class="flex flex-1 min-w-0 items-center gap-2">
              <iconify-icon icon="solar:pills-3-outline" width="18" height="18" class="shrink-0" style="color: var(--brand-500);" />
              <div class="min-w-0">
                <p class="truncate text-[15px]" style="color: var(--foreground);">
                  {{ item.medications[0]?.name || '未知药品' }}
                  <span v-if="item.medications.length > 1" class="text-[12px]" style="color: var(--muted-foreground);">+{{ item.medications.length - 1 }}</span>
                </p>
                <p class="truncate text-[13px]" style="color: var(--muted-foreground);">
                  {{ item.medications.map(d => displayDosage(d)).join(' · ') }}
                </p>
              </div>
            </div>

            <!-- Status indicator -->
            <div class="flex shrink-0 items-center gap-2">
              <span
                v-if="item.intakeStatus !== 'pending'"
                class="rounded-full px-2 py-0.5 text-[11px] font-medium"
                :style="intakeChipStyle(item.intakeStatus)"
              >{{ intakeStatusLabel(item.intakeStatus) }}</span>
              <div
                v-if="item.intakeStatus === 'taken'"
                class="flex h-[24px] w-[24px] items-center justify-center rounded-full"
                style="background: var(--state-success);"
              >
                <iconify-icon icon="solar:check-read" width="14" height="14" style="color: var(--state-success-foreground);" />
              </div>
              <iconify-icon
                v-else-if="item.intakeStatus === 'skipped'"
                icon="solar:close-circle-bold"
                width="24" height="24"
                style="color: var(--state-error);"
              />
              <iconify-icon
                v-else-if="item.intakeStatus === 'half'"
                icon="solar:minus-circle-bold"
                width="24" height="24"
                style="color: #ff9500;"
              />
              <div
                v-else
                class="h-[24px] w-[24px] rounded-full"
                style="border: 1.5px solid var(--border);"
              ></div>
            </div>
          </div>
        </div>

        <!-- Intake action buttons for pending items -->
        <div v-if="pendingItems.length" class="mt-3 space-y-2">
          <div
            v-for="item in pendingItems"
            :key="`action-${item.alarmId}`"
            class="rounded-[12px] p-3"
            style="background: var(--secondary); border: 1px solid var(--border);"
          >
            <p class="mb-2 text-[13px] font-medium" style="color: var(--foreground);">
              {{ item.time }} · {{ item.medications[0]?.name }}
            </p>
            <div class="flex gap-2">
              <button
                type="button"
                class="flex h-10 flex-1 items-center justify-center gap-1.5 rounded-full text-[13px] font-semibold transition active:scale-[0.98]"
                :class="elderMode ? '!h-12 !text-[15px]' : ''"
                style="background: var(--state-success); color: var(--state-success-foreground);"
                :disabled="confirmingId === item.alarmId"
                @click="confirmIntake(item.alarmId, 'taken')"
              >
                <iconify-icon icon="solar:check-circle-bold" width="16" height="16" />
                已服用
              </button>
              <button
                type="button"
                class="flex h-10 flex-1 items-center justify-center rounded-full text-[13px] font-semibold transition active:scale-[0.98]"
                :class="elderMode ? '!h-12 !text-[15px]' : ''"
                style="background: var(--background-300); color: var(--foreground);"
                :disabled="confirmingId === item.alarmId"
                @click="confirmIntake(item.alarmId, 'half')"
              >
                半片
              </button>
              <button
                type="button"
                class="flex h-10 flex-1 items-center justify-center rounded-full text-[13px] font-medium transition active:scale-[0.98]"
                :class="elderMode ? '!h-12 !text-[15px]' : ''"
                style="background: transparent; color: var(--muted-foreground); border: 1px solid var(--border);"
                :disabled="confirmingId === item.alarmId"
                @click="confirmIntake(item.alarmId, 'skipped')"
              >
                跳过
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- DDI Warning (Apple style) -->
      <div v-if="ddiWarnings.length" class="space-y-2">
        <div
          v-for="(w, i) in ddiWarnings"
          :key="i"
          class="flex items-start gap-2 rounded-[12px] px-4 py-3"
          style="background: var(--state-error-surface);"
        >
          <iconify-icon icon="solar:danger-triangle-outline" width="18" height="18" class="mt-0.5 shrink-0" style="color: var(--state-error);" />
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-1.5">
              <span
                class="rounded-full px-2 py-0.5 text-[11px] font-medium"
                :style="ddiSeverityStyle(w.severity)"
              >{{ ddiSeverityLabel(w.severity) }}</span>
              <span class="text-[13px] font-medium" style="color: var(--foreground);">{{ w.drugA }} + {{ w.drugB }}</span>
            </div>
            <p class="mt-1 text-[12px] leading-5" style="color: var(--foreground);">{{ w.description }}</p>
            <p class="mt-0.5 text-[12px] leading-5" style="color: var(--brand-500);">建议：{{ w.recommendation }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Tab: My Medications (Apple list style) -->
    <div v-if="activeTab === 'medications'" class="mx-auto mt-4 max-w-[420px] space-y-3 px-4">
      <!-- Medication list card -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">用药闹钟</h2>
          <button
            type="button"
            class="flex h-8 items-center gap-1 rounded-full px-3 text-[13px] font-semibold transition active:scale-[0.98]"
            style="background: var(--brand-500); color: var(--primary-foreground);"
            @click="openCreatePage"
          >
            <iconify-icon icon="solar:add-circle-outline" width="14" height="14" />
            新建
          </button>
        </div>

        <ClinicalStateNotice
          v-if="!alarmList.length"
          tone="empty"
          title="暂无用药闹钟"
          description="先创建一个闹钟，再把不同药品放进同一提醒里。"
        />

        <div v-else class="mt-2">
          <div
            v-for="(alarm, idx) in alarmList"
            :key="alarm.id"
            class="flex items-center gap-3 py-[14px]"
            :class="{ 'border-b': idx < alarmList.length - 1 }"
            style="border-color: var(--border);"
          >
            <!-- Time avatar -->
            <div
              class="flex h-[40px] w-[40px] shrink-0 items-center justify-center rounded-full"
              :style="alarm.enabled
                ? { background: 'var(--brand-50)', color: 'var(--brand-500)' }
                : { background: 'var(--secondary)', color: 'var(--muted-foreground)' }"
            >
              <span class="text-[13px] font-semibold tabular-nums">{{ alarm.time }}</span>
            </div>

            <div class="min-w-0 flex-1">
              <p class="truncate text-[15px] font-medium" style="color: var(--foreground);">
                {{ alarm.medications.map(m => m.name).join('、') }}
              </p>
              <p class="truncate text-[13px]" style="color: var(--muted-foreground);">
                {{ alarm.medications.length }} 种药品 · {{ alarm.enabled ? '已启用' : '已暂停' }}
              </p>
            </div>

            <!-- Action icons -->
            <div class="flex shrink-0 items-center gap-1">
              <button
                type="button"
                class="flex h-8 w-8 items-center justify-center rounded-full transition active:scale-95"
                style="color: var(--muted-foreground);"
                aria-label="编辑闹钟"
                @click="editAlarm(alarm)"
              >
                <iconify-icon icon="solar:pen-outline" width="16" height="16" />
              </button>
              <button
                type="button"
                class="flex h-8 w-8 items-center justify-center rounded-full transition active:scale-95"
                style="color: var(--muted-foreground);"
                :aria-label="alarm.enabled ? '暂停闹钟' : '启用闹钟'"
                @click="toggleAlarmStatus(alarm)"
              >
                <iconify-icon :icon="alarm.enabled ? 'solar:pause-outline' : 'solar:play-outline'" width="16" height="16" />
              </button>
              <button
                type="button"
                class="flex h-8 w-8 items-center justify-center rounded-full transition active:scale-95"
                style="color: var(--state-error);"
                aria-label="删除闹钟"
                @click="removeAlarm(alarm)"
              >
                <iconify-icon icon="solar:trash-bin-minimalistic-outline" width="16" height="16" />
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- Tab: Scan Recognition -->
    <div v-if="activeTab === 'scan'" class="mx-auto mt-4 max-w-[420px] space-y-3 px-4">
      <!-- Scan trigger card -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">扫描药盒或说明书</h2>
        <p class="mt-1 text-[14px]" style="color: var(--muted-foreground);">拍摄药盒照片，AI 自动识别药品信息并生成老人可读版本。</p>

        <!-- 选图 input：不带 capture -->
        <input
          ref="galleryInput"
          type="file"
          accept="image/*"
          multiple
          class="hidden"
          @change="handleScanUpload"
        />

        <!-- Full-width Apple-style primary button -->
        <button
          type="button"
          class="mt-4 flex h-[52px] w-full items-center justify-center gap-2 rounded-full transition active:scale-[0.98]"
          style="background: var(--brand-500); color: var(--primary-foreground);"
          @click="triggerCameraScan"
        >
          <iconify-icon icon="solar:camera-outline" width="22" height="22" />
          <span class="text-[16px] font-semibold">拍照识别药品</span>
        </button>

        <button
          type="button"
          class="mt-2 flex h-[44px] w-full items-center justify-center gap-2 rounded-full text-[14px] font-medium transition active:scale-[0.98]"
          style="background: var(--secondary); color: var(--foreground);"
          @click="triggerGalleryScan"
        >
          <iconify-icon icon="solar:gallery-add-outline" width="18" height="18" />
          从相册选择
        </button>
      </section>

      <!-- Camera modal -->
      <div
        v-if="showCameraModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        style="background: rgba(0, 0, 0, 0.48);"
        @click.self="closeCameraModal"
      >
        <div
          class="w-full max-w-[360px] overflow-hidden rounded-[19.2px] border p-4"
          style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-lg, 0 25px 50px -12px rgba(0, 0, 0, 0.25));"
        >
          <div class="flex items-center justify-between">
            <div>
              <h3 class="text-[17px] font-semibold" style="color: var(--foreground);">拍摄药盒/说明书</h3>
              <p class="text-[13px]" style="color: var(--muted-foreground);">将药品放入框内并拍照</p>
            </div>
            <button
              type="button"
              class="flex h-8 w-8 items-center justify-center rounded-full transition active:scale-95"
              style="color: var(--muted-foreground);"
              aria-label="关闭相机"
              @click="closeCameraModal"
            >
              <iconify-icon icon="solar:close-circle-outline" width="22" height="22" />
            </button>
          </div>

          <div
            class="relative mt-3 aspect-[4/3] w-full overflow-hidden rounded-[14px]"
            style="background: var(--background-100);"
          >
            <video
              ref="cameraVideo"
              autoplay
              playsinline
              muted
              class="h-full w-full object-cover"
            />
            <div
              v-if="!cameraReady"
              class="absolute inset-0 flex flex-col items-center justify-center gap-2"
              style="color: var(--muted-foreground);"
            >
              <iconify-icon icon="solar:camera-rotate-outline" width="32" height="32" />
              <span class="text-[13px]">正在启动摄像头…</span>
            </div>
          </div>

          <div class="mt-4 flex items-center justify-center gap-4">
            <button
              type="button"
              class="flex h-12 items-center justify-center gap-1.5 rounded-full px-5 text-[14px] font-medium transition active:scale-[0.98]"
              style="background: var(--secondary); color: var(--foreground);"
              @click="closeCameraModal"
            >
              <iconify-icon icon="solar:close-circle-outline" width="18" height="18" />
              取消
            </button>
            <button
              type="button"
              class="flex h-[56px] w-[56px] shrink-0 items-center justify-center rounded-full transition active:scale-[0.96]"
              style="background: var(--brand-500); color: var(--primary-foreground); box-shadow: 0 0 0 4px var(--brand-100, rgba(var(--brand-500-rgb, 59 130 246), 0.2));"
              aria-label="拍照"
              :disabled="!cameraReady"
              @click="captureCameraPhoto"
            >
              <iconify-icon icon="solar:camera-bold" width="26" height="26" />
            </button>
          </div>
        </div>
      </div>

      <!-- Sample images -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">使用示例图片</h2>
            <p class="mt-0.5 text-[13px]" style="color: var(--muted-foreground);">点击任一图片快速测试识别功能</p>
          </div>
          <iconify-icon icon="solar:gallery-outline" width="20" height="20" style="color: var(--muted-foreground);" />
        </div>

        <div class="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-4">
          <button
            v-for="img in sampleImages"
            :key="img.url"
            type="button"
            class="flex flex-col items-center gap-1 rounded-[12px] p-1.5 transition active:scale-[0.98]"
            style="background: var(--secondary); border: 1px solid var(--border);"
            :aria-label="`使用示例图片：${img.name}`"
            @click="useSampleImage(img.url, img.name)"
          >
            <img :src="img.url" :alt="img.name" class="h-16 w-full rounded-[8px] object-cover" />
            <span class="w-full truncate text-center text-[11px]" style="color: var(--foreground);">{{ img.name }}</span>
            <span class="text-[10px]" style="color: var(--muted-foreground);">{{ img.category }}</span>
          </button>
        </div>
      </section>

      <!-- Scan results -->
      <section
        v-if="scanResults.length"
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">识别结果</h2>
          <span class="text-[13px]" style="color: var(--muted-foreground);">{{ scanResults.length }} 项</span>
        </div>

        <div class="mt-3 space-y-3">
          <div
            v-for="(result, idx) in scanResults"
            :key="idx"
            class="rounded-[12px] p-3"
            style="background: var(--secondary); border: 1px solid var(--border);"
          >
            <!-- Drug header -->
            <div class="flex items-center gap-2">
              <div
                class="flex h-[40px] w-[40px] shrink-0 items-center justify-center rounded-full"
                style="background: var(--brand-50); color: var(--brand-500);"
              >
                <span class="text-[16px] font-semibold">{{ result.name.charAt(0) }}</span>
              </div>
              <div class="min-w-0 flex-1">
                <p class="truncate text-[15px] font-medium" style="color: var(--foreground);">{{ result.name }}</p>
                <p class="truncate text-[13px]" style="color: var(--muted-foreground);">
                  用量：{{ result.dosageValue ?? 1 }} {{ result.dosageUnit || '片' }} · {{ result.usage || '饭后' }}
                </p>
              </div>
              <span
                v-if="result.confidence"
                class="rounded-full px-2 py-0.5 text-[11px] font-medium"
                style="background: var(--brand-50); color: var(--brand-500);"
              >{{ Math.round(result.confidence * 100) }}%</span>
            </div>

            <div v-if="result.alias || result.notes" class="mt-2 space-y-1 text-[12px]" style="color: var(--muted-foreground);">
              <p v-if="result.alias">别名：{{ result.alias }}</p>
              <p v-if="result.notes">{{ result.notes }}</p>
            </div>

            <!-- OCR source text (collapsible) -->
            <details v-if="result.sourceText" class="mt-2">
              <summary class="cursor-pointer text-[12px]" style="color: var(--muted-foreground);">查看药盒原文</summary>
              <p class="mt-1 rounded-[8px] p-2 text-[12px] leading-5" style="background: var(--background-100); color: var(--muted-foreground);">{{ result.sourceText }}</p>
            </details>

            <!-- AI explanation -->
            <div v-if="scanExplanations[idx]" class="mt-3 space-y-2">
              <div class="flex items-center gap-2">
                <iconify-icon icon="solar:shield-check-outline" width="16" height="16" style="color: var(--state-success);" />
                <span class="text-[13px] font-medium" style="color: var(--state-success);">AI 用药建议</span>
                <button
                  type="button"
                  class="ml-auto flex h-7 w-7 items-center justify-center rounded-full transition active:scale-95"
                  style="background: var(--state-success-surface); color: var(--state-success);"
                  @click="speakText(scanExplanations[idx].elderFriendlyExplanation)"
                  title="语音朗读"
                >
                  <iconify-icon icon="solar:volume-loud-outline" width="14" height="14" />
                </button>
              </div>

              <div class="rounded-[10px] p-3" style="background: var(--state-success-surface); border: 1px solid var(--state-success);">
                <p
                  :class="elderMode ? 'text-[16px] leading-7' : 'text-[14px] leading-6'"
                  style="color: var(--foreground);"
                >{{ scanExplanations[idx].elderFriendlyExplanation }}</p>
              </div>

              <div v-if="scanExplanations[idx].warnings.length" class="space-y-1">
                <div
                  v-for="(w, i) in scanExplanations[idx].warnings"
                  :key="i"
                  class="flex items-start gap-2 rounded-[8px] px-3 py-1.5 text-[12px]"
                  style="background: #fff4e6; color: #b25c00;"
                >
                  <iconify-icon icon="solar:danger-triangle-outline" width="12" height="12" class="mt-0.5 shrink-0" />
                  {{ w }}
                </div>
              </div>

              <details>
                <summary class="cursor-pointer text-[12px]" style="color: var(--muted-foreground);">查看详细药学信息</summary>
                <pre class="mt-1 whitespace-pre-wrap rounded-[8px] p-2 text-[12px] leading-5" style="background: var(--background-100); color: var(--muted-foreground);">{{ scanExplanations[idx].clinicalParse }}</pre>
              </details>
            </div>

            <!-- Smart alarm suggestion -->
            <div v-if="suggestedSchedules.has(idx)" class="mt-3 rounded-[10px] p-3" style="background: var(--brand-50); border: 1px solid var(--brand-200, var(--brand-500));">
              <div class="mb-2 flex items-center gap-2">
                <iconify-icon icon="solar:bell-bing-outline" width="16" height="16" style="color: var(--brand-500);" />
                <span class="text-[13px] font-medium" style="color: var(--brand-500);">智能提醒建议</span>
              </div>
              <p class="mb-2 text-[12px]" style="color: var(--brand-500);">{{ suggestedSchedules.get(idx)?.reason }}</p>
              <div class="flex flex-wrap gap-2">
                <span
                  v-for="time in suggestedSchedules.get(idx)?.times"
                  :key="time"
                  class="inline-flex items-center gap-1 rounded-full px-3 py-1 text-[13px] font-semibold tabular-nums"
                  style="background: var(--brand-500); color: var(--primary-foreground);"
                >
                  <iconify-icon icon="solar:clock-circle-outline" width="12" height="12" />
                  {{ time }}
                </span>
              </div>
              <button
                type="button"
                class="mt-2 flex h-8 items-center gap-1 rounded-full px-3 text-[12px] font-semibold transition active:scale-95"
                style="background: var(--card); color: var(--brand-500); border: 1px solid var(--brand-500);"
                @click="createAlarmFromSuggestion(result, suggestedSchedules.get(idx)!)"
              >
                <iconify-icon icon="solar:add-circle-outline" width="14" height="14" />
                一键创建闹钟
              </button>
            </div>

            <div v-else-if="explainingIds.has(idx)" class="mt-3 flex items-center gap-2 text-[12px]" style="color: var(--muted-foreground);">
              <iconify-icon icon="solar:refresh-outline" width="14" height="14" class="animate-spin" />
              AI 正在分析「{{ result.name }}」的用药说明…
            </div>

            <button
              v-else
              type="button"
              class="mt-3 flex h-8 items-center gap-1 rounded-full px-3 text-[12px] font-medium transition active:scale-95"
              style="background: var(--secondary); color: var(--foreground);"
              @click="explainScanned(result, idx)"
            >
              <iconify-icon icon="solar:info-circle-outline" width="14" height="14" />
              生成用药建议
            </button>
          </div>
        </div>
      </section>
    </div>

    <p class="mx-auto mt-4 max-w-[420px] px-4 text-center text-[12px] leading-6" style="color: var(--muted-foreground);">
      用药提醒仅用于执行辅助，不替代医生处方与药学指导。
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { checkDrugInteractions, type DdiWarning } from '@/api/modules/ai'
import {
  confirmMedicationIntake,
  createMedicationAlarm,
  deleteMedicationAlarm,
  explainMedication,
  getMedicationAlarms,
  getTodayMedicationSchedule,
  recognizeMedicationBatch,
  toggleMedicationAlarm,
  type MedicationAlarm,
  type MedicationAlarmDrug,
  type MedicationExplainResponse,
  type MedicationRecognitionResult,
  type TodayScheduleResponse,
} from '@/api/modules/medication'
import { useToast } from '@/composables/useToast'
import { emitMedicationAlarmChangedEvent } from '@/modules/medication/utils/medicationAlarm'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'

const router = useRouter()
const { success, error, warning } = useToast()

// 示例图片：用户下载的真实照片 + 结构化 SVG
const sampleImages = [
  { name: '测试图片 1', url: '/pictures/1.webp', category: '真实照片' },
  { name: '测试图片 2', url: '/pictures/2.webp', category: '真实照片' },
  { name: '测试图片 3-1', url: '/pictures/3-1.jpg', category: '真实照片' },
  { name: '测试图片 3-2', url: '/pictures/3-2.jpg', category: '真实照片' },
  { name: '测试图片 3', url: '/pictures/3.webp', category: '真实照片' },
  { name: '测试图片 4-1', url: '/pictures/4-1.avif', category: '真实照片' },
  { name: '测试图片 5-1', url: '/pictures/5-1.jpg', category: '真实照片' },
  { name: '布洛芬缓释胶囊', url: '/pictures/medication/ibuprofen_box.svg', category: '结构化示例' },
  { name: '盐酸二甲双胍片', url: '/pictures/medication/metformin_box.svg', category: '结构化示例' },
  { name: '六味地黄丸', url: '/pictures/medication/liuheining_box.svg', category: '结构化示例' },
  { name: '桑菊感冒片', url: '/pictures/medication/sangju_box.svg', category: '结构化示例' },
  { name: '甘草饮片', url: '/pictures/medication/gancao_herb.svg', category: '结构化示例' },
  { name: '当归', url: '/pictures/herbs/danggui_herb.svg', category: '中药材示例' },
  { name: '黄芪', url: '/pictures/herbs/huangqi_herb.svg', category: '中药材示例' },
  { name: '枸杞', url: '/pictures/herbs/gouqi_herb.svg', category: '中药材示例' },
  { name: '金银花', url: '/pictures/herbs/jinyinhua_herb.svg', category: '中药材示例' },
]

const tabs = [
  { value: 'today', label: '今日服药', icon: 'solar:calendar-mark-outline' },
  { value: 'scan', label: '扫描识别', icon: 'solar:camera-outline' },
  { value: 'medications', label: '用药闹钟', icon: 'solar:pills-3-outline' },
]

const activeTab = ref('today')
const elderMode = ref(localStorage.getItem('hm_elder_mode') === 'true')
watch(elderMode, (val) => { localStorage.setItem('hm_elder_mode', String(val)) })
const galleryInput = ref<HTMLInputElement | null>(null)
const scanResults = ref<MedicationRecognitionResult[]>([])
const scanExplanations = ref<Record<number, MedicationExplainResponse>>({})
const explainingIds = ref(new Set<number>())
const confirmingId = ref<number | null>(null)
const alarmList = ref<MedicationAlarm[]>([])
const todaySchedule = ref<TodayScheduleResponse>({
  date: '',
  items: [],
  totalCount: 0,
  completedCount: 0,
})

// Camera modal state
const showCameraModal = ref(false)
const cameraVideo = ref<HTMLVideoElement | null>(null)
const cameraReady = ref(false)
const cameraStream = ref<MediaStream | null>(null)

const ddiWarnings = ref<DdiWarning[]>([])

const loadDdiWarnings = async () => {
  const raw = await checkDrugInteractions()
  // 后端 SQL 双向匹配 (A->B OR B->A) 会返回重复条目，按药品对规范化去重
  const seen = new Set<string>()
  ddiWarnings.value = raw.filter((w) => {
    const key = [w.drugA, w.drugB].sort().join('||')
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

const todayProgress = computed(() => {
  if (!todaySchedule.value.totalCount) return 0
  return (todaySchedule.value.completedCount / todaySchedule.value.totalCount) * 100
})

const todayProgressLabel = computed(() => {
  if (!todaySchedule.value.totalCount) return '无计划'
  return `${Math.round(todayProgress.value)}%`
})

const pendingItems = computed(() =>
  todaySchedule.value.items.filter((i) => i.intakeStatus === 'pending')
)

const sortAlarms = (alarms: MedicationAlarm[]) =>
  [...alarms].sort((left, right) => left.time.localeCompare(right.time) || left.id - right.id)

const displayDosage = (drug: Pick<MedicationAlarmDrug, 'dosageValue' | 'dosageUnit'>) => {
  if (!drug.dosageValue || !drug.dosageUnit) return '待确认'
  return `${drug.dosageValue} ${drug.dosageUnit}`
}

const intakeStatusLabel = (status: string) => {
  switch (status) {
    case 'taken': return '已服用'
    case 'skipped': return '已跳过'
    case 'half': return '吃了半片'
    default: return '待服用'
  }
}

const intakeChipStyle = (status: string) => {
  switch (status) {
    case 'taken': return { background: 'var(--state-success-surface)', color: 'var(--state-success)' }
    case 'skipped': return { background: 'var(--state-error-surface)', color: 'var(--state-error)' }
    case 'half': return { background: '#fff4e6', color: '#b25c00' }
    default: return { background: 'var(--secondary)', color: 'var(--muted-foreground)' }
  }
}

const ddiSeverityLabel = (severity: string) => {
  if (severity === 'high') return '高风险'
  if (severity === 'moderate') return '中等风险'
  return '低风险'
}

const ddiSeverityStyle = (severity: string) => {
  if (severity === 'high') return { background: 'var(--state-error)', color: 'var(--state-error-foreground)' }
  if (severity === 'moderate') return { background: '#ff9500', color: '#ffffff' }
  return { background: 'var(--secondary)', color: 'var(--foreground)' }
}

// Scan functions
const triggerGalleryScan = () => galleryInput.value?.click()

const stopCameraStream = () => {
  if (cameraStream.value) {
    cameraStream.value.getTracks().forEach((track) => track.stop())
    cameraStream.value = null
  }
  cameraReady.value = false
}

const closeCameraModal = () => {
  showCameraModal.value = false
  stopCameraStream()
}

const startCameraStream = async () => {
  try {
    cameraReady.value = false
    const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
    cameraStream.value = stream
    if (cameraVideo.value) {
      cameraVideo.value.srcObject = stream
      cameraVideo.value.onloadedmetadata = () => {
        cameraReady.value = true
      }
    } else {
      cameraReady.value = true
    }
  } catch (err) {
    error('无法启动摄像头', err instanceof Error ? err.message : '请检查摄像头权限或设备。')
    closeCameraModal()
  }
}

const triggerCameraScan = () => {
  showCameraModal.value = true
  void startCameraStream()
}

const captureCameraPhoto = async () => {
  const video = cameraVideo.value
  const stream = cameraStream.value
  if (!video || !stream || !cameraReady.value) return

  const canvas = document.createElement('canvas')
  canvas.width = video.videoWidth || 1280
  canvas.height = video.videoHeight || 720
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
  closeCameraModal()

  canvas.toBlob((blob) => {
    if (!blob) return
    const file = new File([blob], `camera-${Date.now()}.jpg`, { type: 'image/jpeg' })
    void runRecognition([file])
  }, 'image/jpeg', 0.92)
}

const runRecognition = async (files: File[]) => {
  if (!files.length) return
  try {
    const result = await recognizeMedicationBatch(files)
    scanResults.value = result.items ?? []
    scanExplanations.value = {}
    suggestedSchedules.value = new Map()
    if (scanResults.value.length) {
      success('识别完成', `共识别 ${scanResults.value.length} 种药品，正在生成用药建议…`)
      suggestedSchedules.value = buildSuggestedSchedules(scanResults.value)
      for (let i = 0; i < scanResults.value.length; i++) {
        void explainScanned(scanResults.value[i], i)
      }
    } else {
      error('识别失败', '未能识别出药品信息，请重新拍照。')
    }
  } catch (err) {
    error('识别失败', err instanceof Error ? err.message : '请稍后重试')
  }
}

const handleScanUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  await runRecognition(files)
  input.value = ''
}

const useSampleImage = async (url: string, name: string) => {
  try {
    const res = await fetch(url)
    const blob = await res.blob()
    const file = new File([blob], name, { type: blob.type || 'image/jpeg' })
    await runRecognition([file])
  } catch (err) {
    warning('示例图片加载失败', err instanceof Error ? err.message : '请稍后重试。')
  }
}

const explainScanned = async (result: MedicationRecognitionResult, index: number) => {
  explainingIds.value.add(index)
  try {
    const notes = [result.notes, result.sourceText].filter(Boolean).join('\n') || undefined
    const explanation = await explainMedication(result.name, notes)
    scanExplanations.value = { ...scanExplanations.value, [index]: explanation }
  } catch {
    // Silent fail — manual retry button available
  } finally {
    const next = new Set(explainingIds.value)
    next.delete(index)
    explainingIds.value = next
  }
}

const speakText = (text: string) => {
  if (!text || typeof window === 'undefined' || !('speechSynthesis' in window)) return
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'zh-CN'
  utterance.rate = elderMode.value ? 0.8 : 1.0
  window.speechSynthesis.speak(utterance)
}

// === Smart alarm interval calculation ===

type SuggestedSchedule = {
  times: string[]
  frequency: number
  intervalHours: number
  reason: string
}

function parseFrequency(usage: string, notes: string): number {
  const text = `${usage} ${notes}`.toLowerCase()

  const cnMatch = text.match(/[一每]?天(\d|[一二三四五六七八九])次/)
  if (cnMatch) {
    const numMap: Record<string, number> = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6 }
    const v = cnMatch[1]
    return numMap[v] ?? (parseInt(v, 10) || 1)
  }

  if (/\bqd\b/.test(text)) return 1
  if (/\bbid\b/.test(text)) return 2
  if (/\btid\b/.test(text)) return 3
  if (/\bqid\b/.test(text)) return 4
  if (/\bq\d+h\b/.test(text)) {
    const h = parseInt(text.match(/q(\d+)h/)?.[1] ?? '24', 10)
    return Math.max(1, Math.round(24 / h))
  }

  const dailyMatch = text.match(/每日\s*(\d)\s*次/)
  if (dailyMatch) return parseInt(dailyMatch[1], 10)

  return 1
}

function calculateSmartTimes(frequency: number): SuggestedSchedule {
  const wakeHour = 7
  const sleepHour = 22
  const activeHours = sleepHour - wakeHour

  if (frequency <= 1) {
    return {
      times: ['08:00'],
      frequency: 1,
      intervalHours: 0,
      reason: '每日一次，建议早餐后固定时间服用',
    }
  }

  const idealInterval = activeHours / frequency
  const interval = Math.max(4, Math.min(6, Math.round(idealInterval)))

  const times: string[] = []
  let currentHour = wakeHour + 0.5

  for (let i = 0; i < frequency && currentHour < sleepHour - 1; i++) {
    const h = Math.floor(currentHour)
    const m = Math.round((currentHour - h) * 60)
    times.push(`${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`)
    currentHour += interval
  }

  return {
    times,
    frequency,
    intervalHours: interval,
    reason: `每日${frequency}次，间隔约${interval}小时，在活动时间内均匀分布`,
  }
}

function buildSuggestedSchedules(results: MedicationRecognitionResult[]): Map<number, SuggestedSchedule> {
  const map = new Map<number, SuggestedSchedule>()
  results.forEach((result, idx) => {
    const freq = parseFrequency(result.usage || '', result.notes || '')
    map.set(idx, calculateSmartTimes(freq))
  })
  return map
}

const suggestedSchedules = ref<Map<number, SuggestedSchedule>>(new Map())

const createAlarmFromSuggestion = async (drug: MedicationRecognitionResult, schedule: SuggestedSchedule) => {
  try {
    for (const time of schedule.times) {
      await createMedicationAlarm({
        time,
        enabled: true,
        medications: [{
          name: drug.name,
          alias: drug.alias || '',
          dosageValue: drug.dosageValue ?? 1,
          dosageUnit: drug.dosageUnit || '片',
          usage: drug.usage || '饭后',
          notes: drug.notes || '',
          photoUrl: drug.photoUrl || '',
          enableOcr: false,
          enableYolo: false,
          ocrEndpoint: '',
          yoloEndpoint: '',
          enabled: true,
        }],
      })
    }
    emitMedicationAlarmChangedEvent()
    success('闹钟已创建', `${drug.name} 每日${schedule.times.length}次提醒已设置`)
    activeTab.value = 'medications'
    void loadAlarms()
  } catch (err) {
    error('创建失败', err instanceof Error ? err.message : '请稍后重试')
  }
}

// Medication list
const loadAlarms = async () => {
  try { alarmList.value = sortAlarms(await getMedicationAlarms()) }
  catch (e) { error('读取失败', e instanceof Error ? e.message : '用药闹钟加载失败。') }
}

const loadTodaySchedule = async () => {
  try { todaySchedule.value = await getTodayMedicationSchedule() } catch { /* best-effort */ }
}

const openCreatePage = () => { void router.push('/medication/alarm') }
const editAlarm = (alarm: MedicationAlarm) => { void router.push({ path: '/medication/alarm', query: { id: String(alarm.id) } }) }

const toggleAlarmStatus = async (alarm: MedicationAlarm) => {
  try {
    const response = await toggleMedicationAlarm(alarm.id)
    alarmList.value = alarmList.value.map((item) => item.id === alarm.id ? { ...item, enabled: response.enabled } : item)
    emitMedicationAlarmChangedEvent()
    success(response.enabled ? '闹钟已启用' : '闹钟已暂停')
  } catch (e) { error('状态更新失败', e instanceof Error ? e.message : '请稍后再试。') }
}

const removeAlarm = async (alarm: MedicationAlarm) => {
  const shouldDelete = typeof window === 'undefined' || typeof window.confirm !== 'function' ? true : window.confirm(`确认删除 ${alarm.time} 的用药闹钟吗？`)
  if (!shouldDelete) return
  try {
    await deleteMedicationAlarm(alarm.id)
    alarmList.value = alarmList.value.filter((item) => item.id !== alarm.id)
    emitMedicationAlarmChangedEvent()
    success('闹钟已删除')
  } catch (e) { error('删除失败', e instanceof Error ? e.message : '请稍后再试。') }
}

const confirmIntake = async (alarmId: number, status: 'taken' | 'skipped' | 'half') => {
  confirmingId.value = alarmId
  try {
    await confirmMedicationIntake(alarmId, status)
    todaySchedule.value = {
      ...todaySchedule.value,
      items: todaySchedule.value.items.map((item) => item.alarmId === alarmId ? { ...item, intakeStatus: status } : item),
      completedCount: todaySchedule.value.items.filter((i) => (i.alarmId === alarmId ? status : i.intakeStatus) !== 'pending').length,
    }
    success('确认成功', intakeStatusLabel(status))
  } catch (e) { error('确认失败', e instanceof Error ? e.message : '请稍后重试') }
  finally { confirmingId.value = null }
}

watch(activeTab, (tab) => {
  if (tab === 'medications') void loadAlarms()
  if (tab === 'today') {
    void loadTodaySchedule()
    void loadDdiWarnings()
  }
})

onMounted(() => {
  void loadTodaySchedule()
  void loadDdiWarnings()
})
</script>

<style scoped>
.elder-mode {
  font-size: 20px;
}
.elder-mode :deep(.text-sm) { font-size: 1.125rem !important; }
.elder-mode :deep(.text-xs) { font-size: 1rem !important; }
.elder-mode :deep(.text-base) { font-size: 1.25rem !important; }
.elder-mode :deep(.text-lg) { font-size: 1.375rem !important; }
.elder-mode :deep(.text-xl) { font-size: 1.5rem !important; }
.elder-mode :deep(.text-2xl) { font-size: 1.75rem !important; }
.elder-mode :deep(.text-\[11px\]) { font-size: 1rem !important; }
.elder-mode :deep(.text-\[12px\]) { font-size: 1.0625rem !important; }
.elder-mode :deep(.text-\[13px\]) { font-size: 1.125rem !important; }
.elder-mode :deep(.text-\[14px\]) { font-size: 1.1875rem !important; }
.elder-mode :deep(.text-\[15px\]) { font-size: 1.25rem !important; }
.elder-mode :deep(.text-\[16px\]) { font-size: 1.375rem !important; }
.elder-mode :deep(.text-\[17px\]) { font-size: 1.4375rem !important; }
.elder-mode :deep(.text-\[20px\]) { font-size: 1.625rem !important; }
.elder-mode :deep(.text-\[24px\]) { font-size: 2rem !important; }
.elder-mode :deep(.text-\[28px\]) { font-size: 2.25rem !important; }

.elder-mode :deep(button) {
  min-height: 52px;
}
.elder-mode :deep(.h-10) { height: 3.25rem !important; }
.elder-mode :deep(.h-12) { height: 3.5rem !important; }

.elder-mode :deep(.gap-1) { gap: 0.5rem !important; }
.elder-mode :deep(.gap-2) { gap: 0.75rem !important; }
.elder-mode :deep(.gap-3) { gap: 1rem !important; }

.elder-mode :deep(.p-3) { padding: 1rem !important; }
.elder-mode :deep(.p-4) { padding: 1.25rem !important; }
.elder-mode :deep(.p-\[18px\]) { padding: 1.5rem !important; }

.elder-mode :deep(.py-3) { padding-top: 1rem !important; padding-bottom: 1rem !important; }
.elder-mode :deep(.py-3\.5) { padding-top: 1.125rem !important; padding-bottom: 1.125rem !important; }

.elder-mode :deep(.rounded-\[10px\]) { border-radius: 14px !important; }
.elder-mode :deep(.rounded-\[12px\]) { border-radius: 16px !important; }
.elder-mode :deep(.rounded-\[19\.2px\]) { border-radius: 24px !important; }

.elder-mode :deep(.leading-5) { line-height: 2 !important; }
.elder-mode :deep(.leading-6) { line-height: 2.25 !important; }
.elder-mode :deep(.leading-7) { line-height: 2.5 !important; }
</style>
