import { describe, it, expect } from 'vitest';
import { post, get } from './util/http';
import type { Envelope, Message } from '../src/generated/api-sdk';

// Case 7 — Envelope contains two polymorphic hierarchies side-by-side:
//   - `animal: Animal` (NAME mode, inlined `type` discriminator)
//   - `message: Message` (WRAPPER_OBJECT mode — see F4 in FINDINGS.md)
//
// Because `Envelope` is a non-generic concrete class, F2 (discriminator
// erasure through user-defined generic wrappers) does NOT apply. The
// `as unknown as Message` cast is still required for the WRAPPER_OBJECT
// member because SPIA emits an inlined-discriminator TS shape that does
// not match the actual `{ "<subtype>": {...} }` wire format (F4).
describe('Case 7: Two polymorphic hierarchies (NAME + WRAPPER_OBJECT) in one envelope', () => {
  it('Animal (NAME) + Message (WRAPPER_OBJECT) coexist in same payload', async () => {
    const original: Envelope = {
      animal: { type: 'dog', name: 'Rex', breed: 'Husky' },
      message: { text: { body: 'hi' } } as unknown as Message,
      timestamp: 1714464000000,
    };
    const echoed = await post<Envelope>('/poly/envelope', original);
    expect(echoed).toEqual(original);
  });

  it('backend fixtures round-trip', async () => {
    const fixtures = await get<Envelope[]>('/poly/envelope/fixtures');
    for (const fx of fixtures) {
      const echoed = await post<Envelope>('/poly/envelope', fx);
      expect(echoed).toEqual(fx);
    }
  });
});
