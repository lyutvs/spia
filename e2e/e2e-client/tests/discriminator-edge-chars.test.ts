import { describe, it, expect } from 'vitest';
import { post, get } from './util/http';
import type { EdgeNamed } from '../src/generated/api-sdk';

describe('Case 9: discriminator value edge characters (0.4.1 regression)', () => {
  it('round-trips every fixture shipped by the backend', async () => {
    const fixtures = await get<EdgeNamed[]>('/poly/edge/fixtures');
    expect(fixtures.length).toBeGreaterThan(0);
    for (const fx of fixtures) {
      const echoed = await post<EdgeNamed>('/poly/edge', fx);
      expect(echoed).toEqual(fx);
    }
  });
});
