import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App.tsx';

type Reply = () => Promise<Response>;

const json =
  (body: unknown, status: number): Reply =>
  () =>
    Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
const text =
  (body: string, status: number): Reply =>
  () =>
    Promise.resolve(new Response(body, { status }));
const networkFailure: Reply = () => Promise.reject(new TypeError('Failed to fetch'));
const pending: Reply = () => new Promise<Response>(() => undefined);

const liveHealthy = json({ status: 'Healthy' }, 200);
const readyHealthy = json({ status: 'Healthy', checks: [{ name: 'database', status: 'Healthy' }] }, 200);
const readyUnhealthy = json({ status: 'Unhealthy', checks: [{ name: 'database', status: 'Unhealthy' }] }, 503);

interface ApiReplies {
  readonly live: Reply;
  readonly ready: Reply;
}

/** Each round answers one check (live + ready); the last round repeats. */
function stubApi(...rounds: readonly ApiReplies[]): ReturnType<typeof vi.fn> {
  let liveCalls = 0;
  let readyCalls = 0;
  const roundAt = (index: number): ApiReplies => {
    const round = rounds[Math.min(index, rounds.length - 1)];
    if (round === undefined) {
      throw new Error('stubApi needs at least one round');
    }
    return round;
  };
  const fetchMock = vi.fn((input: RequestInfo | URL) => {
    const url = input instanceof Request ? input.url : input.toString();
    if (url === '/health/live') {
      return roundAt(liveCalls++).live();
    }
    if (url === '/health/ready') {
      return roundAt(readyCalls++).ready();
    }
    return Promise.reject(new Error(`Unexpected request: ${url}`));
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function panel(): HTMLElement {
  return screen.getByRole('region', { name: 'Estado do sistema' });
}

function componentStatus(component: 'API' | 'Banco de dados'): string | null {
  const term = within(panel())
    .getAllByRole('term')
    .find((element) => element.textContent === component);
  return term?.nextElementSibling?.textContent ?? null;
}

async function findSummary(summary: string): Promise<void> {
  await waitFor(() => {
    expect(within(panel()).getByRole('status')).toHaveTextContent(summary);
  });
}

afterEach(() => {
  vi.useRealTimers();
});

describe('App', () => {
  it('presents the product as observation, without promising protection', () => {
    stubApi({ live: pending, ready: pending });

    render(<App />);

    expect(screen.getByRole('heading', { level: 1, name: 'Traceon' })).toBeInTheDocument();
    expect(screen.getByText(/^Traceon observa aplicações web autorizadas/)).toBeInTheDocument();
    expect(document.body).not.toHaveTextContent(/protege|bloqueia|garante|uptime/i);
  });

  it('shows the loading state while the first check is in flight', () => {
    stubApi({ live: pending, ready: pending });

    render(<App />);

    expect(within(panel()).getByRole('status')).toHaveTextContent('Verificando o estado do sistema…');
    expect(panel()).toHaveAttribute('aria-busy', 'true');
    expect(componentStatus('API')).toBe('Verificando…');
    expect(componentStatus('Banco de dados')).toBe('Verificando…');
  });

  it('shows everything available when the API and the database respond', async () => {
    stubApi({ live: liveHealthy, ready: readyHealthy });

    render(<App />);

    await findSummary('API e banco de dados respondendo.');
    expect(componentStatus('API')).toBe('Respondendo');
    expect(componentStatus('Banco de dados')).toBe('Disponível');
    expect(panel()).toHaveAttribute('aria-busy', 'false');
  });

  it('shows the database as unavailable when readiness answers 503', async () => {
    stubApi({ live: liveHealthy, ready: readyUnhealthy });

    render(<App />);

    await findSummary('A API responde, mas o banco de dados está indisponível.');
    expect(componentStatus('API')).toBe('Respondendo');
    expect(componentStatus('Banco de dados')).toBe('Indisponível');
  });

  it.each<[string, ApiReplies]>([
    ['the network fails', { live: networkFailure, ready: networkFailure }],
    ['the proxy answers 502', { live: text('Bad gateway', 502), ready: text('Bad gateway', 502) }],
    ['the proxy answers 500', { live: text('', 500), ready: text('', 500) }],
  ])('shows the API as unreachable when %s', async (_description, replies) => {
    stubApi(replies);

    render(<App />);

    await findSummary('A API não está respondendo.');
    expect(componentStatus('API')).toBe('Não respondendo');
    expect(componentStatus('Banco de dados')).toBe('Desconhecido');
  });

  it('shows an unexpected response when the body breaks the contract', async () => {
    stubApi({ live: text('<!doctype html><html></html>', 200), ready: readyHealthy });

    render(<App />);

    await findSummary('A API respondeu em um formato inesperado.');
    expect(componentStatus('API')).toBe('Resposta inesperada');
    expect(componentStatus('Banco de dados')).toBe('Desconhecido');
  });

  it('shows when the last check happened', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-09T12:34:56.000Z'));
    stubApi({ live: liveHealthy, ready: readyHealthy });

    render(<App />);

    await findSummary('API e banco de dados respondendo.');
    const lastCheck = within(panel()).getByText(/Última verificação/);
    expect(within(lastCheck).getByRole('time')).toHaveAttribute('datetime', '2026-10-09T12:34:56.000Z');
  });

  it('checks again when the user asks for it', async () => {
    const fetchMock = stubApi(
      { live: networkFailure, ready: networkFailure },
      { live: liveHealthy, ready: readyHealthy },
    );
    const user = userEvent.setup();
    render(<App />);
    await findSummary('A API não está respondendo.');

    await user.click(within(panel()).getByRole('button', { name: 'Verificar novamente' }));

    await findSummary('API e banco de dados respondendo.');
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it('ignores a late answer from a check that was replaced by a newer one', async () => {
    let answerFirstCheck: (response: Response) => void = () => undefined;
    const slowLive: Reply = () =>
      new Promise<Response>((resolve) => {
        answerFirstCheck = resolve;
      });
    stubApi({ live: slowLive, ready: readyHealthy }, { live: liveHealthy, ready: readyHealthy });
    const user = userEvent.setup();
    render(<App />);

    await user.click(within(panel()).getByRole('button', { name: 'Verificar novamente' }));
    await findSummary('API e banco de dados respondendo.');
    await act(async () => {
      answerFirstCheck(new Response('Bad gateway', { status: 502 }));
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(within(panel()).getByRole('status')).toHaveTextContent('API e banco de dados respondendo.');
  });

  it('lets the keyboard trigger a new check', async () => {
    const fetchMock = stubApi({ live: liveHealthy, ready: readyHealthy });
    const user = userEvent.setup();
    render(<App />);
    await findSummary('API e banco de dados respondendo.');

    await user.tab();
    expect(within(panel()).getByRole('button', { name: 'Verificar novamente' })).toHaveFocus();
    await user.keyboard('{Enter}');

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(4);
    });
  });
});
