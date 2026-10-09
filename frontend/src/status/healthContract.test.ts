import { describe, expect, it } from 'vitest';
import { parseHealthReport } from './healthContract.ts';

describe('parseHealthReport', () => {
  it('accepts the liveness body without checks', () => {
    expect(parseHealthReport({ status: 'Healthy' })).toEqual({ status: 'Healthy', checks: [] });
  });

  it('accepts the readiness body with its checks', () => {
    const body = { status: 'Unhealthy', checks: [{ name: 'database', status: 'Unhealthy' }] };

    expect(parseHealthReport(body)).toEqual({
      status: 'Unhealthy',
      checks: [{ name: 'database', status: 'Unhealthy' }],
    });
  });

  it('keeps only the contract fields when the body has extra ones', () => {
    const body = {
      status: 'Healthy',
      totalDuration: '00:00:00.01',
      checks: [{ name: 'database', status: 'Healthy', exception: 'stack' }],
    };

    expect(parseHealthReport(body)).toEqual({
      status: 'Healthy',
      checks: [{ name: 'database', status: 'Healthy' }],
    });
  });

  it.each([
    ['null', null],
    ['a string', 'Healthy'],
    ['an array', [{ status: 'Healthy' }]],
    ['an empty object', {}],
    ['status with the wrong type', { status: 1 }],
    ['an unknown status', { status: 'Degraded' }],
    ['a status in another casing', { status: 'healthy' }],
    ['checks that are not an array', { status: 'Healthy', checks: {} }],
    ['a check without name', { status: 'Healthy', checks: [{ status: 'Healthy' }] }],
    ['a check with an empty name', { status: 'Healthy', checks: [{ name: '', status: 'Healthy' }] }],
    ['a check without status', { status: 'Healthy', checks: [{ name: 'database' }] }],
    [
      'a check with an unknown status',
      { status: 'Healthy', checks: [{ name: 'database', status: 'Up' }] },
    ],
    ['a null check', { status: 'Healthy', checks: [null] }],
  ])('rejects %s', (_description, body) => {
    expect(parseHealthReport(body)).toBeUndefined();
  });
});
