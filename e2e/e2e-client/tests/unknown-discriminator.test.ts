import { describe, it, expect } from 'vitest';
import { getRaw } from './util/http';

describe('Case 8: Unknown discriminator from server', () => {
  it('client receives raw JSON without throwing at HTTP layer', async () => {
    const { status, body } = await getRaw('/poly/animals/unknown');
    expect(status).toBe(200);
    const parsed = JSON.parse(body);
    expect(parsed.type).toBe('reptile');
    expect(parsed.name).toBe('Iggy');
    expect(parsed.scaleColor).toBe('green');
    // Documents the contract: the SDK's typed client cannot narrow this safely.
    // Consumers must guard at the call site. This test pins that behavior.
  });
});
