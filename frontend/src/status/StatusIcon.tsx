import type { JSX } from 'react';

export type Tone = 'positive' | 'warning' | 'negative' | 'unknown' | 'pending';

const ICON_PATHS: Record<Tone, string> = {
  positive: 'M5 10.5l3 3 7-7',
  warning: 'M10 6v5M10 14h.01',
  negative: 'M6.5 6.5l7 7M13.5 6.5l-7 7',
  unknown: 'M8 8a2 2 0 1 1 2.8 1.8c-.5.3-.8.7-.8 1.2M10 14h.01',
  pending: 'M10 6v4l2.5 2',
};

const TONE_CLASSES: Record<Tone, string> = {
  positive: 'bg-ok-900 text-ok-300',
  warning: 'bg-warn-900 text-warn-300',
  negative: 'bg-danger-900 text-danger-300',
  unknown: 'bg-ink-700 text-ink-300',
  pending: 'bg-ink-700 text-ink-300',
};

interface StatusIconProps {
  readonly tone: Tone;
}

/** Decorative: every icon sits next to a text that carries the same meaning. */
export function StatusIcon({ tone }: StatusIconProps): JSX.Element {
  return (
    <span className={`inline-flex size-6 shrink-0 items-center justify-center rounded-full ${TONE_CLASSES[tone]}`}>
      <svg
        aria-hidden="true"
        viewBox="0 0 20 20"
        className={`size-4 ${tone === 'pending' ? 'motion-safe:animate-pulse' : ''}`}
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d={ICON_PATHS[tone]} />
      </svg>
    </span>
  );
}
