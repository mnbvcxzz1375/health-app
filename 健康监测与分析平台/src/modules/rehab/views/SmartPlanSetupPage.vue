<template>
  <div class="smart-plan pb-8">
    <!-- 页面头部 -->
    <header class="mx-auto max-w-[420px] px-4 pt-6">
      <div class="flex items-center justify-between gap-3">
        <div class="flex items-center gap-3">
          <button
            type="button"
            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full transition active:scale-[0.98]"
            style="background: var(--secondary); color: var(--foreground);"
            aria-label="返回"
            @click="router.back()"
          >
            <iconify-icon icon="solar:alt-arrow-left-outline" width="20" height="20" />
          </button>
          <div>
            <h1 class="text-[24px] font-semibold tracking-[-0.02em]" style="color: var(--foreground); line-height: 1.2;">
              智能健康计划
            </h1>
            <p class="mt-0.5 text-[13px]" style="color: var(--muted-foreground);">
              基于身体参数自动生成个性化训练与饮食计划
            </p>
          </div>
        </div>
        <button
          type="button"
          class="flex shrink-0 items-center gap-1.5 rounded-full px-3 py-2 text-[13px] font-medium transition active:scale-[0.98]"
          style="background: var(--secondary); color: var(--foreground);"
          @click="fillFromDevice"
        >
          <iconify-icon icon="solar:smartwatch-outline" width="18" height="18" />
          从设备读取
        </button>
      </div>
    </header>

    <div class="mx-auto mt-5 max-w-[420px] space-y-3 px-4">
      <!-- 基础信息表单 -->
      <section
        class="rounded-[19.2px] border p-[18px]"
        style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
      >
        <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">基础信息</h2>
        <p class="mt-0.5 text-[13px]" style="color: var(--muted-foreground);">输入身体参数，AI 将据此生成专属计划。</p>

        <div class="mt-4 grid grid-cols-2 gap-3">
          <label class="block">
            <span class="text-[12px]" style="color: var(--muted-foreground);">身高 (cm)</span>
            <input
              v-model.number="form.height"
              type="number"
              min="50"
              max="250"
              class="mt-1 w-full rounded-[12px] border px-3 py-2.5 text-[15px] tabular-nums outline-none transition focus:border-[color:var(--brand-500)]"
              style="background: var(--background); border-color: var(--border); color: var(--foreground);"
            />
          </label>
          <label class="block">
            <span class="text-[12px]" style="color: var(--muted-foreground);">体重 (kg)</span>
            <input
              v-model.number="form.weight"
              type="number"
              min="20"
              max="200"
              class="mt-1 w-full rounded-[12px] border px-3 py-2.5 text-[15px] tabular-nums outline-none transition focus:border-[color:var(--brand-500)]"
              style="background: var(--background); border-color: var(--border); color: var(--foreground);"
            />
          </label>
          <label class="block">
            <span class="text-[12px]" style="color: var(--muted-foreground);">年龄</span>
            <input
              v-model.number="form.age"
              type="number"
              min="1"
              max="120"
              class="mt-1 w-full rounded-[12px] border px-3 py-2.5 text-[15px] tabular-nums outline-none transition focus:border-[color:var(--brand-500)]"
              style="background: var(--background); border-color: var(--border); color: var(--foreground);"
            />
          </label>
          <label class="block">
            <span class="text-[12px]" style="color: var(--muted-foreground);">性别</span>
            <select
              v-model="form.gender"
              class="mt-1 w-full rounded-[12px] border px-3 py-2.5 text-[15px] outline-none transition focus:border-[color:var(--brand-500)]"
              style="background: var(--background); border-color: var(--border); color: var(--foreground);"
            >
              <option value="male">男</option>
              <option value="female">女</option>
            </select>
          </label>
        </div>

        <!-- 训练目标按钮组 -->
        <div class="mt-4">
          <span class="text-[12px]" style="color: var(--muted-foreground);">训练目标</span>
          <div class="mt-1.5 flex flex-wrap gap-2">
            <button
              v-for="opt in goalOptions"
              :key="opt.value"
              type="button"
              class="flex items-center gap-1.5 rounded-full border px-3.5 py-2 text-[13px] font-medium transition active:scale-[0.98]"
              :style="
                form.goal === opt.value
                  ? 'background: var(--brand-500); color: var(--primary-foreground); border-color: var(--brand-500);'
                  : 'background: var(--background); color: var(--foreground); border-color: var(--border);'
              "
              @click="form.goal = opt.value"
            >
              <iconify-icon :icon="opt.icon" width="16" height="16" />
              {{ opt.label }}
            </button>
          </div>
        </div>

        <!-- 日常活动量 -->
        <label class="mt-4 block">
          <span class="text-[12px]" style="color: var(--muted-foreground);">日常活动量</span>
          <select
            v-model="form.activityLevel"
            class="mt-1.5 w-full rounded-[12px] border px-3 py-2.5 text-[15px] outline-none transition focus:border-[color:var(--brand-500)]"
            style="background: var(--background); border-color: var(--border); color: var(--foreground);"
          >
            <option value="sedentary">久坐</option>
            <option value="light">轻度活动</option>
            <option value="moderate">中度活动</option>
            <option value="active">活跃</option>
          </select>
        </label>

        <!-- 生成计划按钮 -->
        <button
          type="button"
          class="mt-5 flex h-[52px] w-full items-center justify-center gap-2 rounded-full text-[16px] font-semibold transition active:scale-[0.98] disabled:opacity-60"
          style="background: var(--brand-500); color: var(--primary-foreground);"
          :disabled="loading"
          @click="handleGenerate"
        >
          <iconify-icon icon="solar:magic-stick-3-bold-duotone" width="20" height="20" />
          {{ loading ? '生成中…' : '生成计划' }}
        </button>
      </section>

      <!-- 结果展示 -->
      <template v-if="result">
        <section
          v-if="result.rehabCase"
          class="rounded-[19.2px] border p-[18px]"
          :style="result.rehabCase.safety.level !== 'routine'
            ? 'background: #fff7ed; border-color: #fed7aa; box-shadow: var(--shadow-xs);'
            : 'background: #f0f9ff; border-color: #bae6fd; box-shadow: var(--shadow-xs);'"
        >
          <div class="flex items-center gap-2">
            <iconify-icon icon="solar:shield-check-outline" width="20" height="20" />
            <h2 class="text-[17px] font-semibold">病例依据与安全边界</h2>
          </div>
          <p class="mt-2 text-[14px] leading-6">{{ result.rehabCase.safety.uncertainty }}</p>
          <p class="mt-1 text-[14px] font-medium leading-6">{{ result.rehabCase.safety.escalation }}</p>
          <div v-if="result.rehabCase.safety.actionTags?.length" class="mt-3 flex flex-wrap gap-1.5">
            <span
              v-for="tag in result.rehabCase.safety.actionTags"
              :key="tag"
              class="rounded-full bg-white/80 px-2.5 py-1 text-[11px] font-medium text-slate-700"
            >
              {{ tag }}
            </span>
          </div>
          <div class="mt-3 grid grid-cols-2 gap-2 text-[12px] text-slate-700">
            <div class="rounded-lg bg-white/70 p-2">
              <p class="text-[11px] text-slate-500">监测基线</p>
              <p class="mt-1 font-medium">
                静息心率 {{ result.rehabCase.monitoring.restingHeartRate }} · 睡眠 {{ result.rehabCase.monitoring.sleepScore }}
              </p>
              <p class="mt-0.5">压力 {{ result.rehabCase.monitoring.stressScore }} · 风险 {{ result.rehabCase.monitoring.riskScore }}</p>
            </div>
            <div class="rounded-lg bg-white/70 p-2">
              <p class="text-[11px] text-slate-500">用药上下文</p>
              <p class="mt-1 font-medium">当前 {{ result.rehabCase.medication.activeCount }} 种药物</p>
              <p class="mt-0.5 truncate">{{ result.rehabCase.medication.names.join('、') || '暂无用药记录' }}</p>
            </div>
          </div>
          <div v-if="result.rehabCase.constraints.length" class="mt-3 space-y-2">
            <article v-for="constraint in result.rehabCase.constraints" :key="constraint.code" class="rounded-xl border border-black/5 bg-white/70 p-2.5">
              <p class="text-[13px] font-semibold">{{ constraint.reason }}</p>
              <p class="mt-1 text-[12px] leading-5">{{ constraint.action }}</p>
            </article>
          </div>
          <p v-if="result.rehabCase.evidence.length" class="mt-3 text-[12px] leading-5 opacity-80">
            已使用 {{ result.rehabCase.evidence.length }} 条病例证据：{{ result.rehabCase.evidence.map(item => item.sourceType).join('、') }}
          </p>
          <div v-if="result.rehabCase.reports.length" class="mt-2 text-[12px] leading-5 opacity-80">
            已纳入 {{ result.rehabCase.reports.length }} 份用户报告：{{ result.rehabCase.reports.map(item => item.title).join('、') }}
          </div>
          <div class="mt-2 text-[12px] leading-5 opacity-80">
            时间范围：{{ result.rehabCase.timeRange.label }}
            <span v-if="result.rehabCase.posture.status !== 'not_available'">
              · 已有姿态评分 {{ result.rehabCase.posture.score ?? '—' }}
            </span>
            <span v-else> · 姿态结果待推理服务返回</span>
          </div>
        </section>
        <!-- 3.1 AI 分析总结 -->
        <section
          class="rounded-[19.2px] border p-[18px]"
          style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
        >
          <div class="flex items-center gap-2">
            <iconify-icon icon="solar:stars-outline" width="20" height="20" style="color: var(--brand-500);" />
            <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">AI 分析总结</h2>
          </div>
          <p class="mt-2 text-[14px] leading-6" style="color: var(--foreground);">{{ result.summary }}</p>
        </section>

        <!-- 3.2 评估指标 -->
        <section
          class="rounded-[19.2px] border p-[18px]"
          style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
        >
          <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">评估指标</h2>
          <div class="mt-3 grid grid-cols-2 gap-3">
            <article
              class="rounded-[12px] border p-3"
              style="background: var(--secondary); border-color: var(--border);"
            >
              <p class="text-[11px] uppercase tracking-[0.08em]" style="color: var(--muted-foreground);">BMI</p>
              <p class="mt-1.5 truncate text-[22px] font-semibold tabular-nums" style="color: var(--foreground);">{{ result.bmi }}</p>
              <p class="mt-0.5 text-[12px]" style="color: var(--muted-foreground);">{{ bmiCategoryLabel(result.bmiCategory) }}</p>
            </article>
            <article
              class="rounded-[12px] border p-3"
              style="background: var(--secondary); border-color: var(--border);"
            >
              <p class="text-[11px] uppercase tracking-[0.08em]" style="color: var(--muted-foreground);">基础代谢</p>
              <p class="mt-1.5 truncate text-[22px] font-semibold tabular-nums" style="color: var(--foreground);">
                {{ result.bmr }} <span class="text-[12px] font-normal" style="color: var(--muted-foreground);">kcal</span>
              </p>
              <p class="mt-0.5 text-[12px]" style="color: var(--muted-foreground);">BMR</p>
            </article>
            <article
              class="rounded-[12px] border p-3"
              style="background: var(--secondary); border-color: var(--border);"
            >
              <p class="text-[11px] uppercase tracking-[0.08em]" style="color: var(--muted-foreground);">总消耗</p>
              <p class="mt-1.5 truncate text-[22px] font-semibold tabular-nums" style="color: var(--foreground);">
                {{ result.tdee }} <span class="text-[12px] font-normal" style="color: var(--muted-foreground);">kcal</span>
              </p>
              <p class="mt-0.5 text-[12px]" style="color: var(--muted-foreground);">TDEE</p>
            </article>
            <article
              class="rounded-[12px] border p-3"
              style="background: var(--secondary); border-color: var(--border);"
            >
              <p class="text-[11px] uppercase tracking-[0.08em]" style="color: var(--muted-foreground);">目标热量</p>
              <p class="mt-1.5 truncate text-[22px] font-semibold tabular-nums" style="color: var(--brand-500);">
                {{ result.targetCalories }} <span class="text-[12px] font-normal" style="color: var(--muted-foreground);">kcal</span>
              </p>
              <p class="mt-0.5 text-[12px]" style="color: var(--muted-foreground);">每日摄入</p>
            </article>
          </div>
        </section>

        <!-- 3.3 周训练计划 -->
        <section
          class="rounded-[19.2px] border p-[18px]"
          style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
        >
          <div class="flex items-center justify-between">
            <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">周训练计划</h2>
            <span class="text-[12px]" style="color: var(--muted-foreground);">
              {{ result.weeklyPlan.filter(d => !d.isRestDay).length }} 天训练 / 7 天
            </span>
          </div>

          <div class="mt-3 space-y-2">
            <div
              v-for="day in result.weeklyPlan"
              :key="day.dayIndex"
              class="overflow-hidden rounded-[12px] border"
              style="background: var(--background); border-color: var(--border);"
            >
              <component
                :is="day.isRestDay ? 'div' : 'button'"
                :type="day.isRestDay ? undefined : 'button'"
                class="flex w-full items-center justify-between gap-3 px-3 py-3 text-left transition active:scale-[0.99]"
                @click="!day.isRestDay && toggleDay(day.dayIndex)"
              >
                <div class="flex min-w-0 items-center gap-3">
                  <span
                    class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full"
                    :style="day.isRestDay
                      ? 'background: var(--secondary); color: var(--muted-foreground);'
                      : 'background: var(--brand-500); color: var(--primary-foreground);'"
                  >
                    <iconify-icon
                      :icon="day.isRestDay ? 'solar:bed-outline' : 'solar:dumbbell-outline'"
                      width="18"
                      height="18"
                    />
                  </span>
                  <div class="min-w-0">
                    <p class="text-[15px] font-semibold" style="color: var(--foreground);">{{ day.day }}</p>
                    <p class="mt-0.5 truncate text-[12px]" :style="day.isRestDay ? 'color: var(--muted-foreground);' : 'color: var(--muted-foreground);'">
                      {{ day.focus }}
                    </p>
                  </div>
                </div>
                <div class="shrink-0 text-right">
                  <template v-if="day.isRestDay">
                    <p class="text-[13px]" style="color: var(--muted-foreground);">休息日</p>
                  </template>
                  <template v-else>
                    <p class="text-[14px] font-semibold tabular-nums" style="color: var(--foreground);">{{ day.duration }} 分钟</p>
                    <p class="mt-0.5 text-[12px] tabular-nums" style="color: var(--muted-foreground);">{{ day.estimatedCalories }} kcal</p>
                  </template>
                </div>
              </component>

              <!-- 展开动作列表 -->
              <div
                v-if="!day.isRestDay && expandedDay === day.dayIndex && day.exercises.length"
                class="border-t px-3 py-2.5"
                style="border-color: var(--border); background: var(--secondary);"
              >
                <div
                  v-for="ex in day.exercises"
                  :key="ex.id"
                  class="flex items-center justify-between gap-2 py-1.5"
                >
                  <div class="min-w-0">
                    <p class="truncate text-[14px] font-medium" style="color: var(--foreground);">{{ ex.name }}</p>
                    <p class="mt-0.5 truncate text-[12px]" style="color: var(--muted-foreground);">{{ ex.muscleGroup }}</p>
                  </div>
                  <span class="shrink-0 text-[12px] tabular-nums" style="color: var(--muted-foreground);">
                    {{ ex.caloriesBurnPerMin }} kcal/min
                  </span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- 3.4 饮食建议 -->
        <section
          class="rounded-[19.2px] border p-[18px]"
          style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
        >
          <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">饮食建议</h2>

          <!-- 顶部指标卡 -->
          <div class="mt-3 grid grid-cols-2 gap-3">
            <article
              class="rounded-[12px] border p-3"
              style="background: var(--secondary); border-color: var(--border);"
            >
              <p class="text-[11px] uppercase tracking-[0.08em]" style="color: var(--muted-foreground);">目标热量</p>
              <p class="mt-1.5 text-[20px] font-semibold tabular-nums" style="color: var(--brand-500);">
                {{ result.dietSuggestion.targetCalories }} <span class="text-[11px] font-normal" style="color: var(--muted-foreground);">kcal</span>
              </p>
            </article>
            <article
              class="rounded-[12px] border p-3"
              style="background: var(--secondary); border-color: var(--border);"
            >
              <p class="text-[11px] uppercase tracking-[0.08em]" style="color: var(--muted-foreground);">蛋白质</p>
              <p class="mt-1.5 text-[20px] font-semibold tabular-nums" style="color: var(--state-success);">
                {{ result.dietSuggestion.targetProtein }} <span class="text-[11px] font-normal" style="color: var(--muted-foreground);">g</span>
              </p>
            </article>
            <article
              class="rounded-[12px] border p-3"
              style="background: var(--secondary); border-color: var(--border);"
            >
              <p class="text-[11px] uppercase tracking-[0.08em]" style="color: var(--muted-foreground);">碳水</p>
              <p class="mt-1.5 text-[20px] font-semibold tabular-nums" style="color: var(--foreground);">
                {{ result.dietSuggestion.targetCarbs }} <span class="text-[11px] font-normal" style="color: var(--muted-foreground);">g</span>
              </p>
            </article>
            <article
              class="rounded-[12px] border p-3"
              style="background: var(--secondary); border-color: var(--border);"
            >
              <p class="text-[11px] uppercase tracking-[0.08em]" style="color: var(--muted-foreground);">脂肪</p>
              <p class="mt-1.5 text-[20px] font-semibold tabular-nums" style="color: var(--state-error);">
                {{ result.dietSuggestion.targetFat }} <span class="text-[11px] font-normal" style="color: var(--muted-foreground);">g</span>
              </p>
            </article>
          </div>

          <!-- 餐次列表 -->
          <div class="mt-3 space-y-2.5">
            <article
              v-for="meal in result.dietSuggestion.meals"
              :key="meal.mealType"
              class="rounded-[12px] border p-3"
              style="background: var(--background); border-color: var(--border);"
            >
              <div class="flex items-center justify-between">
                <p class="text-[15px] font-semibold" style="color: var(--foreground);">{{ meal.title }}</p>
                <span class="text-[13px] font-semibold tabular-nums" style="color: var(--brand-500);">{{ meal.calories }} kcal</span>
              </div>
              <ul class="mt-2 space-y-1">
                <li
                  v-for="food in meal.foods"
                  :key="food"
                  class="flex items-start gap-1.5 text-[13px]"
                  style="color: var(--foreground);"
                >
                  <iconify-icon icon="solar:check-circle-outline" width="14" height="14" style="color: var(--state-success); margin-top: 2px;" />
                  <span>{{ food }}</span>
                </li>
              </ul>
              <div
                class="mt-2.5 flex items-center justify-between border-t pt-2 text-[12px] tabular-nums"
                style="border-color: var(--border); color: var(--muted-foreground);"
              >
                <span>热量 {{ meal.calories }} kcal</span>
                <span>蛋白 {{ meal.protein }}g</span>
                <span>碳水 {{ meal.carbs }}g</span>
                <span>脂肪 {{ meal.fat }}g</span>
              </div>
            </article>
          </div>

          <!-- tips -->
          <ul
            v-if="result.dietSuggestion.tips.length"
            class="mt-3 space-y-1.5"
          >
            <li
              v-for="tip in result.dietSuggestion.tips"
              :key="tip"
              class="flex items-start gap-2 text-[13px] leading-5"
              style="color: var(--foreground);"
            >
              <iconify-icon icon="solar:info-circle-outline" width="16" height="16" style="color: var(--muted-foreground); margin-top: 1px;" />
              <span>{{ tip }}</span>
            </li>
          </ul>
        </section>

        <!-- 3.5 推荐动作 -->
        <section
          v-if="result.exercises.length"
          class="rounded-[19.2px] border p-[18px]"
          style="background: var(--card); border-color: var(--border); box-shadow: var(--shadow-xs);"
        >
          <div class="flex items-center justify-between">
            <h2 class="text-[17px] font-semibold" style="color: var(--foreground);">推荐动作</h2>
            <span class="text-[13px] tabular-nums" style="color: var(--muted-foreground);">{{ selectedExerciseIds.length }} / {{ result.exercises.length }} 项</span>
          </div>

          <div class="mt-3 space-y-2">
            <article
              v-for="ex in result.exercises"
              :key="ex.id"
              class="rounded-[12px] border p-3 transition active:scale-[0.99]"
              style="background: var(--secondary); border-color: var(--border);"
            >
              <div class="flex items-start gap-3">
                <input
                  type="checkbox"
                  :checked="selectedExerciseIds.includes(ex.id)"
                  class="mt-1 h-5 w-5 shrink-0 rounded-md accent-[color:var(--brand-500)]"
                  @change="toggleExercise(ex.id)"
                />
                <div class="min-w-0 flex-1">
                  <p class="text-[15px] font-semibold" style="color: var(--foreground);">{{ ex.name }}</p>
                  <div class="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-[12px]" style="color: var(--muted-foreground);">
                    <span>肌群：{{ ex.muscleGroup }}</span>
                    <span>器械：{{ ex.equipment }}</span>
                    <span class="tabular-nums">消耗：{{ ex.caloriesBurnPerMin }} kcal/min</span>
                  </div>
                  <p class="mt-2 text-[13px] leading-5" style="color: var(--foreground);">
                    <span style="color: var(--muted-foreground);">步骤：</span>{{ ex.steps }}
                  </p>
                  <p class="mt-1 text-[13px] leading-5" style="color: var(--foreground);">
                    <span style="color: var(--muted-foreground);">益处：</span>{{ ex.benefits }}
                  </p>
                </div>
              </div>
            </article>
          </div>
        </section>

        <!-- 3.6 底部操作按钮 -->
        <div class="space-y-2.5 pt-1">
          <button
            type="button"
            class="flex h-[52px] w-full items-center justify-center gap-2 rounded-full text-[16px] font-semibold transition active:scale-[0.98] disabled:opacity-60"
            style="background: var(--brand-500); color: var(--primary-foreground);"
            :disabled="applying || !selectedExerciseIds.length"
            @click="handleApply"
          >
            <iconify-icon icon="solar:check-circle-outline" width="20" height="20" />
            {{ applying ? '应用中…' : '应用为今日计划' }}
          </button>
          <button
            type="button"
            class="flex h-[52px] w-full items-center justify-center gap-2 rounded-full text-[16px] font-semibold transition active:scale-[0.98]"
            style="background: var(--secondary); color: var(--foreground);"
            @click="router.push('/rehab/weekly-progress')"
          >
            <iconify-icon icon="solar:graph-up-outline" width="20" height="20" />
            查看每周进度
          </button>
          <button
            type="button"
            class="flex h-[52px] w-full items-center justify-center gap-2 rounded-full text-[16px] font-semibold transition active:scale-[0.98]"
            style="background: var(--secondary); color: var(--foreground);"
            @click="router.push('/rehab/reminder')"
          >
            <iconify-icon icon="solar:bell-outline" width="20" height="20" />
            设置每日提醒
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  applySmartPlan,
  generateSmartPlan,
  saveWeeklyPlanToLocal,
  type SmartPlanRequest,
  type SmartPlanResponse,
  type SmartPlanGoal,
  type SmartPlanActivityLevel,
} from '@/api/modules/rehab'
import { useToast } from '@/composables/useToast'

const router = useRouter()
const { success, error } = useToast()

const form = ref<SmartPlanRequest>({
  height: 170,
  weight: 65,
  age: 30,
  gender: 'male',
  goal: 'auto',
  activityLevel: 'moderate',
  source: 'manual',
})

const loading = ref(false)
const applying = ref(false)
const result = ref<SmartPlanResponse | null>(null)
const selectedExerciseIds = ref<number[]>([])
const expandedDay = ref<number | null>(null)

const goalOptions: { value: SmartPlanGoal; label: string; icon: string }[] = [
  { value: 'fat_loss', label: '减脂', icon: 'solar:fire-outline' },
  { value: 'muscle_gain', label: '增肌', icon: 'solar:dumbbell-outline' },
  { value: 'body_shaping', label: '塑形', icon: 'solar:body-outline' },
  { value: 'maintenance', label: '保持健康', icon: 'solar:heart-outline' },
  { value: 'rehab', label: '康复', icon: 'solar:shield-check-outline' },
  { value: 'auto', label: 'AI 自动分析', icon: 'solar:magic-stick-outline' },
]

const bmiCategoryLabel = (category: string): string => {
  switch (category) {
    case 'underweight':
      return '偏瘦'
    case 'normal':
      return '正常'
    case 'overweight':
      return '偏胖'
    case 'obese':
      return '肥胖'
    default:
      return category
  }
}

const toggleDay = (dayIndex: number): void => {
  expandedDay.value = expandedDay.value === dayIndex ? null : dayIndex
}

const toggleExercise = (id: number): void => {
  const idx = selectedExerciseIds.value.indexOf(id)
  if (idx >= 0) {
    selectedExerciseIds.value.splice(idx, 1)
  } else {
    selectedExerciseIds.value.push(id)
  }
}

const fillFromDevice = (): void => {
  form.value.source = 'device'
  form.value.height = 170
  form.value.weight = 65
  form.value.age = 30
  form.value.gender = 'male'
  success('已从设备读取身体数据')
}

const handleGenerate = async (): Promise<void> => {
  loading.value = true
  try {
    const res = await generateSmartPlan({ ...form.value })
    result.value = res
    selectedExerciseIds.value = [...res.exerciseIds]
    expandedDay.value = null
    // 持久化到 localStorage，供提醒页读取今日训练内容
    saveWeeklyPlanToLocal(res.weeklyPlan, form.value.goal)
    success('计划已生成')
  } catch (err) {
    error('生成失败', err instanceof Error ? err.message : '请稍后重试')
  } finally {
    loading.value = false
  }
}

const handleApply = async (): Promise<void> => {
  if (!selectedExerciseIds.value.length) return
  applying.value = true
  try {
    await applySmartPlan(selectedExerciseIds.value)
    success('已应用为今日计划')
    router.push('/rehab')
  } catch (err) {
    error('应用失败', err instanceof Error ? err.message : '请稍后重试')
  } finally {
    applying.value = false
  }
}
</script>
