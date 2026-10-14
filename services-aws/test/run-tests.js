const assert = require('assert');
const { setMockDocClient, TABLES } = require('../src/handlers/shared/db');
const authHandler = require('../src/handlers/auth');
const subHandler = require('../src/handlers/subscription');
const { generateLicenseKey } = require('../scripts/generate-licenses');

// In-memory DynamoDB Mock Store
const store = {
  [TABLES.USERS]: new Map(),
  [TABLES.SUBSCRIPTIONS]: new Map(),
  [TABLES.LICENSES]: new Map(),
};

function buildMockDocClient() {
  return {
    send: async (command) => {
      const name = command.constructor.name;
      const input = command.input;
      const table = input.TableName;
      const tableStore = store[table];

      if (!tableStore) {
        throw new Error(`Unknown table: ${table}`);
      }

      if (name === 'GetCommand') {
        const key = Object.values(input.Key)[0];
        const item = tableStore.get(key) || null;
        return { Item: item ? JSON.parse(JSON.stringify(item)) : null };
      }

      if (name === 'PutCommand') {
        const key = Object.values(input.Item)[0];
        tableStore.set(key, JSON.parse(JSON.stringify(input.Item)));
        return {};
      }

      if (name === 'UpdateCommand') {
        const key = Object.values(input.Key)[0];
        let item = tableStore.get(key);
        if (!item) {
          item = { ...input.Key };
        }

        // Check ConditionExpression if any
        if (input.ConditionExpression && input.ExpressionAttributeValues) {
          if (input.ConditionExpression.includes('usedQuota <= :maxAllowed')) {
            const maxAllowed = input.ExpressionAttributeValues[':maxAllowed'];
            const curUsed = item.usedQuota || 0;
            if (curUsed > maxAllowed) {
              const err = new Error('ConditionalCheckFailed');
              err.name = 'ConditionalCheckFailedException';
              throw err;
            }
          }
        }

        // Apply update expression values
        if (input.ExpressionAttributeValues) {
          for (const [attrKey, val] of Object.entries(input.ExpressionAttributeValues)) {
            if (attrKey === ':p') item.currentPeriod = val;
            if (attrKey === ':u') item.usedQuota = val;
            if (attrKey === ':c') item.usedQuota = (item.usedQuota || 0) + val;
            if (attrKey === ':pt') item.planType = val;
            if (attrKey === ':exp') item.expiresAt = val;
            if (attrKey === ':lk') item.licenseKey = val;
            if (attrKey === ':t') item.updatedAt = val;
            if (attrKey === ':today') item.lastEmergencyDate = val;
            if (attrKey === ':used') item.status = val;
            if (attrKey === ':act') item.status = val;
            if (attrKey === ':one') item.tokenVersion = (item.tokenVersion || 0) + 1;
            if (attrKey === ':d') item.lastDeviceId = val;
          }
        }

        tableStore.set(key, JSON.parse(JSON.stringify(item)));
        return { Attributes: item };
      }

      throw new Error(`Mock does not handle command: ${name}`);
    },
  };
}

async function runTests() {
  console.log('🧪 Starting SpaceSlimmer Comprehensive Backend Test Suite...\n');
  setMockDocClient(buildMockDocClient());

  // Seed demo licenses
  const demoKey = generateLicenseKey();
  store[TABLES.LICENSES].set(demoKey, {
    licenseKey: demoKey,
    planType: 'pro-yearly',
    status: 'available',
    boundUser: null,
  });

  // Test 1: Register User Validation
  console.log('Test 1: Register validation...');
  {
    const res = await authHandler.register({
      body: JSON.stringify({ email: 'bad-email', password: '123' }),
    });
    assert.strictEqual(res.statusCode, 400);
    const body = JSON.parse(res.body);
    assert.strictEqual(body.error, 'INVALID_EMAIL');
    console.log('  ✓ Invalid email properly rejected');
  }

  // Test 2: Successful Registration
  console.log('\nTest 2: Successful Registration with Device ID & Uniform Subscription...');
  let authToken = '';
  const testEmail = 'user@spaceslimmer.com';
  {
    const res = await authHandler.register({
      headers: { 'x-device-id': 'pixel-9-pro-xyz' },
      body: JSON.stringify({ email: testEmail, password: 'password123' }),
    });
    assert.strictEqual(res.statusCode, 201);
    const body = JSON.parse(res.body);
    assert.ok(body.token);
    assert.strictEqual(body.subscription.isPro, false);
    assert.strictEqual(body.subscription.quota.total, 30);
    assert.strictEqual(body.subscription.quota.used, 0);
    assert.strictEqual(body.subscription.quota.remaining, 30);
    assert.ok(body.subscription.limits);
    assert.strictEqual(body.subscription.limits.maxResolution, '1080p');
    authToken = body.token;
    console.log('  ✓ User registered, default 30 free monthly quota + limits allocated');
  }

  // Test 3: Duplicate Registration Rejection
  console.log('\nTest 3: Duplicate Registration...');
  {
    const res = await authHandler.register({
      body: JSON.stringify({ email: testEmail, password: 'password123' }),
    });
    assert.strictEqual(res.statusCode, 409);
    console.log('  ✓ Duplicate email rejected with 409');
  }

  // Test 4: Login with Uniform Subscription
  console.log('\nTest 4: User Login...');
  {
    const res = await authHandler.login({
      body: JSON.stringify({ email: testEmail, password: 'password123' }),
    });
    assert.strictEqual(res.statusCode, 200);
    const body = JSON.parse(res.body);
    assert.ok(body.token);
    assert.strictEqual(body.subscription.isPro, false);
    assert.strictEqual(body.subscription.quota.remaining, 30);
    assert.strictEqual(body.subscription.limits.maxBatchFiles.video, 3);
    console.log('  ✓ Login successful, JWT token and full subscription limits returned');
  }

  // Test 5: GET /api/subscription/status
  console.log('\nTest 5: Get Subscription Status & Limits...');
  {
    const res = await subHandler.getStatus({
      headers: { Authorization: `Bearer ${authToken}` },
    });
    assert.strictEqual(res.statusCode, 200);
    const body = JSON.parse(res.body);
    assert.strictEqual(body.isPro, false);
    assert.strictEqual(body.quota.remaining, 30);
    assert.strictEqual(body.limits.maxResolution, '1080p');
    assert.strictEqual(body.limits.maxBatchFiles.video, 3);
    assert.strictEqual(body.limits.allowHevc, false);
    console.log('  ✓ Limits returned: 1080p max, 3 videos batch limit, HEVC locked');
  }

  // Test 6: POST /api/subscription/consume (Normal)
  console.log('\nTest 6: Consume Quota (Normal usage)...');
  {
    const res = await subHandler.consume({
      headers: { Authorization: `Bearer ${authToken}` },
      body: JSON.stringify({ count: 5, mediaType: 'video' }),
    });
    assert.strictEqual(res.statusCode, 200);
    const body = JSON.parse(res.body);
    assert.strictEqual(body.success, true);
    assert.strictEqual(body.isPro, false);
    assert.strictEqual(body.isEmergency, false);
    assert.strictEqual(body.consumed, 5);
    assert.strictEqual(body.quota.used, 5);
    assert.strictEqual(body.quota.remaining, 25);
    console.log('  ✓ Consumed 5 credits, remaining is now 25');
  }

  // Test 7: Consume the remaining 25 credits to reach monthly limit
  console.log('\nTest 7: Consuming to full monthly limit...');
  {
    const res = await subHandler.consume({
      headers: { Authorization: `Bearer ${authToken}` },
      body: JSON.stringify({ count: 25, mediaType: 'photo' }),
    });
    assert.strictEqual(res.statusCode, 200);
    const body = JSON.parse(res.body);
    assert.strictEqual(body.quota.used, 30);
    assert.strictEqual(body.quota.remaining, 0);
    console.log('  ✓ Monthly 30 credits fully used up');
  }

  // Test 8: Emergency Daily Quota Trigger
  console.log('\nTest 8: Emergency Daily Quota Trigger (When monthly quota is 0)...');
  {
    const res = await subHandler.consume({
      headers: { Authorization: `Bearer ${authToken}` },
      body: JSON.stringify({ count: 1, mediaType: 'video' }),
    });
    assert.strictEqual(res.statusCode, 200);
    const body = JSON.parse(res.body);
    assert.strictEqual(body.success, true);
    assert.strictEqual(body.isEmergency, true);
    assert.strictEqual(body.consumed, 1);
    assert.strictEqual(body.quota.dailyEmergencyAvailable, false);
    console.log(`  ✓ Emergency daily compression triggered: "${body.message}"`);
  }

  // Test 9: Exhausted Quota (Both Monthly and Daily Emergency used)
  console.log('\nTest 9: Quota Exhausted (Monthly 0 + Emergency used today)...');
  {
    const res = await subHandler.consume({
      headers: { Authorization: `Bearer ${authToken}` },
      body: JSON.stringify({ count: 1, mediaType: 'video' }),
    });
    assert.strictEqual(res.statusCode, 403);
    const body = JSON.parse(res.body);
    assert.strictEqual(body.error, 'QUOTA_EXCEEDED');
    assert.strictEqual(body.quota.remaining, 0);
    console.log('  ✓ Promptly rejected with 403 QUOTA_EXCEEDED when emergency is also spent');
  }

  // Test 10: Activate PRO License
  console.log('\nTest 10: Activate PRO License (Generated Key)...');
  {
    const res = await subHandler.activate({
      headers: { Authorization: `Bearer ${authToken}` },
      body: JSON.stringify({ licenseKey: demoKey }),
    });
    assert.strictEqual(res.statusCode, 200);
    const body = JSON.parse(res.body);
    assert.strictEqual(body.success, true);
    assert.strictEqual(body.subscription.isPro, true);
    assert.strictEqual(body.subscription.planType, 'pro-yearly');
    assert.ok(body.subscription.expiresAt);
    console.log(`  ✓ PRO yearly activated! Key: ${demoKey}, Expires: ${body.subscription.expiresAt}`);
  }

  // Test 11: Verify PRO Privileges (Unlimited Quota & 4K/HEVC unlocked)
  console.log('\nTest 11: Verify PRO Privileges & Limits...');
  {
    const statusRes = await subHandler.getStatus({
      headers: { Authorization: `Bearer ${authToken}` },
    });
    const statusBody = JSON.parse(statusRes.body);
    assert.strictEqual(statusBody.isPro, true);
    assert.strictEqual(statusBody.limits.maxResolution, '4K');
    assert.strictEqual(statusBody.limits.allowHevc, true);
    assert.strictEqual(statusBody.limits.allowAv1, true);
    assert.strictEqual(statusBody.limits.allowCustomParams, true);

    // Test unlimited consumption
    const consumeRes = await subHandler.consume({
      headers: { Authorization: `Bearer ${authToken}` },
      body: JSON.stringify({ count: 100 }),
    });
    const consumeBody = JSON.parse(consumeRes.body);
    assert.strictEqual(consumeBody.success, true);
    assert.strictEqual(consumeBody.isPro, true);
    console.log('  ✓ PRO privileges verified: 4K/HEVC unlocked, unlimited compressions granted');
  }

  // Test 12: Re-activation of used license should fail
  console.log('\nTest 12: Re-activation of used license should fail...');
  {
    const res = await subHandler.activate({
      headers: { Authorization: `Bearer ${authToken}` },
      body: JSON.stringify({ licenseKey: demoKey }),
    });
    assert.strictEqual(res.statusCode, 409);
    console.log('  ✓ Already used license rejected with 409');
  }

  // Test 13: Logout / Token Revocation
  console.log('\nTest 13: Server-side Token Revocation (Logout)...');
  {
    const logoutRes = await authHandler.logout({
      headers: { Authorization: `Bearer ${authToken}` },
    });
    assert.strictEqual(logoutRes.statusCode, 200);

    // Old token should now be rejected as unauthorized
    const meRes = await authHandler.me({
      headers: { Authorization: `Bearer ${authToken}` },
    });
    assert.strictEqual(meRes.statusCode, 401);
    console.log('  ✓ Old JWT immediately rejected after logout (tokenVersion revocation verified)');
  }

  console.log('\n======================================================');
  console.log('🎉 ALL 13 PRODUCTION-GRADE TESTS PASSED SUCCESSFULLY! 🚀');
  console.log('======================================================\n');
}

runTests().catch((err) => {
  console.error('❌ Test suite failed:', err);
  process.exit(1);
});
