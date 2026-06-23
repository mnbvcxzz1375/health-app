export type RehabExerciseMedia = {
  imageSrc: string
  videoSrc: string
  imageAlt: string
  videoAlt: string
}

const svgToDataUrl = (svg: string) =>
  `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg.replace(/\n\s*/g, ' ').trim())}`

const createBoard = (title: string, subtitle: string, body: string) => `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="720" viewBox="0 0 1200 720" fill="none">
  <rect width="1200" height="720" rx="48" fill="#F3FAF7"/>
  <rect x="56" y="56" width="1088" height="608" rx="36" fill="#FFFFFF" stroke="#D6E7E1" stroke-width="2"/>
  <text x="88" y="120" font-size="32" font-weight="700" fill="#115E59" font-family="Microsoft YaHei, PingFang SC, sans-serif">${title}</text>
  <text x="88" y="160" font-size="18" fill="#64748B" font-family="Microsoft YaHei, PingFang SC, sans-serif">${subtitle}</text>
  <rect x="160" y="520" width="880" height="18" rx="9" fill="#D6E7E1"/>
  ${body}
</svg>`

const birdDogImage = svgToDataUrl(
  createBoard(
    '鸟狗式',
    '四点支撑，对侧手脚向前后延伸',
    `
      <circle cx="500" cy="280" r="34" fill="#0F172A"/>
      <path d="M500 315 C555 350 620 372 700 380" stroke="#0F172A" stroke-width="24" stroke-linecap="round"/>
      <path d="M548 338 L666 280" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
      <path d="M654 388 L748 452" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
      <path d="M500 348 L446 418" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
      <path d="M450 418 L422 498" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
      <path d="M642 394 L620 506" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
    `,
  ),
)

const birdDogVideo = svgToDataUrl(
  createBoard(
    '鸟狗式动态示范',
    '伸展时保持骨盆和躯干稳定',
    `
      <circle cx="500" cy="280" r="34" fill="#0F172A"/>
      <path d="M500 315 C555 350 620 372 700 380" stroke="#0F172A" stroke-width="24" stroke-linecap="round"/>
      <g opacity="1">
        <animate attributeName="opacity" values="1;0;1" dur="2.6s" repeatCount="indefinite"/>
        <path d="M548 338 L666 280" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
        <path d="M654 388 L748 452" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
      </g>
      <g opacity="0">
        <animate attributeName="opacity" values="0;1;0" dur="2.6s" repeatCount="indefinite"/>
        <path d="M548 338 L618 382" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
        <path d="M654 388 L704 438" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
      </g>
      <path d="M500 348 L446 418" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
      <path d="M450 418 L422 498" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
      <path d="M642 394 L620 506" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
    `,
  ),
)

const deadBugImage = svgToDataUrl(
  createBoard(
    '死虫式',
    '仰卧抬手抬腿，维持腹压稳定',
    `
      <circle cx="446" cy="292" r="34" fill="#0F172A"/>
      <path d="M482 292 C560 290 638 296 738 320" stroke="#0F172A" stroke-width="24" stroke-linecap="round"/>
      <path d="M540 280 L612 206" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
      <path d="M556 316 L636 394" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
      <path d="M704 320 L790 236" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
      <path d="M704 320 L804 408" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
    `,
  ),
)

const deadBugVideo = svgToDataUrl(
  createBoard(
    '死虫式动态示范',
    '交替伸展上肢和下肢',
    `
      <circle cx="446" cy="292" r="34" fill="#0F172A"/>
      <path d="M482 292 C560 290 638 296 738 320" stroke="#0F172A" stroke-width="24" stroke-linecap="round"/>
      <g opacity="1">
        <animate attributeName="opacity" values="1;0;1" dur="2.6s" repeatCount="indefinite"/>
        <path d="M540 280 L612 206" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
        <path d="M704 320 L804 408" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
      </g>
      <g opacity="0">
        <animate attributeName="opacity" values="0;1;0" dur="2.6s" repeatCount="indefinite"/>
        <path d="M540 280 L622 382" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
        <path d="M704 320 L790 240" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
      </g>
      <path d="M556 316 L636 394" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
    `,
  ),
)

const hipFlexorImage = svgToDataUrl(
  createBoard(
    '髂腰肌拉伸',
    '弓步位前移重心，感受髋前侧拉伸',
    `
      <circle cx="600" cy="210" r="34" fill="#0F172A"/>
      <path d="M600 246 L606 366" stroke="#0F172A" stroke-width="24" stroke-linecap="round"/>
      <path d="M606 286 L714 320" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
      <path d="M606 286 L544 340" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
      <path d="M606 366 L714 470" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
      <path d="M606 366 L554 474" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
      <path d="M714 470 L808 470" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
    `,
  ),
)

const hipFlexorVideo = svgToDataUrl(
  createBoard(
    '髂腰肌拉伸动态示范',
    '缓慢前移后回正，保持躯干直立',
    `
      <circle cx="600" cy="210" r="34" fill="#0F172A"/>
      <g opacity="1">
        <animate attributeName="opacity" values="1;0;1" dur="2.8s" repeatCount="indefinite"/>
        <path d="M600 246 L606 366" stroke="#0F172A" stroke-width="24" stroke-linecap="round"/>
        <path d="M606 366 L714 470" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
      </g>
      <g opacity="0">
        <animate attributeName="opacity" values="0;1;0" dur="2.8s" repeatCount="indefinite"/>
        <path d="M606 252 L620 372" stroke="#0F172A" stroke-width="24" stroke-linecap="round"/>
        <path d="M620 372 L730 472" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
      </g>
      <path d="M606 286 L714 320" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
      <path d="M606 286 L544 340" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
      <path d="M606 366 L554 474" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
    `,
  ),
)

const bandRowImage = svgToDataUrl(
  createBoard(
    '弹力带划船',
    '肩胛后收下压，肘部贴近身体回拉',
    `
      <circle cx="592" cy="208" r="34" fill="#0F172A"/>
      <path d="M592 244 L594 366" stroke="#0F172A" stroke-width="24" stroke-linecap="round"/>
      <path d="M592 278 L490 250" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
      <path d="M592 278 L696 252" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
      <path d="M594 366 L530 492" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
      <path d="M594 366 L662 492" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
      <path d="M430 240 C470 236 512 240 548 252" stroke="#14B8A6" stroke-width="12" stroke-linecap="round"/>
      <path d="M642 258 C714 236 786 230 852 238" stroke="#14B8A6" stroke-width="12" stroke-linecap="round"/>
    `,
  ),
)

const bandRowVideo = svgToDataUrl(
  createBoard(
    '弹力带划船动态示范',
    '回拉时肩胛发力，回程时放慢节奏',
    `
      <circle cx="592" cy="208" r="34" fill="#0F172A"/>
      <path d="M592 244 L594 366" stroke="#0F172A" stroke-width="24" stroke-linecap="round"/>
      <path d="M594 366 L530 492" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
      <path d="M594 366 L662 492" stroke="#0F172A" stroke-width="22" stroke-linecap="round"/>
      <g opacity="1">
        <animate attributeName="opacity" values="1;0;1" dur="2.6s" repeatCount="indefinite"/>
        <path d="M592 278 L490 250" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
        <path d="M592 278 L696 252" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
      </g>
      <g opacity="0">
        <animate attributeName="opacity" values="0;1;0" dur="2.6s" repeatCount="indefinite"/>
        <path d="M592 278 L538 258" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
        <path d="M592 278 L648 258" stroke="#0F172A" stroke-width="20" stroke-linecap="round"/>
      </g>
      <path d="M430 240 C470 236 512 240 548 252" stroke="#14B8A6" stroke-width="12" stroke-linecap="round"/>
      <path d="M642 258 C714 236 786 230 852 238" stroke="#14B8A6" stroke-width="12" stroke-linecap="round"/>
    `,
  ),
)

const mediaMap: Record<string, RehabExerciseMedia> = {
  鸟狗式: {
    imageSrc: birdDogImage,
    videoSrc: birdDogVideo,
    imageAlt: '鸟狗式动作示意图',
    videoAlt: '鸟狗式动态示范',
  },
  死虫式: {
    imageSrc: deadBugImage,
    videoSrc: deadBugVideo,
    imageAlt: '死虫式动作示意图',
    videoAlt: '死虫式动态示范',
  },
  髂腰肌拉伸: {
    imageSrc: hipFlexorImage,
    videoSrc: hipFlexorVideo,
    imageAlt: '髂腰肌拉伸动作示意图',
    videoAlt: '髂腰肌拉伸动态示范',
  },
  弹力带划船: {
    imageSrc: bandRowImage,
    videoSrc: bandRowVideo,
    imageAlt: '弹力带划船动作示意图',
    videoAlt: '弹力带划船动态示范',
  },
}

export function getRehabExerciseMedia(name: string): RehabExerciseMedia | null {
  return mediaMap[name] ?? null
}
