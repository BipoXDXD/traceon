// Response contract of GET /health/live and GET /health/ready.
// The body is untrusted: it becomes a HealthReport only through parseHealthReport.

const HEALTH_STATUSES = ['Healthy', 'Unhealthy'] as const;

export type HealthStatus = (typeof HEALTH_STATUSES)[number];

export interface HealthCheck {
  readonly name: string;
  readonly status: HealthStatus;
}

export interface HealthReport {
  readonly status: HealthStatus;
  readonly checks: readonly HealthCheck[];
}

export function parseHealthReport(body: unknown): HealthReport | undefined {
  if (!isRecord(body)) {
    return undefined;
  }
  const status = parseHealthStatus(body.status);
  const checks = body.checks === undefined ? [] : parseChecks(body.checks);
  if (status === undefined || checks === undefined) {
    return undefined;
  }
  return { status, checks };
}

function parseChecks(value: unknown): HealthCheck[] | undefined {
  if (!Array.isArray(value)) {
    return undefined;
  }
  const checks: HealthCheck[] = [];
  for (const item of value as unknown[]) {
    const check = parseCheck(item);
    if (check === undefined) {
      return undefined;
    }
    checks.push(check);
  }
  return checks;
}

function parseCheck(value: unknown): HealthCheck | undefined {
  if (!isRecord(value)) {
    return undefined;
  }
  const name = value.name;
  const status = parseHealthStatus(value.status);
  if (typeof name !== 'string' || name === '' || status === undefined) {
    return undefined;
  }
  return { name, status };
}

function parseHealthStatus(value: unknown): HealthStatus | undefined {
  return HEALTH_STATUSES.find((status) => status === value);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
