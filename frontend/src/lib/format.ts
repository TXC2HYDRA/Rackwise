export const watts = (w: number) => (w >= 1000 ? `${(w / 1000).toFixed(1)} kW` : `${w} W`)
export const dateTime = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—'
export const pct = (used: number, total: number) => (total > 0 ? Math.round((used / total) * 100) : 0)
