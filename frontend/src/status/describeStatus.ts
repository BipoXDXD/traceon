import type { Tone } from './StatusIcon.tsx';
import type { SystemStatusState } from './useSystemStatus.ts';

export interface ComponentView {
  readonly label: string;
  readonly tone: Tone;
}

export interface StatusView {
  readonly summary: string;
  readonly detail: string;
  readonly tone: Tone;
  readonly api: ComponentView;
  readonly database: ComponentView;
  readonly lastCheckedAt: Date | undefined;
}

const CHECKING: ComponentView = { label: 'Verificando…', tone: 'pending' };
const DATABASE_UNKNOWN: ComponentView = { label: 'Desconhecido', tone: 'unknown' };

export function describeStatus(state: SystemStatusState): StatusView {
  switch (state.kind) {
    case 'checking':
      return {
        summary: 'Verificando o estado do sistema…',
        detail: 'Consultando a API e o banco de dados.',
        tone: 'pending',
        api: CHECKING,
        database: CHECKING,
        lastCheckedAt: state.lastCheckedAt,
      };
    case 'operational':
      return {
        summary: 'API e banco de dados respondendo.',
        detail: 'As verificações de liveness e readiness passaram.',
        tone: 'positive',
        api: { label: 'Respondendo', tone: 'positive' },
        database: { label: 'Disponível', tone: 'positive' },
        lastCheckedAt: state.checkedAt,
      };
    case 'database-unavailable':
      return {
        summary: 'A API responde, mas o banco de dados está indisponível.',
        detail: 'A verificação de readiness respondeu 503. Confira se o PostgreSQL está em execução.',
        tone: 'warning',
        api: { label: 'Respondendo', tone: 'positive' },
        database: { label: 'Indisponível', tone: 'negative' },
        lastCheckedAt: state.checkedAt,
      };
    case 'api-unreachable':
      return {
        summary: 'A API não está respondendo.',
        detail: 'Sem resposta da API, não é possível saber o estado do banco de dados.',
        tone: 'negative',
        api: { label: 'Não respondendo', tone: 'negative' },
        database: DATABASE_UNKNOWN,
        lastCheckedAt: state.checkedAt,
      };
    case 'unexpected-response':
      return {
        summary: 'A API respondeu em um formato inesperado.',
        detail: 'A resposta não segue o contrato de health check; o estado real não pôde ser confirmado.',
        tone: 'warning',
        api: { label: 'Resposta inesperada', tone: 'warning' },
        database: DATABASE_UNKNOWN,
        lastCheckedAt: state.checkedAt,
      };
    default:
      return state satisfies never;
  }
}
