<template>
  <div class="apple-rehab pb-6">
    <!-- Page Header -->
    <div class="mx-auto max-w-[420px] px-4 pt-6">
      <div class="flex items-start justify-between">
        <div>
          <h1 class="text-[28px] font-semibold tracking-[-0.02em]" style="color: var(--foreground);">康复训练</h1>
        </div>
        <div class="flex gap-2">
          <button
            type="button"
            class="flex h-8 items-center gap-1 rounded-full px-3 text-[12px] font-medium transition active:scale-[0.98]"
            style="background: var(--secondary); color: var(--foreground);"
            @click="openPlanSettings"
          >
            <iconify-icon icon="solar:settings-outline" width="14" height="14" />
            调整
          </button>
          <button
            type="button"
            class="flex h-8 items-center gap-1 rounded-full px-3 text-[12px] font-semibold transition active:scale-[0.98]"
            style="background: var(--brand-500); color: var(--primary-foreground);"
            @click="goSmartPlan"
          >
            <iconify-icon icon="solar:magic-stick-3-bold-duotone" width="14" height="14" />
            智能计划
          </button>
        </div>
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

    <!-- Tab: Today -->
    <div v-if="activeTab === 'today'" class="mx-auto mt-4 max-w-[420px] space-y-3 px-4">
      <!-- Completion Ring Card -->
      <section
        class="rounded-[19.2px] border p-5"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex flex-col items-center">
          <div class="relative" style="width: 120px; height: 120px;">
            <div
              class="absolute inset-0 rounded-full"
              :style="{ background: `conic-gradient(var(--state-success) 0% ${progress}%, var(--background-200) ${progress}% 100%)` }"
            ></div>
            <div
              class="absolute flex flex-col items-center justify-center rounded-full"
              style="inset: 12px; background: var(--card);"
            >
              <span class="text-[32px] font-semibold leading-none" style="color: var(--foreground);">{{ progress }}%</span>
              <span class="mt-1 text-[12px]" style="color: var(--muted-foreground);">今日完成度</span>
            </div>
          </div>

          <div class="mt-5 flex w-full justify-around gap-4">
            <div class="flex flex-col items-center">
              <span class="whitespace-nowrap text-[20px] font-semibold leading-none tabular-nums" style="color: var(--foreground);">{{ doneCount }}/{{ todayExercises.length || 0 }}</span>
              <span class="mt-1 text-[12px]" style="color: var(--muted-foreground);">已完成</span>
            </div>
            <div class="flex flex-col items-center">
              <span class="whitespace-nowrap text-[20px] font-semibold leading-none tabular-nums" style="color: var(--foreground);">{{ totalMinutes }}</span>
              <span class="mt-1 text-[12px]" style="color: var(--muted-foreground);">总分钟</span>
            </div>
            <div class="flex flex-col items-center">
              <span class="whitespace-nowrap text-[20px] font-semibold leading-none tabular-nums" style="color: var(--foreground);">{{ streakDays }}</span>
              <span class="mt-1 text-[12px]" style="color: var(--muted-foreground);">连续天数</span>
            </div>
          </div>
        </div>
      </section>

      <!-- Today's Training Card -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">今日训练</h2>
          <span class="text-[13px]" style="color: var(--muted-foreground);">{{ todayExercises.length }} 项</span>
        </div>

        <ClinicalStateNotice
          v-if="!todayExercises.length"
          tone="empty"
          title="今日暂无训练动作"
          description="可前往智能计划生成或手动添加。"
        />

        <div v-else class="mt-2">
          <div
            v-for="(ex, idx) in todayExercises"
            :key="ex.id"
            class="flex items-center gap-3 py-3.5"
            :class="{ 'border-b': idx < todayExercises.length - 1 }"
            style="border-color: var(--border);"
          >
            <button
              type="button"
              class="flex min-w-0 flex-1 items-center gap-3 text-left transition active:scale-[0.99]"
              @click="openVideo(ex.name, ex.id)"
            >
              <div
                class="flex h-[48px] w-[48px] shrink-0 items-center justify-center rounded-[12px]"
                :style="ex.done
                  ? { background: 'var(--state-success-surface)', color: 'var(--state-success)' }
                  : { background: 'var(--brand-50)', color: 'var(--brand-500)' }"
              >
                <iconify-icon :icon="ex.done ? 'solar:check-circle-bold' : 'solar:play-outline'" width="22" height="22" />
              </div>

              <div class="min-w-0 flex-1">
                <p class="truncate text-[15px] font-medium" style="color: var(--foreground);">{{ ex.name }}</p>
                <p class="mt-0.5 truncate text-[13px]" style="color: var(--muted-foreground);">
                  {{ ex.duration }} · 聚焦 {{ ex.focus }}
                </p>
                <span
                  class="mt-1.5 inline-flex items-center whitespace-nowrap rounded-full px-2.5 py-0.5 text-[11px]"
                  :style="ex.done
                    ? { background: 'var(--state-success-surface)', color: 'var(--state-success)' }
                    : { background: 'var(--background-200)', color: 'var(--foreground)' }"
                >{{ ex.done ? '已完成' : '待完成' }}</span>
              </div>
            </button>

            <div class="flex shrink-0 items-center gap-1">
              <button
                type="button"
                class="flex h-8 w-8 items-center justify-center rounded-full transition active:scale-95"
                style="color: var(--muted-foreground);"
                aria-label="动作示范"
                @click="openVideo(ex.name, ex.id)"
              >
                <iconify-icon icon="solar:video-frame-play-back-outline" width="16" height="16" />
              </button>
              <button
                type="button"
                class="flex h-8 w-8 items-center justify-center rounded-full transition active:scale-95"
                :aria-label="ex.done ? '取消完成' : '标记完成'"
                @click="toggleDone(ex.id)"
              >
                <iconify-icon
                  :icon="ex.done ? 'solar:check-circle-bold' : 'solar:check-circle-outline'"
                  width="20" height="20"
                  :style="ex.done ? { color: 'var(--state-success)' } : { color: 'var(--muted-foreground)' }"
                />
              </button>
              <button
                type="button"
                class="flex h-8 w-8 items-center justify-center rounded-full transition active:scale-95"
                style="color: var(--state-error);"
                aria-label="删除"
                @click="removeExercise(ex.id)"
              >
                <iconify-icon icon="solar:trash-bin-minimalistic-outline" width="16" height="16" />
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- Plan summary card -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">训练计划</h2>
          <button
            type="button"
            class="text-[13px] font-medium transition active:scale-95"
            style="color: var(--brand-500);"
            @click="openPlanSettings"
          >编辑</button>
        </div>

        <div class="mt-3 grid grid-cols-2 gap-2">
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">重点</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">{{ planSummary.focus }}</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">频次</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">{{ planSummary.frequency }}</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">时长</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">{{ planSummary.duration }}</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">强度</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">{{ planSummary.intensity }}</p>
          </div>
        </div>
      </section>

      <!-- Start Training Button -->
      <button
        type="button"
        class="flex h-[52px] w-full items-center justify-center gap-2 rounded-full transition active:scale-[0.98]"
        style="background: var(--brand-500); color: var(--primary-foreground);"
        @click="goReminderSettings"
      >
        <iconify-icon icon="solar:bell-bing-outline" width="22" height="22" />
        <span class="text-[16px] font-semibold">设置提醒</span>
      </button>

      <!-- Connect Rehab Sensor -->
      <button
        type="button"
        class="flex h-[44px] w-full items-center justify-center gap-2 rounded-full transition active:scale-[0.98]"
        style="background: var(--secondary); color: var(--foreground);"
        @click="router.push('/devices/brands?device_type=rehab_sensor')"
      >
        <iconify-icon icon="solar:body-outline" width="18" height="18" />
        <span class="text-[14px] font-medium">连接康复传感器</span>
      </button>
    </div>

    <!-- Tab: Video Correction -->
    <div v-if="activeTab === 'video'" class="mx-auto mt-4 max-w-[420px] space-y-3 px-4">
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">动作拍摄纠错</h2>
        <p class="mt-1 text-[14px]" style="color: var(--muted-foreground);">录制居家训练动作，系统将返回动作评分、问题段落与分段修正建议。</p>

        <!-- Video preview -->
        <div
          class="mt-3 overflow-hidden rounded-[12px]"
          style="background: #000;"
        >
          <video
            v-if="recordState === 'recorded' && recordedUrl"
            :src="recordedUrl"
            class="h-60 w-full object-cover"
            controls
            playsinline
          />
          <video
            v-else
            ref="cameraVideoRef"
            class="h-60 w-full object-cover"
            autoplay
            muted
            playsinline
          />
        </div>

        <!-- Status -->
        <div class="mt-3 rounded-[10px] p-3" style="background: var(--secondary);">
          <p class="text-[13px] font-semibold" style="color: var(--foreground);">录制状态</p>
          <p class="mt-1 text-[13px] leading-5" style="color: var(--muted-foreground);">
            {{ cameraStatusText }}
            <span v-if="cameraError" style="color: var(--state-error);"> · {{ cameraError }}</span>
          </p>
        </div>

        <!-- Controls -->
        <div class="mt-3 grid grid-cols-2 gap-2">
          <button
            type="button"
            class="flex h-10 items-center justify-center gap-1.5 rounded-full text-[13px] font-medium transition active:scale-[0.98]"
            :style="recordState === 'recording'
              ? { background: 'var(--background-300)', color: 'var(--muted-foreground)' }
              : { background: 'var(--secondary)', color: 'var(--foreground)' }"
            :disabled="recordState === 'recording'"
            @click="requestCamera"
          >
            <iconify-icon icon="solar:camera-outline" width="16" height="16" />
            授权相机
          </button>
          <button
            type="button"
            class="flex h-10 items-center justify-center gap-1.5 rounded-full text-[13px] font-semibold transition active:scale-[0.98]"
            :style="recordState === 'recording' || !isCameraReady
              ? { background: 'var(--background-300)', color: 'var(--muted-foreground)' }
              : { background: 'var(--brand-500)', color: 'var(--primary-foreground)' }"
            :disabled="recordState === 'recording' || !isCameraReady"
            @click="startRecord"
          >
            <iconify-icon icon="solar:video-camera-record-outline" width="16" height="16" />
            开始录制
          </button>
          <button
            type="button"
            class="flex h-10 items-center justify-center gap-1.5 rounded-full text-[13px] font-medium transition active:scale-[0.98]"
            :style="recordState !== 'recording'
              ? { background: 'var(--background-300)', color: 'var(--muted-foreground)' }
              : { background: 'var(--state-error)', color: 'var(--state-error-foreground)' }"
            :disabled="recordState !== 'recording'"
            @click="stopRecord"
          >
            <iconify-icon icon="solar:stop-circle-outline" width="16" height="16" />
            停止
          </button>
          <button
            type="button"
            class="flex h-10 items-center justify-center gap-1.5 rounded-full text-[13px] font-medium transition active:scale-[0.98]"
            :style="recordState !== 'recorded'
              ? { background: 'var(--background-300)', color: 'var(--muted-foreground)' }
              : { background: 'var(--secondary)', color: 'var(--foreground)' }"
            :disabled="recordState !== 'recorded'"
            @click="resetRecording"
          >
            <iconify-icon icon="solar:refresh-outline" width="16" height="16" />
            重录
          </button>
        </div>

        <button
          type="button"
          class="mt-3 flex h-[52px] w-full items-center justify-center gap-2 rounded-full text-[16px] font-semibold transition active:scale-[0.98]"
          :style="recordState !== 'recorded' || videoTaskState === 'uploading' || videoTaskState === 'analyzing'
            ? { background: 'var(--background-300)', color: 'var(--muted-foreground)' }
            : { background: 'var(--brand-500)', color: 'var(--primary-foreground)' }"
          :disabled="recordState !== 'recorded' || videoTaskState === 'uploading' || videoTaskState === 'analyzing'"
          @click="uploadRecordedVideo"
        >
          <iconify-icon
            :icon="videoTaskState === 'uploading' || videoTaskState === 'analyzing' ? 'solar:refresh-outline' : 'solar:upload-outline'"
            width="20" height="20"
            :class="{ 'animate-spin': videoTaskState === 'uploading' || videoTaskState === 'analyzing' }"
          />
          {{ videoTaskState === 'uploading' ? '上传中…' : videoTaskState === 'analyzing' ? '分析中…' : '上传并分析' }}
        </button>

        <!-- Task state -->
        <div class="mt-3 rounded-[10px] p-3" style="background: var(--secondary);">
          <div class="flex items-center justify-between gap-2">
            <p class="text-[13px] font-semibold" style="color: var(--foreground);">分析进度</p>
            <span
              class="rounded-full px-2 py-0.5 text-[11px] font-medium"
              :style="videoTaskStateStyle"
            >{{ videoTaskStateLabel }}</span>
          </div>
          <p class="mt-2 text-[13px] leading-5" style="color: var(--muted-foreground);">
            任务状态：{{ videoTaskStateLabel }}
            <span v-if="videoTaskId" class="text-[11px]">({{ videoTaskId }})</span>
          </p>
        </div>

        <!-- Result -->
        <div
          v-if="videoResult && videoResult.status === 'DONE'"
          class="mt-3 space-y-3 rounded-[12px] p-4"
          style="background: var(--state-success-surface); border: 1px solid var(--state-success);"
        >
          <div class="flex items-center justify-between gap-2">
            <p class="text-[14px] font-semibold" style="color: var(--state-success);">动作评分</p>
            <span
              class="rounded-full px-2.5 py-0.5 text-[13px] font-bold"
              style="background: var(--state-success); color: var(--state-success-foreground);"
            >{{ videoResult.score ?? '--' }}</span>
          </div>

          <div v-if="videoResult.issues?.length" class="space-y-1">
            <p class="text-[11px] uppercase tracking-[0.12em]" style="color: var(--state-success);">发现问题</p>
            <ul class="list-disc space-y-1 pl-5 text-[13px] leading-5" style="color: var(--foreground);">
              <li v-for="item in videoResult.issues" :key="item">{{ item }}</li>
            </ul>
          </div>

          <div v-if="videoResult.tips?.length" class="space-y-1">
            <p class="text-[11px] uppercase tracking-[0.12em]" style="color: var(--state-success);">改进建议</p>
            <ul class="list-disc space-y-1 pl-5 text-[13px] leading-5" style="color: var(--foreground);">
              <li v-for="item in videoResult.tips" :key="item">{{ item }}</li>
            </ul>
          </div>
        </div>

        <!-- Segments -->
        <div
          v-if="videoResult && videoResult.status === 'DONE' && videoResult.segments?.length"
          class="mt-3 space-y-2"
        >
          <article
            v-for="segment in videoResult.segments"
            :key="`${segment.start}-${segment.end}`"
            class="rounded-[10px] p-3"
            style="background: var(--secondary); border: 1px solid var(--border);"
          >
            <p class="text-[11px] uppercase tracking-[0.12em]" style="color: var(--muted-foreground);">{{ segment.start }} - {{ segment.end }}</p>
            <p class="mt-2 text-[13px] font-semibold" style="color: var(--foreground);">{{ segment.issue }}</p>
            <p class="mt-2 text-[13px] leading-5" style="color: var(--muted-foreground);">{{ segment.suggestion }}</p>
          </article>
        </div>
      </section>
    </div>

    <!-- Tab: Trends -->
    <div v-if="activeTab === 'trends'" class="mx-auto mt-4 max-w-[420px] space-y-3 px-4">
      <!-- Weekly chart -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">本周进度</h2>
          <span
            v-if="weekTrend?.deltaPercent !== undefined"
            class="text-[13px] font-semibold"
            :style="{ color: 'var(--state-success)' }"
          >+{{ weekTrend.deltaPercent }}%</span>
        </div>

        <div class="mt-4 h-40 overflow-hidden rounded-[12px]" style="background: var(--secondary);">
          <EChartCanvas :option="weekChartOption" />
        </div>

        <div class="mt-3 rounded-[10px] p-3" style="background: var(--secondary);">
          <p class="text-[12px]" style="color: var(--muted-foreground);">系统洞察</p>
          <p class="mt-1 text-[13px] leading-6" style="color: var(--foreground);">
            {{ weekTrend?.insight ?? '暂无训练趋势数据。' }}
          </p>
        </div>
      </section>

      <!-- Performance Analysis -->
      <section
        v-if="performanceAnalysis"
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">设备数据分析</h2>
        <div class="mt-2 flex items-center gap-2">
          <iconify-icon icon="solar:health-outline" width="16" height="16" style="color: var(--brand-500);" />
          <span class="text-[14px] font-medium" style="color: var(--foreground);">{{ performanceAnalysis.overallAssessment }}</span>
        </div>

        <div class="mt-3 space-y-2">
          <div
            v-for="a in performanceAnalysis.exerciseAnalyses"
            :key="a.exerciseName"
            class="rounded-[10px] p-3"
            :style="performanceLevelStyle(a.performanceLevel)"
          >
            <div class="flex items-center justify-between">
              <span class="text-[14px] font-medium" style="color: var(--foreground);">{{ a.exerciseName }}</span>
              <span
                class="rounded-full px-2 py-0.5 text-[11px] font-medium"
                :style="performanceChipStyle(a.performanceLevel)"
              >{{ performanceLabel(a.performanceLevel) }}</span>
            </div>
            <p class="mt-1 text-[12px]" style="color: var(--muted-foreground);">{{ a.note }}</p>
            <div v-if="a.avgHeartRate > 0" class="mt-1 flex gap-3 text-[11px]" style="color: var(--muted-foreground);">
              <span>平均心率 {{ Math.round(a.avgHeartRate) }} bpm</span>
              <span>最高心率 {{ Math.round(a.maxHeartRate) }} bpm</span>
            </div>
          </div>
        </div>

        <div v-if="performanceAnalysis.warnings.length" class="mt-3 space-y-1">
          <div
            v-for="(w, i) in performanceAnalysis.warnings"
            :key="i"
            class="flex items-start gap-2 rounded-[8px] px-3 py-1.5 text-[12px]"
            style="background: #fff4e6; color: #b25c00;"
          >
            <iconify-icon icon="solar:danger-triangle-outline" width="12" height="12" class="mt-0.5 shrink-0" />
            {{ w }}
          </div>
        </div>

        <div v-if="performanceAnalysis.planAdjustments.length" class="mt-3 rounded-[10px] p-3" style="background: var(--brand-50);">
          <p class="text-[12px] font-semibold" style="color: var(--brand-500);">AI 调整建议</p>
          <p
            v-for="(adj, i) in performanceAnalysis.planAdjustments"
            :key="i"
            class="mt-1 text-[12px] leading-5"
            style="color: var(--brand-500);"
          >{{ adj }}</p>
        </div>
      </section>

      <!-- Reminder summary -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">提醒设置</h2>
          <button
            type="button"
            class="text-[13px] font-medium transition active:scale-95"
            style="color: var(--brand-500);"
            @click="goReminderSettings"
          >编辑</button>
        </div>

        <div class="mt-3 grid grid-cols-2 gap-2">
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">状态</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">{{ reminderSummary.status }}</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">渠道</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">{{ reminderSummary.channel }}</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">时间</p>
            <p class="mt-1 text-[14px] font-semibold tabular-nums" style="color: var(--foreground);">{{ reminderSummary.time }}</p>
          </div>
          <div class="rounded-[10px] p-3" style="background: var(--secondary);">
            <p class="text-[11px]" style="color: var(--muted-foreground);">日期</p>
            <p class="mt-1 text-[14px] font-semibold" style="color: var(--foreground);">{{ reminderSummary.days }}</p>
          </div>
        </div>
      </section>
    </div>

    <!-- Plan Settings Modal (Apple style) -->
    <div v-if="showPlanSettings" class="fixed inset-0 z-50 flex items-end justify-center bg-black/45 p-4 sm:items-center" @click.self="showPlanSettings = false">
      <div
        class="w-full max-w-[420px] rounded-[19.2px] p-5"
        style="background: var(--card); box-shadow: var(--shadow-2xl);"
      >
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="text-[11px] uppercase tracking-[0.18em]" style="color: var(--muted-foreground);">Plan Settings</p>
            <h2 class="mt-2 text-[20px] font-semibold" style="color: var(--foreground);">训练计划设置</h2>
            <p class="mt-1 text-[13px] leading-5" style="color: var(--muted-foreground);">更新重点、频次、时长和强度，新的节奏会立即用于首页与提醒系统。</p>
          </div>
          <button
            type="button"
            class="flex h-8 w-8 items-center justify-center rounded-full transition active:scale-95"
            style="background: var(--secondary); color: var(--muted-foreground);"
            aria-label="关闭"
            @click="showPlanSettings = false"
          >
            <iconify-icon icon="solar:close-circle-outline" width="16" height="16" />
          </button>
        </div>

        <div class="mt-4 grid gap-3 sm:grid-cols-2">
          <label class="block">
            <span class="text-[12px]" style="color: var(--muted-foreground);">训练重点</span>
            <input
              v-model="planSettingsForm.focus"
              class="mt-1 w-full rounded-[10px] px-3 py-2.5 text-[14px] outline-none"
              style="background: var(--secondary); border: 1px solid var(--border); color: var(--foreground);"
            />
          </label>
          <label class="block">
            <span class="text-[12px]" style="color: var(--muted-foreground);">训练频次</span>
            <input
              v-model="planSettingsForm.frequency"
              class="mt-1 w-full rounded-[10px] px-3 py-2.5 text-[14px] outline-none"
              style="background: var(--secondary); border: 1px solid var(--border); color: var(--foreground);"
            />
          </label>
          <label class="block">
            <span class="text-[12px]" style="color: var(--muted-foreground);">单次时长</span>
            <input
              v-model="planSettingsForm.duration"
              class="mt-1 w-full rounded-[10px] px-3 py-2.5 text-[14px] outline-none"
              style="background: var(--secondary); border: 1px solid var(--border); color: var(--foreground);"
            />
          </label>
          <label class="block">
            <span class="text-[12px]" style="color: var(--muted-foreground);">训练强度</span>
            <input
              v-model="planSettingsForm.intensity"
              class="mt-1 w-full rounded-[10px] px-3 py-2.5 text-[14px] outline-none"
              style="background: var(--secondary); border: 1px solid var(--border); color: var(--foreground);"
            />
          </label>
        </div>

        <div class="mt-5 grid grid-cols-2 gap-2.5">
          <button
            type="button"
            class="flex h-11 items-center justify-center rounded-full text-[14px] font-medium transition active:scale-[0.98]"
            style="background: var(--secondary); color: var(--foreground);"
            :disabled="planSettingsSaving"
            @click="showPlanSettings = false"
          >取消</button>
          <button
            type="button"
            class="flex h-11 items-center justify-center rounded-full text-[14px] font-semibold transition active:scale-[0.98]"
            style="background: var(--brand-500); color: var(--primary-foreground);"
            :disabled="planSettingsSaving"
            @click="submitPlanSettings"
          >{{ planSettingsSaving ? '保存中…' : '保存' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { EChartsCoreOption } from 'echarts'
import { useRouter } from 'vue-router'
import {
  createRehabVideoTask,
  getRehabAnalysis,
  getRehabPlan,
  getRehabPlanSettings,
  getRehabVideoTask,
  removeRehabExercise,
  saveRehabPlanSettings,
  toggleRehabExercise,
  type RehabExercise,
  type RehabPerformanceAnalysis,
  type RehabPlanSettings,
  type RehabPlanSummary,
  type RehabReminderSummary,
  type RehabVideoResult,
  type RehabWeekTrend,
} from '@/api/modules/rehab'
import { useToast } from '@/composables/useToast'
import ClinicalStateNotice from '@/shared/components/clinical/ClinicalStateNotice.vue'
import EChartCanvas from '@/shared/components/EChartCanvas.vue'

const router = useRouter()
const { success, warning, error } = useToast()

const tabs = [
  { value: 'today', label: '今日', icon: 'solar:calendar-mark-outline' },
  { value: 'video', label: '视频纠错', icon: 'solar:video-camera-record-outline' },
  { value: 'trends', label: '趋势', icon: 'solar:chart-2-outline' },
]

const activeTab = ref('today')

const todayExercises = ref<RehabExercise[]>([])
const weekTrend = ref<RehabWeekTrend | null>(null)

const planSummary = ref<RehabPlanSummary>({
  focus: '核心稳定',
  frequency: '每周 3 次',
  duration: '单次 20-30 分钟',
  intensity: '低-中',
})

const reminderSummary = ref<RehabReminderSummary>({
  time: '--:--',
  days: '未设置',
  channel: '未开启',
  status: '未设置',
})

const performanceAnalysis = ref<RehabPerformanceAnalysis | null>(null)

const loadAnalysis = async () => {
  try {
    performanceAnalysis.value = await getRehabAnalysis()
  } catch { /* no device data available */ }
}

const performanceLabel = (level: string) => {
  switch (level) {
    case 'excellent': return '优秀'
    case 'good': return '良好'
    case 'overexertion': return '过度运动'
    case 'underperformance': return '强度不足'
    case 'not_completed': return '未完成'
    default: return '无数据'
  }
}

const performanceLevelStyle = (level: string) => {
  if (level === 'overexertion') return { background: 'var(--state-error-surface)', border: '1px solid var(--state-error)' }
  if (level === 'underperformance') return { background: '#fff4e6', border: '1px solid #ff9500' }
  if (level === 'excellent') return { background: 'var(--state-success-surface)', border: '1px solid var(--state-success)' }
  return { background: 'var(--secondary)', border: '1px solid var(--border)' }
}

const performanceChipStyle = (level: string) => {
  if (level === 'overexertion') return { background: 'var(--state-error)', color: 'var(--state-error-foreground)' }
  if (level === 'underperformance') return { background: '#ff9500', color: '#ffffff' }
  if (level === 'excellent') return { background: 'var(--state-success)', color: 'var(--state-success-foreground)' }
  return { background: 'var(--background-300)', color: 'var(--foreground)' }
}

const doneCount = computed(() => todayExercises.value.filter((x) => x.done).length)
const progress = computed(() => {
  if (!todayExercises.value.length) return 0
  return Math.round((doneCount.value / todayExercises.value.length) * 100)
})
const totalMinutes = computed(() => todayExercises.value.reduce((sum, x) => sum + x.minutes, 0))
const streakDays = computed(() => {
  // 计算连续训练天数：基于本周趋势的非零天数
  const values = weekTrend.value?.values ?? []
  let streak = 0
  for (let i = values.length - 1; i >= 0; i -= 1) {
    if (values[i] > 0) streak += 1
    else break
  }
  return streak
})

const cameraVideoRef = ref<HTMLVideoElement | null>(null)
const cameraStream = ref<MediaStream | null>(null)
const mediaRecorder = ref<MediaRecorder | null>(null)
const recordState = ref<'idle' | 'ready' | 'recording' | 'recorded'>('idle')
const cameraError = ref('')

const recordedBlob = ref<Blob | null>(null)
const recordedUrl = ref('')
const recordChunks = ref<Blob[]>([])

const videoTaskId = ref('')
const videoTaskState = ref<'idle' | 'uploading' | 'analyzing' | 'done' | 'failed'>('idle')
const videoResult = ref<RehabVideoResult | null>(null)

const showPlanSettings = ref(false)
const planSettingsSaving = ref(false)
const planSettingsForm = reactive<RehabPlanSettings>({
  focus: '',
  frequency: '',
  duration: '',
  intensity: '',
})

const isCameraReady = computed(() => recordState.value === 'ready' || recordState.value === 'recorded')
const cameraStatusText = computed(() => {
  if (recordState.value === 'recording') return '录制中，请保持动作稳定。'
  if (recordState.value === 'recorded') return '录制完成，可上传进行动作纠错。'
  if (recordState.value === 'ready') return '相机已就绪，可开始录制。'
  return '请先授权相机，再进行动作录制。'
})

const videoTaskStateLabel = computed(() => {
  if (videoTaskState.value === 'uploading') return '上传中'
  if (videoTaskState.value === 'analyzing') return '分析中'
  if (videoTaskState.value === 'done') return '分析完成'
  if (videoTaskState.value === 'failed') return '任务失败'
  return '未开始'
})

const videoTaskStateStyle = computed(() => {
  if (videoTaskState.value === 'done') return { background: 'var(--state-success)', color: 'var(--state-success-foreground)' }
  if (videoTaskState.value === 'failed') return { background: 'var(--state-error)', color: 'var(--state-error-foreground)' }
  if (videoTaskState.value === 'uploading' || videoTaskState.value === 'analyzing') return { background: 'var(--brand-500)', color: 'var(--primary-foreground)' }
  return { background: 'var(--secondary)', color: 'var(--muted-foreground)' }
})

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const loadPlan = async () => {
  try {
    const data = await getRehabPlan()
    todayExercises.value = data.exercises
    weekTrend.value = data.weekTrend
    planSummary.value = data.planSummary
    reminderSummary.value = data.reminderSummary
  } catch (err) {
    warning('加载失败', err instanceof Error ? err.message : '请稍后重试')
  }
}

const loadPlanSettings = async () => {
  try {
    const data = await getRehabPlanSettings()
    Object.assign(planSettingsForm, data)
  } catch {
    Object.assign(planSettingsForm, planSummary.value)
  }
}

const toggleDone = async (id: number) => {
  try {
    const data = await toggleRehabExercise(id)
    todayExercises.value = data.exercises
    weekTrend.value = data.weekTrend
    planSummary.value = data.planSummary
    reminderSummary.value = data.reminderSummary

    if (todayExercises.value.length && doneCount.value === todayExercises.value.length) {
      success('今日训练已完成', '你已达成全部训练目标。')
    }
  } catch (err) {
    warning('更新失败', err instanceof Error ? err.message : '请稍后重试')
  }
}

const removeExercise = async (id: number) => {
  try {
    const data = await removeRehabExercise(id)
    todayExercises.value = data.exercises
    weekTrend.value = data.weekTrend
    planSummary.value = data.planSummary
    reminderSummary.value = data.reminderSummary
    success('动作已移除', '活动清单已更新。')
  } catch (err) {
    error('删除失败', err instanceof Error ? err.message : '请稍后重试。')
  }
}

const openVideo = (name: string, planId: number) => {
  router.push({ path: '/rehab/exercise', query: { name, planId: String(planId) } })
}

const goReminderSettings = () => {
  router.push('/rehab/reminder')
}

const goSmartPlan = () => router.push('/rehab/smart-plan')

const attachCameraStream = () => {
  if (!cameraVideoRef.value || !cameraStream.value) return
  cameraVideoRef.value.srcObject = cameraStream.value
}

const requestCamera = async () => {
  cameraError.value = ''
  if (!navigator.mediaDevices?.getUserMedia) {
    cameraError.value = '当前浏览器不支持相机能力。'
    warning('相机不可用', cameraError.value)
    return
  }

  try {
    if (!cameraStream.value) {
      cameraStream.value = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    }
    attachCameraStream()
    recordState.value = 'ready'
  } catch (err) {
    cameraError.value = err instanceof Error ? err.message : '相机授权失败'
    warning('相机授权失败', cameraError.value)
  }
}

const startRecord = async () => {
  if (!cameraStream.value) {
    await requestCamera()
  }
  if (!cameraStream.value) return

  try {
    recordChunks.value = []
    const recorder = new MediaRecorder(cameraStream.value)
    mediaRecorder.value = recorder

    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        recordChunks.value.push(event.data)
      }
    }

    recorder.onstop = () => {
      const blob = new Blob(recordChunks.value, { type: 'video/webm' })
      recordedBlob.value = blob
      if (recordedUrl.value) {
        URL.revokeObjectURL(recordedUrl.value)
      }
      recordedUrl.value = URL.createObjectURL(blob)
      recordState.value = 'recorded'
    }

    recorder.start()
    recordState.value = 'recording'
    videoResult.value = null
    videoTaskId.value = ''
    videoTaskState.value = 'idle'
  } catch (err) {
    cameraError.value = err instanceof Error ? err.message : '录制失败'
    warning('录制失败', cameraError.value)
  }
}

const stopRecord = () => {
  if (mediaRecorder.value && mediaRecorder.value.state === 'recording') {
    mediaRecorder.value.stop()
  }
}

const resetRecording = () => {
  if (recordedUrl.value) {
    URL.revokeObjectURL(recordedUrl.value)
  }
  recordedUrl.value = ''
  recordedBlob.value = null
  videoResult.value = null
  videoTaskId.value = ''
  videoTaskState.value = 'idle'
  recordState.value = cameraStream.value ? 'ready' : 'idle'
}

const releaseCamera = () => {
  if (cameraStream.value) {
    cameraStream.value.getTracks().forEach((track) => track.stop())
    cameraStream.value = null
  }
  if (cameraVideoRef.value) {
    cameraVideoRef.value.srcObject = null
  }
}

const uploadRecordedVideo = async () => {
  if (!recordedBlob.value) {
    warning('请先录制动作视频')
    return
  }

  try {
    videoTaskState.value = 'uploading'
    videoResult.value = null

    const formData = new FormData()
    formData.append('exerciseName', todayExercises.value[0]?.name ?? '康复动作')
    formData.append('file', new File([recordedBlob.value], 'rehab-record.webm', { type: recordedBlob.value.type || 'video/webm' }))

    const task = await createRehabVideoTask(formData)
    videoTaskId.value = task.taskId
    videoTaskState.value = 'analyzing'

    for (let i = 0; i < 12; i += 1) {
      await sleep(800)
      const latest = await getRehabVideoTask(task.taskId)

      if (latest.status === 'DONE') {
        videoResult.value = latest
        videoTaskState.value = 'done'
        success('动作纠错完成', '已生成纠错建议与分段分析。')
        return
      }

      if (latest.status === 'FAILED') {
        throw new Error(latest.message ?? '动作分析失败')
      }
    }

    videoTaskState.value = 'failed'
    warning('分析超时', '任务仍在处理中，请稍后再试。')
  } catch (err) {
    videoTaskState.value = 'failed'
    warning('上传或分析失败', err instanceof Error ? err.message : '请稍后重试')
  }
}

const openPlanSettings = () => {
  Object.assign(planSettingsForm, planSummary.value)
  showPlanSettings.value = true
}

const submitPlanSettings = async () => {
  planSettingsSaving.value = true
  try {
    const payload: RehabPlanSettings = {
      focus: planSettingsForm.focus.trim(),
      frequency: planSettingsForm.frequency.trim(),
      duration: planSettingsForm.duration.trim(),
      intensity: planSettingsForm.intensity.trim(),
    }
    const saved = await saveRehabPlanSettings(payload)
    planSummary.value = saved
    showPlanSettings.value = false
    success('计划设置已保存', '新的训练设置已生效。')
  } catch (err) {
    warning('保存失败', err instanceof Error ? err.message : '请稍后重试')
  } finally {
    planSettingsSaving.value = false
  }
}

const weekChartOption = computed<EChartsCoreOption>(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 16, right: 16, top: 24, bottom: 20, containLabel: true },
  xAxis: {
    type: 'category',
    data: weekTrend.value?.labels ?? [],
    axisTick: { show: false },
    axisLine: { lineStyle: { color: 'var(--background-300)' } },
    axisLabel: { color: 'var(--muted-foreground)' },
  },
  yAxis: {
    type: 'value',
    axisLine: { show: false },
    splitLine: { lineStyle: { color: 'var(--background-200)' } },
    axisLabel: { color: 'var(--muted-foreground)' },
  },
  series: [
    {
      name: '训练分钟',
      type: 'bar',
      data: weekTrend.value?.values ?? [],
      barWidth: 18,
      itemStyle: { borderRadius: [8, 8, 0, 0], color: '#007aff' },
    },
  ],
}))

onMounted(() => {
  void loadPlan()
  void loadPlanSettings()
  void loadAnalysis()
})

onBeforeUnmount(() => {
  releaseCamera()
  if (recordedUrl.value) {
    URL.revokeObjectURL(recordedUrl.value)
  }
})
</script>
