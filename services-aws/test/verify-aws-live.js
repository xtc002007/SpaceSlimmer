const https = require('https');

const BASE_URL = 'https://6ta8jul1hf.execute-api.us-east-2.amazonaws.com/dev';

function request(path, method, body = null, token = null) {
  return new Promise((resolve, reject) => {
    const url = new URL(`${BASE_URL}${path}`);
    const headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'X-Device-Id': 'aws-smoke-test-device',
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const data = body ? JSON.stringify(body) : null;
    if (data) {
      headers['Content-Length'] = Buffer.byteLength(data);
    }

    const req = https.request(url, { method, headers }, (res) => {
      let raw = '';
      res.on('data', chunk => raw += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, data: JSON.parse(raw) });
        } catch {
          resolve({ status: res.statusCode, raw });
        }
      });
    });

    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

async function verifyLiveAws() {
  console.log('🌐 Starting End-to-End Live AWS Deployment Verification...\n');
  console.log(`Endpoint: ${BASE_URL}\n`);

  const testEmail = `smoke_${Date.now()}@spaceslimmer.com`;
  const testPassword = 'Password123!';

  // 1. Register
  console.log(`1. Testing POST /api/auth/register with ${testEmail}...`);
  const regRes = await request('/api/auth/register', 'POST', {
    email: testEmail,
    password: testPassword,
  });
  console.log('  Status:', regRes.status);
  console.log('  Response:', JSON.stringify(regRes.data));
  if (regRes.status !== 201) throw new Error('Registration failed on AWS');
  const token = regRes.data.token;

  // 2. Login
  console.log('\n2. Testing POST /api/auth/login...');
  const loginRes = await request('/api/auth/login', 'POST', {
    email: testEmail,
    password: testPassword,
  });
  console.log('  Status:', loginRes.status);
  console.log('  IsPro:', loginRes.data.subscription.isPro);
  console.log('  Remaining Quota:', loginRes.data.subscription.quota.remaining);
  if (loginRes.status !== 200) throw new Error('Login failed on AWS');

  // 3. Status
  console.log('\n3. Testing GET /api/subscription/status...');
  const statusRes = await request('/api/subscription/status', 'GET', null, token);
  console.log('  Status:', statusRes.status);
  console.log('  Max Resolution:', statusRes.data.limits.maxResolution);
  console.log('  Batch Video Limit:', statusRes.data.limits.maxBatchFiles.video);

  // 4. Consume
  console.log('\n4. Testing POST /api/subscription/consume (consuming 2 credits)...');
  const consumeRes = await request('/api/subscription/consume', 'POST', { count: 2 }, token);
  console.log('  Status:', consumeRes.status);
  console.log('  Consumed:', consumeRes.data.consumed);
  console.log('  New Remaining Quota:', consumeRes.data.quota.remaining);
  if (consumeRes.data.quota.remaining !== 28) throw new Error('Quota decrement mismatch');

  // 5. Activate PRO License
  console.log('\n5. Testing POST /api/subscription/activate (activating SS-PRO-1Y-DEMO01)...');
  const activateRes = await request('/api/subscription/activate', 'POST', { licenseKey: 'SS-PRO-1Y-DEMO01' }, token);
  console.log('  Status:', activateRes.status);
  console.log('  Message:', activateRes.data.message);
  console.log('  PlanType:', activateRes.data.subscription?.planType);
  console.log('  ExpiresAt:', activateRes.data.subscription?.expiresAt);
  if (activateRes.status !== 200) throw new Error('Activation failed on AWS');

  // 6. Verify PRO status after activation
  console.log('\n6. Testing GET /api/subscription/status after PRO activation...');
  const postStatusRes = await request('/api/subscription/status', 'GET', null, token);
  console.log('  Status:', postStatusRes.status);
  console.log('  IsPro:', postStatusRes.data.isPro);
  console.log('  Max Resolution:', postStatusRes.data.limits.maxResolution);
  console.log('  HEVC Allowed:', postStatusRes.data.limits.allowHevc);
  console.log('  Remaining Quota:', postStatusRes.data.quota.remaining);
  if (!postStatusRes.data.isPro || postStatusRes.data.limits.maxResolution !== '4K') {
    throw new Error('PRO status not reflected after activation');
  }

  console.log('\n===============================================================');
  console.log('🚀 LIVE AWS API VERIFICATION PASSED 100% SUCCESFULLY! 🎉');
  console.log('===============================================================\n');
}

verifyLiveAws().catch(err => {
  console.error('\n❌ Live verification failed:', err);
  process.exit(1);
});
