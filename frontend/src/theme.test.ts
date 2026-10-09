import { describe, expect, it } from 'vitest';
import css from './index.css?raw';
import { contrastRatio, readOklchTokens } from './test/contrast.ts';

const TEXT_MINIMUM = 4.5;
const NON_TEXT_MINIMUM = 3;

const tokens = readOklchTokens(css);

function token(name: string) {
  const value = tokens.get(name);
  if (value === undefined) {
    throw new Error(`Token --color-${name} not found in index.css`);
  }
  return value;
}

const SURFACES = ['ink-950', 'ink-900', 'ink-800'];

describe('theme contrast (WCAG AA)', () => {
  it('sanity-checks the contrast math with black on white', () => {
    const white = { lightness: 1, chroma: 0, hueDegrees: 0 };
    const black = { lightness: 0, chroma: 0, hueDegrees: 0 };

    expect(contrastRatio(black, white)).toBeCloseTo(21, 1);
  });

  it.each(SURFACES.flatMap((surface) => ['ink-100', 'ink-300', 'ink-400'].map((text) => [text, surface])))(
    'text %s on %s reaches 4.5:1',
    (text, surface) => {
      expect(contrastRatio(token(text), token(surface))).toBeGreaterThanOrEqual(TEXT_MINIMUM);
    },
  );

  it.each([
    ['ok-300', 'ok-900'],
    ['warn-300', 'warn-900'],
    ['danger-300', 'danger-900'],
    ['ink-300', 'ink-700'],
    ['brand-200', 'ink-950'],
    ['ink-950', 'brand-300'],
    ['ink-950', 'brand-200'],
    ['ink-950', 'brand-400'],
  ])('text %s on %s reaches 4.5:1', (text, background) => {
    expect(contrastRatio(token(text), token(background))).toBeGreaterThanOrEqual(TEXT_MINIMUM);
  });

  it.each(SURFACES)('focus ring brand-300 on %s reaches 3:1', (surface) => {
    expect(contrastRatio(token('brand-300'), token(surface))).toBeGreaterThanOrEqual(NON_TEXT_MINIMUM);
  });
});
