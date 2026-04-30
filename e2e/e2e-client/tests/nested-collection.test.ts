import { describe, it, expect } from 'vitest';
import { post, get } from './util/http';
import type { Animal, Page } from '../src/generated/api-sdk';

describe('Case 2: List<Animal>', () => {
  it('mixed-type list round-trip preserves order and discriminators', async () => {
    const original: Animal[] = [
      { type: 'dog', name: 'Rex', breed: 'Husky' },
      { type: 'cat', name: 'Whiskers', livesLeft: 9 },
      { type: 'bird', name: 'Tweety', wingspanCm: 12.5 },
    ];
    const echoed = await post<Animal[]>('/poly/animals/list', original);
    expect(echoed).toEqual(original);
  });

  it('round-trips backend fixtures', async () => {
    const fx = await get<Animal[]>('/poly/animals/list/fixtures');
    expect(fx).toHaveLength(3);
    const echoed = await post<Animal[]>('/poly/animals/list', fx);
    expect(echoed).toEqual(fx);
  });
});

describe('Case 3: Map<String, Animal>', () => {
  it('map values preserve discriminators', async () => {
    const original: Record<string, Animal> = {
      a: { type: 'dog', name: 'Rex', breed: 'Husky' },
      b: { type: 'cat', name: 'Whiskers', livesLeft: 9 },
    };
    const echoed = await post<Record<string, Animal>>('/poly/animals/map', original);
    expect(echoed).toEqual(original);
  });

  it('round-trips backend map fixtures', async () => {
    const fx = await get<Record<string, Animal>>('/poly/animals/map/fixtures');
    const echoed = await post<Record<string, Animal>>('/poly/animals/map', fx);
    expect(echoed).toEqual(fx);
  });
});

// Case 4 — Page<Animal>: documents a Jackson + Spring + Kotlin generics defect.
// The custom generic wrapper Page<T> erases polymorphic type info on both
// serialize (response body drops `type`) and deserialize (incoming JSON's `type`
// is stripped before subtype resolution, then re-serialized without it).
// List<Animal> and Map<String, Animal> work fine; only the user-defined
// generic wrapper fails. See FINDINGS.md candidate F2.
//
// These tests use `it.fails` so the suite stays green while still exercising
// the broken path — they will FAIL (a good signal) once the runtime is fixed.
describe('Case 4: Page<Animal> [KNOWN-BROKEN: generic-wrapper type erasure]', () => {
  it.fails('wrapper preserves polymorphic items', async () => {
    const original: Page<Animal> = {
      items: [
        { type: 'dog', name: 'Rex', breed: 'Husky' },
        { type: 'cat', name: 'Whiskers', livesLeft: 9 },
      ],
      page: 0,
      total: 2,
    };
    const echoed = await post<Page<Animal>>('/poly/animals/page', original);
    expect(echoed).toEqual(original);
  });

  it.fails('round-trips backend page fixtures', async () => {
    const fx = await get<Page<Animal>>('/poly/animals/page/fixtures');
    expect(fx.items).toHaveLength(3);
    const echoed = await post<Page<Animal>>('/poly/animals/page', fx);
    expect(echoed).toEqual(fx);
  });
});
