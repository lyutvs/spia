const PORT = process.env['SPIA_E2E_BACKEND_PORT'] ?? '18080';
const BASE = `http://localhost:${PORT}`;

export class BackendError extends Error {
  constructor(public status: number, public body: string, url: string) {
    super(`Backend ${status} for ${url}\n--- body ---\n${body}`);
  }
}

async function ensureOk(res: Response, url: string): Promise<void> {
  if (!res.ok) {
    const body = await res.text();
    throw new BackendError(res.status, body, url);
  }
}

export async function post<T>(path: string, body: unknown): Promise<T> {
  const url = `${BASE}${path}`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  await ensureOk(res, url);
  return (await res.json()) as T;
}

export async function get<T>(path: string): Promise<T> {
  const url = `${BASE}${path}`;
  const res = await fetch(url);
  await ensureOk(res, url);
  return (await res.json()) as T;
}

export async function getRaw(path: string): Promise<{ status: number; body: string }> {
  const url = `${BASE}${path}`;
  const res = await fetch(url);
  return { status: res.status, body: await res.text() };
}
