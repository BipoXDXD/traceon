import { describe, expect, it } from 'vitest';
import type { HealthReport } from './healthContract.ts';
import { deriveSystemStatus } from './systemStatus.ts';
import type { ProbeResults, SystemStatus } from './systemStatus.ts';

type ProbeResult = ProbeResults['live'];

const CHECKED_AT = new Date('2026-10-09T12:00:00Z');

const liveOk: ProbeResult = {
  kind: 'success',
  status: 200,
  data: { status: 'Healthy', checks: [] },
};

function ready(status: number, report: HealthReport): ProbeResult {
  return { kind: 'success', status, data: report };
}

const readyHealthy = ready(200, { status: 'Healthy', checks: [{ name: 'database', status: 'Healthy' }] });
const readyUnhealthy = ready(503, { status: 'Unhealthy', checks: [{ name: 'database', status: 'Unhealthy' }] });

function kindOf(live: ProbeResult, readiness: ProbeResult): SystemStatus['kind'] {
  return deriveSystemStatus({ live, ready: readiness, checkedAt: CHECKED_AT }).kind;
}

describe('deriveSystemStatus', () => {
  it('is operational when the API is live and the database check is healthy', () => {
    expect(deriveSystemStatus({ live: liveOk, ready: readyHealthy, checkedAt: CHECKED_AT })).toEqual({
      kind: 'operational',
      checkedAt: CHECKED_AT,
    });
  });

  it('reports the database as unavailable when readiness answers 503 with an unhealthy database', () => {
    expect(kindOf(liveOk, readyUnhealthy)).toBe('database-unavailable');
  });

  it.each<[string, ProbeResult]>([
    ['a network failure', { kind: 'unreachable', reason: 'network' }],
    ['a timeout', { kind: 'unreachable', reason: 'timeout' }],
    ['a 500 from the proxy', { kind: 'unexpected-status', status: 500 }],
    ['a 502 from the proxy', { kind: 'unexpected-status', status: 502 }],
    ['a 504 from the proxy', { kind: 'unexpected-status', status: 504 }],
  ])('reports the API as unreachable when liveness gets %s', (_description, live) => {
    expect(kindOf(live, { kind: 'unreachable', reason: 'network' })).toBe('api-unreachable');
  });

  it.each<[string, ProbeResult, ProbeResult]>([
    ['liveness body breaks the contract', { kind: 'invalid-body', status: 200 }, readyHealthy],
    ['liveness answers a non-5xx error', { kind: 'unexpected-status', status: 404 }, readyHealthy],
    ['liveness reports Unhealthy with 200', ready(200, { status: 'Unhealthy', checks: [] }), readyHealthy],
    ['readiness body breaks the contract', liveOk, { kind: 'invalid-body', status: 503 }],
    ['readiness answers an unexpected status', liveOk, { kind: 'unexpected-status', status: 502 }],
    ['readiness does not answer while liveness does', liveOk, { kind: 'unreachable', reason: 'timeout' }],
    ['readiness says Unhealthy with 200', liveOk, ready(200, { status: 'Unhealthy', checks: [{ name: 'database', status: 'Unhealthy' }] })],
    ['readiness says Healthy with 503', liveOk, ready(503, { status: 'Healthy', checks: [{ name: 'database', status: 'Healthy' }] })],
    ['readiness overall status contradicts a healthy database check', liveOk, ready(200, { status: 'Unhealthy', checks: [{ name: 'database', status: 'Healthy' }] })],
    ['readiness overall status contradicts an unhealthy database check', liveOk, ready(503, { status: 'Healthy', checks: [{ name: 'database', status: 'Unhealthy' }] })],
    ['readiness has no database check', liveOk, ready(200, { status: 'Healthy', checks: [] })],
    ['readiness is healthy but the database check is not', liveOk, ready(200, { status: 'Healthy', checks: [{ name: 'database', status: 'Unhealthy' }] })],
  ])('reports an unexpected response when %s', (_description, live, readiness) => {
    expect(kindOf(live, readiness)).toBe('unexpected-response');
  });

  it('keeps the check time in every outcome', () => {
    const status = deriveSystemStatus({
      live: { kind: 'unreachable', reason: 'network' },
      ready: { kind: 'unreachable', reason: 'network' },
      checkedAt: CHECKED_AT,
    });

    expect(status.checkedAt).toBe(CHECKED_AT);
  });
});
