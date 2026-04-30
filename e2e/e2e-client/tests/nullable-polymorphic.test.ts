import { describe, it, expect } from 'vitest';
import { post, get } from './util/http';
import type { AnimalHolder } from '../src/generated/api-sdk';

describe('Case 10: nullable polymorphic field', () => {
  it('present pet round-trips', async () => {
    const original: AnimalHolder = {
      pet: { type: 'dog', name: 'Rex', breed: 'Husky' },
      label: 'with-pet',
    };
    const echoed = await post<AnimalHolder>('/poly/animals/holder', original);
    expect(echoed).toEqual(original);
  });

  it('explicit null pet round-trips as null (or absent due to non_null)', async () => {
    const original: AnimalHolder = { pet: null, label: 'without-pet' };
    const echoed = await post<AnimalHolder>('/poly/animals/holder', original);
    // application.yml sets default-property-inclusion: non_null on the backend,
    // so null fields are stripped from the response.
    expect(echoed.label).toBe('without-pet');
    expect(echoed.pet ?? null).toBe(null);
  });

  it('backend fixtures round-trip', async () => {
    const fixtures = await get<AnimalHolder[]>('/poly/animals/holder/fixtures');
    expect(fixtures).toHaveLength(2);
    for (const fx of fixtures) {
      const echoed = await post<AnimalHolder>('/poly/animals/holder', fx);
      expect(echoed.label).toBe(fx.label);
      expect(echoed.pet ?? null).toEqual(fx.pet ?? null);
    }
  });
});
