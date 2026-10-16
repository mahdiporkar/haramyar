// DEVELOPMENT AND TEST FIXTURES ONLY — never run against production.
// Provisions staff accounts the way an administrator would: a Keycloak account with the haramyar-staff realm role,
// a strong password and the CONFIGURE_TOTP required action (MFA is enrolled on first login), pre-bound to a Hive
// canonical user that receives business roles. No Hive platform role is ever assigned.
//
// Environment: KEYCLOAK_URL (internal), KEYCLOAK_ADMIN_USER, KEYCLOAK_ADMIN_PASSWORD, HARAMYAR_OIDC_ISSUER (as browsers
// see it), HIVE_AUTHORIZATION_URL, HIVE_PROVISIONING_PASSWORD, HARAMYAR_FIXTURE_STAFF_PASSWORD.
const env = name => {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value.replace(/\/+$/, '');
};
const keycloak = env('KEYCLOAK_URL');
const issuer = env('HARAMYAR_OIDC_ISSUER');
const hive = env('HIVE_AUTHORIZATION_URL');
const staffPassword = env('HARAMYAR_FIXTURE_STAFF_PASSWORD');
const hiveAuth = 'Basic ' + Buffer.from(`provisioner:${env('HIVE_PROVISIONING_PASSWORD')}`).toString('base64');

const STAFF = [
  {username: 'staff.coordinator', firstName: 'Coordinator', lastName: 'Fixture', roles: ['rozeh-coordinator']},
  {username: 'staff.editor', firstName: 'Editor', lastName: 'Fixture', roles: ['martyrs-editor']},
  {username: 'staff.reviewer', firstName: 'Reviewer', lastName: 'Fixture', roles: ['martyrs-reviewer']},
  {username: 'staff.admin', firstName: 'Business admin', lastName: 'Fixture', roles: ['haramyar-admin']},
];

async function json(response, what) {
  const text = await response.text();
  if (!response.ok && response.status !== 409) throw new Error(`${what}: ${response.status} ${text}`);
  return text ? JSON.parse(text) : null;
}

const token = (await json(await fetch(`${keycloak}/realms/master/protocol/openid-connect/token`, {
  method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'},
  body: new URLSearchParams({grant_type: 'password', client_id: 'admin-cli', username: env('KEYCLOAK_ADMIN_USER'), password: env('KEYCLOAK_ADMIN_PASSWORD')}),
}), 'keycloak admin token')).access_token;
const kc = (path, init = {}) => fetch(`${keycloak}/admin/realms/haramyar${path}`, {...init, headers: {Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...init.headers}});
const hiveCall = (method, path, body) => fetch(`${hive}${path}`, {method, headers: {Authorization: hiveAuth, 'Content-Type': 'application/json'}, ...(body ? {body: JSON.stringify(body)} : {})});

const staffRole = await json(await kc('/roles/haramyar-staff'), 'staff role');
for (const member of STAFF) {
  let [existing] = await json(await kc(`/users?exact=true&username=${encodeURIComponent(member.username)}`), 'lookup');
  if (!existing) {
    await json(await kc('/users', {method: 'POST', body: JSON.stringify({
      username: member.username, firstName: member.firstName, lastName: member.lastName, enabled: true,
      requiredActions: ['CONFIGURE_TOTP'], credentials: [{type: 'password', value: staffPassword, temporary: false}],
    })}), `create ${member.username}`);
    [existing] = await json(await kc(`/users?exact=true&username=${encodeURIComponent(member.username)}`), 'lookup');
  }
  await json(await kc(`/users/${existing.id}/role-mappings/realm`, {method: 'POST', body: JSON.stringify([staffRole])}), 'staff role mapping');

  const displayName = `${member.firstName} ${member.lastName}`;
  const users = await json(await hiveCall('GET', `/admin/users?query=${encodeURIComponent(member.firstName)}&limit=100`), 'hive users');
  let hiveUser = users.find(u => u.identities?.some(i => i.issuer === issuer && i.subject === existing.id));
  if (!hiveUser) hiveUser = await json(await hiveCall('POST', '/admin/users', {tenantId: 'default', displayName, identities: [{issuer, subject: existing.id}]}), `hive user ${member.username}`);
  for (const role of member.roles) await json(await hiveCall('POST', `/admin/roles/${role}/assignments`, {subject: `user:${hiveUser.id}`}), `assign ${role}`);
  console.log(`staff fixture ${member.username} -> hive ${hiveUser.id} (${member.roles.join(', ')})`);
}
