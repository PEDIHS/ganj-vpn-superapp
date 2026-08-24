-- Integration-test catalog only. Never run this migration in production.
INSERT INTO control_users (id, status, display_name)
VALUES ('10000000-0000-4000-8000-000000000001', 'active', 'Integration User')
ON CONFLICT (id) DO NOTHING;

INSERT INTO control_devices (
  id, user_id, status, signing_public_jwk, encryption_public_jwk, key_version, attestation_status
) VALUES (
  '20000000-0000-4000-8000-000000000001',
  '10000000-0000-4000-8000-000000000001',
  'active',
  '{"kty":"OKP","crv":"Ed25519","x":"11qYAYdk9J6bkFNo9kwEaJPdAROb7uDx8ryqcZbB6kQ"}',
  '{"kty":"OKP","crv":"X25519","x":"hSDwCYkwp1R0i33ctD73Wg2_Og0mOBr066SpjqqbTmo"}',
  'integration-v1',
  'trusted'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO control_plans (
  id, code, name, tier, duration_days, traffic_limit_bytes, device_limit, features,
  price_amount_minor, price_currency, channels, play_product_id
) VALUES (
  '30000000-0000-4000-8000-000000000002', 'premium-30d', 'Premium 30 Days', 'premium', 30,
  107374182400, 2, ARRAY['smart_connect','premium_servers'], 2990000, 'IRR',
  ARRAY['play','direct','wallet'], 'ganj.premium.30d'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO control_services (
  id, user_id, plan_id, name, status, tier, traffic_limit_bytes, traffic_used_bytes,
  expires_at, device_limit, allowed_protocols
) VALUES (
  '40000000-0000-4000-8000-000000000001',
  '10000000-0000-4000-8000-000000000001',
  '30000000-0000-4000-8000-000000000002',
  'Integration Premium', 'active', 'premium', 107374182400, 0, now() + interval '30 days', 2,
  ARRAY['vless','trojan']
) ON CONFLICT (id) DO NOTHING;

INSERT INTO control_service_devices (service_id, device_id, user_id)
VALUES (
  '40000000-0000-4000-8000-000000000001',
  '20000000-0000-4000-8000-000000000001',
  '10000000-0000-4000-8000-000000000001'
) ON CONFLICT DO NOTHING;

INSERT INTO control_servers (
  id, code, name, country_code, city, tier, status, load_ratio, latency_hint_ms, protocols, secret_ref
) VALUES (
  '50000000-0000-4000-8000-000000000002', 'de-premium-01', 'Germany Premium 01', 'DE',
  'Frankfurt', 'premium', 'active', 0.2, 60, ARRAY['vless','trojan'], 'integration/de-premium-01'
) ON CONFLICT (id) DO NOTHING;
