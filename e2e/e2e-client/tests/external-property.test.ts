import { describe, it, expect } from 'vitest';
import { post, get } from './util/http';
import type { PaymentEvent } from '../src/generated/api-sdk';

// Case 5 finding: SPIA emits the discriminator INSIDE the payload union
// (`PaymentPayload = ({ kind: 'card' } & CardPayload) | ...`) just like a
// PROPERTY-mode hierarchy, ignoring Jackson's EXTERNAL_PROPERTY semantics.
//
// Jackson at runtime ALSO requires `kind` inside the payload here — when the
// parent class has a sibling field named `kind`, Jackson consumes it as a
// normal field and falls back to looking for the type id INSIDE the
// polymorphic value. So both the SDK-emitted shape and the wire format agree
// that `kind` lives in `payload`. The parent-level `kind` is just a duplicate
// regular field. EXTERNAL_PROPERTY in this configuration effectively behaves
// like PROPERTY mode (see report).
describe('Case 5: EXTERNAL_PROPERTY discriminator', () => {
  it('card event round-trips with kind on both parent and payload', async () => {
    const original: PaymentEvent = {
      kind: 'card',
      payload: { kind: 'card', last4: '4242', brand: 'visa' },
      amountCents: 1000,
    };
    const echoed = await post<PaymentEvent>('/poly/payments', original);
    expect(echoed).toEqual(original);
  });

  it('bank transfer event round-trips', async () => {
    const original: PaymentEvent = {
      kind: 'bank',
      payload: { kind: 'bank', account: 'DE89-3704-0044-0532-0130-00' },
      amountCents: 250000,
    };
    const echoed = await post<PaymentEvent>('/poly/payments', original);
    expect(echoed).toEqual(original);
  });

  it('backend fixtures round-trip', async () => {
    const fixtures = await get<PaymentEvent[]>('/poly/payments/fixtures');
    expect(fixtures.length).toBeGreaterThanOrEqual(2);
    for (const fx of fixtures) {
      const echoed = await post<PaymentEvent>('/poly/payments', fx);
      expect(echoed).toEqual(fx);
    }
  });
});
