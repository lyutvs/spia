import { describe, it, expect } from 'vitest';
import { post, get } from './util/http';
import { stableStringify } from './util/normalize';
import type { Animal } from '../src/generated/api-sdk';

describe('Case 1: Animal sealed hierarchy (NAME mode)', () => {
  it('Dog round-trip preserves discriminator and fields', async () => {
    const original: Animal = { type: 'dog', name: 'Rex', breed: 'Husky' };
    const echoed = await post<Animal>('/poly/animals', original);
    expect(echoed).toEqual(original);
    expect(stableStringify(echoed)).toBe(stableStringify(original));
    if (echoed.type === 'dog') {
      // breed is dog-specific — must be visible only inside this branch
      expect(echoed.breed).toBe('Husky');
    } else {
      throw new Error(`expected dog, got ${echoed.type}`);
    }
  });

  it('Cat round-trip', async () => {
    const original: Animal = { type: 'cat', name: 'Whiskers', livesLeft: 9 };
    const echoed = await post<Animal>('/poly/animals', original);
    expect(echoed).toEqual(original);
  });

  it('Bird round-trip', async () => {
    const original: Animal = { type: 'bird', name: 'Tweety', wingspanCm: 12.5 };
    const echoed = await post<Animal>('/poly/animals', original);
    expect(echoed).toEqual(original);
  });

  it('backend-originated fixtures round-trip', async () => {
    const fixtures = await get<Animal[]>('/poly/animals/fixtures');
    expect(fixtures).toHaveLength(3);
    for (const fx of fixtures) {
      const echoed = await post<Animal>('/poly/animals', fx);
      expect(echoed).toEqual(fx);
    }
  });
});
