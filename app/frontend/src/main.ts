import {
  createApi,
  type Address,
  type ApiResponse,
  type CreateUserRequest,
  type Page,
  type UpdateUserRequest,
  type UserProfileDto,
} from './generated/api-sdk';

const api = createApi('http://localhost:8080');

export async function demo(): Promise<void> {
  // GET — path variable + nested DTO
  const user: UserProfileDto = await api.user.getUserProfile(1);
  const address: Address | null = user.address;
  console.log('fetched', user.name, address?.city);

  // POST — request body
  const created: UserProfileDto = await api.user.createUser({
    name: 'Ada Lovelace',
    email: 'ada@example.com',
  } satisfies CreateUserRequest);

  // PUT — path variable + partial body
  const updated: UserProfileDto = await api.user.updateUser(created.id, {
    email: 'new@example.com',
    bio: null,
  } satisfies UpdateUserRequest);

  // DELETE — returns void
  await api.user.deleteUser(updated.id);

  // GET with optional @RequestParam + single-parameter generic
  const page: Page<UserProfileDto> = await api.user.listUsers();
  const total: number = page.totalElements;
  console.log('total users:', total);

  // GET with all optional query params provided
  const filtered: Page<UserProfileDto> = await api.user.listUsers(0, 10, 'ada');
  console.log('filtered count:', filtered.content.length);

  // GET with multi-parameter generic envelope
  const wrapped: ApiResponse<UserProfileDto, string> = await api.user.wrappedUser(1);
  const data: UserProfileDto | null = wrapped.data;
  const error: string | null = wrapped.error;
  console.log('wrapped success:', wrapped.success, 'data:', data?.name, 'error:', error);
}

demo().catch((err: unknown) => {
  console.error('demo failed', err);
  process.exit(1);
});
