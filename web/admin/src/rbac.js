export const PERMISSIONS = Object.freeze({
  USERS_READ: 'users.read',
  DEVICES_READ: 'devices.read',
  DEVICES_REVOKE: 'devices.revoke',
  SERVERS_READ: 'servers.read',
  SERVERS_MAINTAIN: 'servers.maintain',
  CATALOG_READ: 'catalog.read',
  CATALOG_WRITE: 'catalog.write',
  ORDERS_READ: 'orders.read',
  SUBSCRIPTIONS_READ: 'subscriptions.read',
  TICKETS_READ: 'tickets.read',
  TICKETS_WRITE: 'tickets.write',
  FLAGS_READ: 'flags.read',
  FLAGS_WRITE: 'flags.write',
  CONFIG_READ: 'config.read',
  CONFIG_STAGE: 'config.stage',
  CONFIG_PUBLISH: 'config.publish',
  MONITORING_READ: 'monitoring.read',
  RELEASES_READ: 'releases.read',
  RELEASES_PAUSE: 'releases.pause',
  AUDIT_READ: 'audit.read',
});

const ALL = Object.freeze(Object.values(PERMISSIONS));

export const ROLE_PERMISSIONS = Object.freeze({
  'ganj-admin-support': Object.freeze([
    PERMISSIONS.USERS_READ, PERMISSIONS.DEVICES_READ, PERMISSIONS.ORDERS_READ,
    PERMISSIONS.SUBSCRIPTIONS_READ, PERMISSIONS.TICKETS_READ, PERMISSIONS.TICKETS_WRITE,
  ]),
  'ganj-admin-operations': Object.freeze([
    PERMISSIONS.SERVERS_READ, PERMISSIONS.SERVERS_MAINTAIN, PERMISSIONS.MONITORING_READ,
    PERMISSIONS.RELEASES_READ,
  ]),
  'ganj-admin-catalog': Object.freeze([
    PERMISSIONS.CATALOG_READ, PERMISSIONS.CATALOG_WRITE, PERMISSIONS.ORDERS_READ,
    PERMISSIONS.SUBSCRIPTIONS_READ,
  ]),
  'ganj-admin-release': Object.freeze([
    PERMISSIONS.FLAGS_READ, PERMISSIONS.FLAGS_WRITE, PERMISSIONS.CONFIG_READ,
    PERMISSIONS.CONFIG_STAGE, PERMISSIONS.CONFIG_PUBLISH, PERMISSIONS.RELEASES_READ,
    PERMISSIONS.RELEASES_PAUSE,
  ]),
  'ganj-admin-security': Object.freeze([
    PERMISSIONS.USERS_READ, PERMISSIONS.DEVICES_READ, PERMISSIONS.DEVICES_REVOKE,
    PERMISSIONS.MONITORING_READ, PERMISSIONS.AUDIT_READ,
  ]),
  'ganj-admin-super': ALL,
});

export function permissionsForRoles(roles) {
  if (!Array.isArray(roles)) return Object.freeze([]);
  const granted = new Set();
  for (const role of roles) {
    for (const permission of ROLE_PERMISSIONS[role] ?? []) granted.add(permission);
  }
  return Object.freeze([...granted].sort());
}

export function hasPermission(principal, permission) {
  return principal?.permissions?.includes(permission) === true;
}

export function requirePermission(principal, permission) {
  if (!hasPermission(principal, permission)) {
    const error = new Error('این حساب مجوز لازم را ندارد.');
    error.status = 403;
    error.code = 'permission_denied';
    throw error;
  }
}
