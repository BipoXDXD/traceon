import type { JSX } from 'react';
import { SystemStatusPanel } from './status/SystemStatusPanel.tsx';

export default function App(): JSX.Element {
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="mx-auto w-full max-w-3xl px-4 pt-8 sm:pt-12">
        <div className="flex items-center gap-3">
          <TraceonMark />
          <h1 className="text-2xl font-semibold tracking-tight text-ink-100">Traceon</h1>
        </div>
      </header>

      <main className="mx-auto w-full max-w-3xl flex-1 px-4 pt-8 pb-16 sm:pt-12">
        <p className="max-w-prose text-xl leading-relaxed text-ink-100 sm:text-2xl">
          Traceon observa aplicações web autorizadas e ajuda a investigar alterações com histórico e evidências.
        </p>
        <p className="mt-4 max-w-prose leading-relaxed text-ink-400">
          Uma alteração observada não significa invasão confirmada. Nesta etapa, a tela mostra apenas o estado da
          API e do banco de dados.
        </p>

        <div className="mt-12">
          <SystemStatusPanel />
        </div>
      </main>

      <footer className="mx-auto w-full max-w-3xl px-4 pb-8 text-sm text-ink-400">
        Etapa Foundation. Nenhum dado demonstrativo: o painel consulta a API real.
      </footer>
    </div>
  );
}

function TraceonMark(): JSX.Element {
  return (
    <svg aria-hidden="true" viewBox="0 0 32 32" className="size-8">
      <rect width="32" height="32" rx="8" className="fill-ink-800" />
      <path
        d="M7 21h5l3-10 3 14 3-8h4"
        fill="none"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="stroke-brand-300"
      />
    </svg>
  );
}
