// WCAG 2.x contrast for OKLCH colors, used only by the theme test.

export interface Oklch {
  readonly lightness: number;
  readonly chroma: number;
  readonly hueDegrees: number;
}

const SRGB_LUMINANCE_WEIGHTS = [0.2126, 0.7152, 0.0722] as const;
const WCAG_FLARE = 0.05;

export function contrastRatio(foreground: Oklch, background: Oklch): number {
  const lighter = Math.max(relativeLuminance(foreground), relativeLuminance(background));
  const darker = Math.min(relativeLuminance(foreground), relativeLuminance(background));
  return (lighter + WCAG_FLARE) / (darker + WCAG_FLARE);
}

// OKLCH -> OKLab -> linear sRGB (Björn Ottosson's matrices). Linear sRGB is what WCAG luminance weighs,
// so no gamma step is needed; out-of-gamut channels are clamped like a browser would roughly do.
function relativeLuminance({ lightness, chroma, hueDegrees }: Oklch): number {
  const hue = (hueDegrees * Math.PI) / 180;
  const a = chroma * Math.cos(hue);
  const b = chroma * Math.sin(hue);
  const l = (lightness + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (lightness - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (lightness - 0.0894841775 * a - 1.291485548 * b) ** 3;
  const red = clamp(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s);
  const green = clamp(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s);
  const blue = clamp(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s);
  const [wr, wg, wb] = SRGB_LUMINANCE_WEIGHTS;
  return wr * red + wg * green + wb * blue;
}

function clamp(channel: number): number {
  return Math.min(1, Math.max(0, channel));
}

export function readOklchTokens(css: string): ReadonlyMap<string, Oklch> {
  const tokens = new Map<string, Oklch>();
  for (const match of css.matchAll(/--color-([\w-]+):\s*oklch\(([\d.]+)\s+([\d.]+)\s+([\d.]+)\)/g)) {
    const [, name, lightness, chroma, hue] = match;
    if (name !== undefined && lightness !== undefined && chroma !== undefined && hue !== undefined) {
      tokens.set(name, { lightness: Number(lightness), chroma: Number(chroma), hueDegrees: Number(hue) });
    }
  }
  return tokens;
}
