// Applies deployment-specific switches to the haramyar realm on every bootstrap (the realm export is imported only once,
// so existing realms are updated through the admin API). Each switch is off unless explicitly enabled. Self-registration
// (mobile number + password; the number is never verified, no SMS) is enabled on haramyar.com and in local Compose;
// password reset stays off everywhere because it has no delivery channel (docs/BUSINESS_DECISIONS.md).
//
// Environment: KEYCLOAK_URL (internal), KEYCLOAK_ADMIN_USER, KEYCLOAK_ADMIN_PASSWORD,
//              HARAMYAR_SELF_REGISTRATION=true|false (default false), HARAMYAR_PASSWORD_RESET=true|false (default false).
const env = name => {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value.replace(/\/+$/, '');
};
const keycloak = env('KEYCLOAK_URL');
const flag = name => process.env[name] === 'true';

const tokenResponse = await fetch(`${keycloak}/realms/master/protocol/openid-connect/token`, {
  method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'},
  body: new URLSearchParams({grant_type: 'password', client_id: 'admin-cli', username: env('KEYCLOAK_ADMIN_USER'), password: env('KEYCLOAK_ADMIN_PASSWORD')}),
});
if (!tokenResponse.ok) throw new Error(`keycloak admin token: ${tokenResponse.status}`);
const {access_token: token} = await tokenResponse.json();

const policy = {registrationAllowed: flag('HARAMYAR_SELF_REGISTRATION'), resetPasswordAllowed: flag('HARAMYAR_PASSWORD_RESET')};
const response = await fetch(`${keycloak}/admin/realms/haramyar`, {
  method: 'PUT', headers: {Authorization: `Bearer ${token}`, 'Content-Type': 'application/json'}, body: JSON.stringify(policy),
});
if (!response.ok) throw new Error(`realm policy: ${response.status} ${await response.text()}`);
console.log(`realm policy: self-registration ${policy.registrationAllowed ? 'enabled' : 'disabled'}, password reset ${policy.resetPasswordAllowed ? 'enabled' : 'disabled'}`);
