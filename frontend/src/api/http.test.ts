import { afterEach, describe, expect, it, vi } from 'vitest';
import { getJson } from './http.ts';

const TIMEOUT_MS = 1_000;

function parseName(body: unknown): string | undefined {
  if (typeof body === 'object' && body !== null && 'name' in body && typeof body.name === 'string') {
    return body.name;
  }
  return undefined;
}

function stubFetch(implementation: (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>): void {
  vi.stubGlobal('fetch', vi.fn(implementation));
}

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function hangUntilAborted(_input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  return new Promise((_resolve, reject) => {
    init?.signal?.addEventListener('abort', () => {
      reject(new DOMException('aborted', 'AbortError'));
    });
  });
}

function request(signal = new AbortController().signal) {
  return getJson('/resource', { parse: parseName, acceptedStatuses: [200, 503], signal, timeoutMs: TIMEOUT_MS });
}

afterEach(() => {
  vi.useRealTimers();
});

describe('getJson', () => {
  it('returns the parsed data for an accepted status', async () => {
    stubFetch(() => Promise.resolve(jsonResponse({ name: 'traceon' }, 200)));

    await expect(request()).resolves.toEqual({ kind: 'success', status: 200, data: 'traceon' });
  });

  it('treats a non-2xx status as success when the caller accepts it', async () => {
    stubFetch(() => Promise.resolve(jsonResponse({ name: 'down' }, 503)));

    await expect(request()).resolves.toEqual({ kind: 'success', status: 503, data: 'down' });
  });

  it('requests the given path asking for JSON', async () => {
    const fetchMock = vi.fn<typeof fetch>(() => Promise.resolve(jsonResponse({ name: 'traceon' }, 200)));
    vi.stubGlobal('fetch', fetchMock);

    await request();

    expect(fetchMock).toHaveBeenCalledWith(
      '/resource',
      expect.objectContaining({ method: 'GET', headers: { Accept: 'application/json' } }),
    );
  });

  it.each([404, 500, 502, 504])('reports status %i as unexpected without reading the body', async (status) => {
    stubFetch(() => Promise.resolve(new Response('Bad gateway', { status })));

    await expect(request()).resolves.toEqual({ kind: 'unexpected-status', status });
  });

  it('reports a body that is not JSON as invalid', async () => {
    stubFetch(() => Promise.resolve(new Response('<!doctype html><html></html>', { status: 200 })));

    await expect(request()).resolves.toEqual({ kind: 'invalid-body', status: 200 });
  });

  it('reports JSON that the parser rejects as invalid', async () => {
    stubFetch(() => Promise.resolve(jsonResponse({ name: 42 }, 200)));

    await expect(request()).resolves.toEqual({ kind: 'invalid-body', status: 200 });
  });

  it('reports a network failure as unreachable', async () => {
    stubFetch(() => Promise.reject(new TypeError('Failed to fetch')));

    await expect(request()).resolves.toEqual({ kind: 'unreachable', reason: 'network' });
  });

  it('gives up after the timeout and reports it as unreachable', async () => {
    vi.useFakeTimers();
    stubFetch(hangUntilAborted);

    const pending = request();
    await vi.advanceTimersByTimeAsync(TIMEOUT_MS);

    await expect(pending).resolves.toEqual({ kind: 'unreachable', reason: 'timeout' });
  });

  it('does not give up before the timeout', async () => {
    vi.useFakeTimers();
    stubFetch(hangUntilAborted);
    let settled = false;

    void request().then(() => {
      settled = true;
    });
    await vi.advanceTimersByTimeAsync(TIMEOUT_MS - 1);

    expect(settled).toBe(false);
  });

  it('reports cancellation by the caller separately from failures', async () => {
    stubFetch(hangUntilAborted);
    const controller = new AbortController();

    const pending = request(controller.signal);
    controller.abort();

    await expect(pending).resolves.toEqual({ kind: 'cancelled' });
  });

  it('does not call the network when the caller already cancelled', async () => {
    const fetchMock = vi.fn(hangUntilAborted);
    vi.stubGlobal('fetch', fetchMock);
    const controller = new AbortController();
    controller.abort();

    await expect(request(controller.signal)).resolves.toEqual({ kind: 'cancelled' });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
