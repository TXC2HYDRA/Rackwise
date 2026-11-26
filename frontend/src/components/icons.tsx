const paths = {
  dashboard: 'M3 12l9-9 9 9M5 10v10h5v-6h4v6h5V10',
  inventory: 'M4 6h16M4 12h16M4 18h16M7 6v0M7 12v0M7 18v0',
  requests: 'M9 5h10v14H9zM5 9v10h10M12 9h4M12 13h4',
  plus: 'M12 5v14M5 12h14',
  queue: 'M5 13l4 4L19 7',
  history: 'M12 7v5l3 2M4 12a8 8 0 108-8H8M8 4L4 8',
  audit: 'M12 3l8 3v6c0 4.5-3.2 8-8 9-4.8-1-8-4.5-8-9V6l8-3zM9 12l2 2 4-4',
  admin: 'M4 7h16M4 12h16M4 17h16M9 5v4M15 10v4M8 15v4',
} as const

export type IconName = keyof typeof paths

export function Icon({ name, className = 'h-5 w-5' }: { name: IconName; className?: string }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <path d={paths[name]} />
    </svg>
  )
}
