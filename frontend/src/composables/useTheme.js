import { ref, onMounted, onUnmounted } from 'vue'
import { getUserThemePref, setUserThemePref } from '@/api/user'
import { useUserStore } from '@/store/user'

const DEFAULT_PREF = {
  mode: 'system', theme: 'light',
  schedule: { lightAt: '07:00', darkAt: '19:00' },
  storage: 'account'
}
const LS_KEY = 'themePref'
let _instance = null

export function useTheme() {
  if (_instance) return _instance

  const pref = ref(loadFromLocal())
  const currentTheme = ref(pref.value.theme)
  let scheduleTimer = null
  let sunTimer = null
  let mql = null

  function mergePref(raw) {
    const base = { ...DEFAULT_PREF, ...(raw || {}) }
    base.schedule = { ...DEFAULT_PREF.schedule, ...(raw && raw.schedule || {}) }
    return base
  }
  function loadFromLocal() {
    try {
      const s = localStorage.getItem(LS_KEY)
      return s ? mergePref(JSON.parse(s)) : { ...DEFAULT_PREF, schedule: { ...DEFAULT_PREF.schedule } }
    } catch { return { ...DEFAULT_PREF, schedule: { ...DEFAULT_PREF.schedule } } }
  }
  function saveToLocal() { localStorage.setItem(LS_KEY, JSON.stringify(pref.value)) }

  async function loadFromServer() {
    const user = useUserStore()
    if (!user.isLoggedIn) return
    try {
      const remote = await getUserThemePref()
      if (remote) { pref.value = mergePref(remote); saveToLocal(); apply() }
    } catch {}
  }
  async function saveToServer() {
    if (pref.value.storage !== 'account') return
    const user = useUserStore()
    if (!user.isLoggedIn) return
    try { await setUserThemePref(pref.value) } catch {}
  }

  function apply() { document.documentElement.setAttribute('data-theme', currentTheme.value) }

  function setTheme(t) {
    currentTheme.value = t
    pref.value.theme = t
    saveToLocal(); saveToServer(); apply()
  }
  function setMode(mode, extra = {}) {
    pref.value = { ...pref.value, mode, ...extra }
    saveToLocal(); saveToServer()
    reschedule()
  }
  function reschedule() {
    clearInterval(scheduleTimer); scheduleTimer = null
    clearInterval(sunTimer); sunTimer = null
    if (mql) { mql.removeEventListener('change', onMediaChange); mql = null }

    if (pref.value.mode === 'schedule') {
      const check = () => setTheme(inRange(now(), pref.value.schedule.lightAt, pref.value.schedule.darkAt) ? 'light' : 'dark')
      check(); scheduleTimer = setInterval(check, 60_000)
    } else if (pref.value.mode === 'system') {
      mql = window.matchMedia('(prefers-color-scheme: dark)')
      mql.addEventListener('change', onMediaChange)
      onMediaChange({ matches: mql.matches })
    } else if (pref.value.mode === 'sun') {
      const check = () => {
        const { sunrise, sunset } = beijingSunTimes(new Date())
        setTheme(inRange(now(), sunrise, sunset) ? 'light' : 'dark')
      }
      check(); sunTimer = setInterval(check, 5 * 60_000)
    } else { apply() }
  }
  function onMediaChange(e) { setTheme(e.matches ? 'dark' : 'light') }

  onMounted(async () => { apply(); await loadFromServer(); reschedule() })
  onUnmounted(() => {
    clearInterval(scheduleTimer); clearInterval(sunTimer)
    if (mql) mql.removeEventListener('change', onMediaChange)
  })

  _instance = {
    pref, currentTheme, setTheme,
    toggle: () => setTheme(currentTheme.value === 'dark' ? 'light' : 'dark'),
    setMode,
    setSchedule: (schedule) => setMode('schedule', { schedule })
  }
  return _instance
}

// 工具函数
function now() { const d = new Date(); return d.getHours() * 60 + d.getMinutes() }
function toMin(s) { const [h, m] = s.split(':').map(Number); return h * 60 + m }
function inRange(n, a, b) {
  const [x, y] = [toMin(a), toMin(b)]
  return x <= y ? n >= x && n < y : n >= x || n < y
}

// 北京日出日落（NOAA 简化算法，误差 <2 分钟，无需联网）
function beijingSunTimes(date) {
  const lat = 39.9042, lng = 116.4074, tzOffset = 8
  const rad = Math.PI / 180
  const doy = Math.floor((date - new Date(date.getFullYear(), 0, 0)) / 86400000)
  const gamma = 2 * Math.PI / 365 * (doy - 1)
  const eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma) - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2 * gamma) - 0.040849 * Math.sin(2 * gamma))
  const decl = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma)
             - 0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma)
             - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma)
  const ha = Math.acos(Math.cos(90.833 * rad) / (Math.cos(lat * rad) * Math.cos(decl))
             - Math.tan(lat * rad) * Math.tan(decl)) / rad
  const solarNoon = 720 - 4 * lng - eqTime + tzOffset * 60
  const sunriseMin = Math.floor(solarNoon - 4 * ha)
  const sunsetMin  = Math.floor(solarNoon + 4 * ha)
  const fmt = (m) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`
  return { sunrise: fmt(sunriseMin), sunset: fmt(sunsetMin) }
}
