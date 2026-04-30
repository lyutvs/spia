import { describe, it, expect } from 'vitest';
import { post, get } from './util/http';
import type { Message } from '../src/generated/api-sdk';

// Case 6 finding: SPIA emits `Message` with an inlined `type` discriminator
// (`type Message = ({ type: 'image' } & ImageMessage) | ({ type: 'text' } & TextMessage)`),
// identical to PROPERTY mode. This does NOT match Jackson's WRAPPER_OBJECT
// wire format, which uses the subtype name as an OUTER object key:
// `{"text": {"body": "hello"}}`. The `as unknown as Message` cast is therefore
// required so the test can validate the actual runtime wire shape that Jackson
// produces and consumes; the emitted TS type is structurally wrong here.
describe('Case 6: WRAPPER_OBJECT discriminator', () => {
  it('text message round-trips with subtype name as wrapper key', async () => {
    const original = { text: { body: 'hello' } } as unknown as Message;
    const echoed = await post<Message>('/poly/messages', original);
    expect(echoed).toEqual(original);
  });

  it('image message round-trips', async () => {
    const original = { image: { url: 'https://example.com/x.png', widthPx: 1024 } } as unknown as Message;
    const echoed = await post<Message>('/poly/messages', original);
    expect(echoed).toEqual(original);
  });

  it('backend fixtures round-trip', async () => {
    const fixtures = await get<Message[]>('/poly/messages/fixtures');
    expect(fixtures.length).toBeGreaterThanOrEqual(2);
    for (const fx of fixtures) {
      const echoed = await post<Message>('/poly/messages', fx);
      expect(echoed).toEqual(fx);
    }
  });
});
