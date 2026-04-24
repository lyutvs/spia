/**
 * Manual integration check: hits a running Spring Boot server on :8080 and
 * exercises each generated SDK endpoint. Run the server in one terminal with
 *   JAVA_HOME=... ./gradlew :app:bootRun
 * and this script in another with
 *   cd app/frontend && npm run integration
 */
import { createApi } from './generated/api-sdk';

const api = createApi('http://localhost:8080');

async function run(): Promise<void> {
  const profile = await api.user.getUserProfile(42);
  console.log('getUserProfile →', profile);

  const created = await api.user.createUser({
    name: 'Grace Hopper',
    email: 'grace@example.com',
  });
  console.log('createUser →', created);

  const updated = await api.user.updateUser(created.id, {
    email: 'grace.hopper@example.com',
    bio: 'Admiral',
  });
  console.log('updateUser →', updated);

  const page = await api.user.listUsers(0, 5);
  console.log('listUsers →', page);

  const wrapped = await api.user.wrappedUser(created.id);
  console.log('wrappedUser →', wrapped);

  await api.user.deleteUser(created.id);
  console.log('deleteUser → ok');
}

run()
  .then(() => process.exit(0))
  .catch((err: unknown) => {
    console.error('integration failed:', err);
    process.exit(1);
  });
