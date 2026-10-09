import type { JSX } from 'react';
import { describeStatus } from './describeStatus.ts';
import type { ComponentView } from './describeStatus.ts';
import { StatusIcon } from './StatusIcon.tsx';
import { useSystemStatus } from './useSystemStatus.ts';

const TITLE_ID = 'system-status-title';
const timeFormat = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'medium' });

export function SystemStatusPanel(): JSX.Element {
  const { state, recheck } = useSystemStatus();
  const view = describeStatus(state);
  const checking = state.kind === 'checking';

  return (
    <section
      aria-labelledby={TITLE_ID}
      aria-busy={checking}
      className="rounded-xl bg-ink-900 p-4 shadow-lg ring-1 ring-ink-800 ring-inset sm:p-6"
    >
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 id={TITLE_ID} className="text-lg font-semibold text-ink-100">
          Estado do sistema
        </h2>
        <button
          type="button"
          onClick={recheck}
          className="min-h-11 rounded-lg bg-brand-300 px-4 text-base font-semibold text-ink-950 transition-colors hover:bg-brand-200 active:bg-brand-400"
        >
          Verificar novamente
        </button>
      </div>

      <div role="status" className="mt-6 flex items-start gap-3">
        <StatusIcon tone={view.tone} />
        <p>
          <span className="block font-semibold text-ink-100">{view.summary}</span>
          <span className="mt-1 block text-sm leading-relaxed text-ink-400">{view.detail}</span>
        </p>
      </div>

      <dl className="mt-6 grid gap-3 sm:grid-cols-2">
        <ComponentRow name="API" view={view.api} />
        <ComponentRow name="Banco de dados" view={view.database} />
      </dl>

      <p className="mt-6 text-sm text-ink-400">
        Última verificação:{' '}
        {view.lastCheckedAt === undefined ? (
          'ainda não concluída'
        ) : (
          <time dateTime={view.lastCheckedAt.toISOString()} className="text-ink-300">
            {timeFormat.format(view.lastCheckedAt)}
          </time>
        )}
      </p>
    </section>
  );
}

interface ComponentRowProps {
  readonly name: string;
  readonly view: ComponentView;
}

function ComponentRow({ name, view }: ComponentRowProps): JSX.Element {
  return (
    <div className="flex items-center justify-between gap-3 rounded-lg bg-ink-800 p-4">
      <dt className="text-sm text-ink-400">{name}</dt>
      <dd className="flex items-center gap-2 font-semibold text-ink-100">
        <StatusIcon tone={view.tone} />
        <span>{view.label}</span>
      </dd>
    </div>
  );
}
