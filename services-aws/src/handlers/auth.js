const { GetCommand, PutCommand, UpdateCommand } = require('@aws-sdk/lib-dynamodb');
const bcrypt = require('bcryptjs');
const {
  getDocClient,
  TABLES,
  corsResponse,
  getBody,
  generateToken,
  verifyAuthUser,
  sanitizeError,
} = require('./shared/db');
const { isValidEmail, isValidPassword } = require('./shared/security');
const { checkRateLimit, getClientIp } = require('./shared/rateLimit');

const PLAN_LIMITS = {
  free: {
    maxBatchFiles: { video: 3, photo: 10, audio: 5 },
    maxResolution: '1080p',
    maxFileSizeMb: 500,
    allowHevc: false,
    allowAv1: false,
    allowAvif: false,
    allowCustomParams: false,
    vaultRetentionDays: 3,
    allowHighParallelism: false,
  },
  pro: {
    maxBatchFiles: { video: 9999, photo: 9999, audio: 9999 },
    maxResolution: '4K',
    maxFileSizeMb: 102400,
    allowHevc: true,
    allowAv1: true,
    allowAvif: true,
    allowCustomParams: true,
    vaultRetentionDays: 30,
    allowHighParallelism: true,
  },
};

function getCurrentPeriod() {
  return new Date().toISOString().slice(0, 7); // 'YYYY-MM'
}

function getTodayDate() {
  return new Date().toISOString().slice(0, 10); // 'YYYY-MM-DD'
}

function buildSubscriptionSummary(subscription, now = new Date()) {
  const isProPlan = Boolean(subscription.planType && subscription.planType.startsWith('pro-'));
  let isPro = isProPlan;
  let status = subscription.status || 'active';

  if (isPro && subscription.expiresAt && subscription.planType !== 'pro-lifetime') {
    if (new Date(subscription.expiresAt) <= now) {
      isPro = false;
      status = 'expired';
    }
  }

  const total = subscription.monthlyQuota || 30;
  const used = subscription.usedQuota || 0;
  const today = getTodayDate();
  const dailyEmergencyAvailable = !isPro && used >= total && subscription.lastEmergencyDate !== today;

  return {
    isPro,
    planType: isPro ? subscription.planType : 'free',
    status,
    expiresAt: subscription.expiresAt || null,
    quota: {
      period: subscription.currentPeriod || getCurrentPeriod(),
      total: isPro ? 999999 : total,
      used,
      remaining: isPro ? 999999 : Math.max(0, total - used),
      dailyEmergencyAvailable,
    },
    limits: isPro ? PLAN_LIMITS.pro : PLAN_LIMITS.free,
  };
}

/**
 * POST /api/auth/register
 * Body: { email, password, deviceId? }
 */
exports.register = async (event) => {
  try {
    const ip = getClientIp(event);
    const rateCheck = checkRateLimit(`register:${ip}`, 10, 3600000); // 10 registrations/hr per IP
    if (!rateCheck.ok) {
      return corsResponse(429, { error: 'TOO_MANY_REQUESTS', message: '请求过于频繁，请稍后再试' }, event);
    }

    const body = getBody(event);
    const email = (body.email || '').trim().toLowerCase();
    const password = body.password || '';
    const deviceId = (event.headers?.['x-device-id'] || body.deviceId || '').trim();

    if (!isValidEmail(email)) {
      return corsResponse(400, { error: 'INVALID_EMAIL', message: '请输入有效的邮箱地址' }, event);
    }
    if (!isValidPassword(password)) {
      return corsResponse(400, { error: 'INVALID_PASSWORD', message: '密码长度至少需6位字符' }, event);
    }

    const docClient = getDocClient();

    // Check if user already exists
    const existing = await docClient.send(new GetCommand({
      TableName: TABLES.USERS,
      Key: { email },
    }));

    if (existing.Item) {
      return corsResponse(409, { error: 'USER_ALREADY_EXISTS', message: '该邮箱已被注册，请直接登录' }, event);
    }

    const passwordHash = await bcrypt.hash(password, 10);
    const now = new Date();
    const nowIso = now.toISOString();
    const currentPeriod = getCurrentPeriod();
    const tokenVersion = 1;

    // 1. Create user account
    await docClient.send(new PutCommand({
      TableName: TABLES.USERS,
      Item: {
        email,
        passwordHash,
        tokenVersion,
        lastDeviceId: deviceId || null,
        createdAt: nowIso,
        updatedAt: nowIso,
      },
    }));

    // 2. Initialize default Free tier subscription (30 compressions/month)
    const defaultSubscription = {
      userId: email,
      planType: 'free',
      status: 'active',
      monthlyQuota: 30,
      usedQuota: 0,
      currentPeriod,
      lastEmergencyDate: null,
      expiresAt: null,
      createdAt: nowIso,
      updatedAt: nowIso,
    };

    await docClient.send(new PutCommand({
      TableName: TABLES.SUBSCRIPTIONS,
      Item: defaultSubscription,
    }));

    const token = generateToken({ email, tokenVersion });
    const subSummary = buildSubscriptionSummary(defaultSubscription, now);

    return corsResponse(201, {
      message: '注册成功',
      token,
      user: { email },
      subscription: subSummary,
    }, event);
  } catch (error) {
    console.error('Register Error:', error);
    return corsResponse(500, { error: sanitizeError(error) }, event);
  }
};

/**
 * POST /api/auth/login
 * Body: { email, password, deviceId? }
 */
exports.login = async (event) => {
  try {
    const ip = getClientIp(event);
    const body = getBody(event);
    const email = (body.email || '').trim().toLowerCase();
    const password = body.password || '';
    const deviceId = (event.headers?.['x-device-id'] || body.deviceId || '').trim();

    // Rate limit: max 15 login attempts per 15 min per IP/account
    const rateCheck = checkRateLimit(`login:${ip}#${email}`, 15, 900000);
    if (!rateCheck.ok) {
      return corsResponse(429, { error: 'TOO_MANY_REQUESTS', message: '登录失败过多，请15分钟后再试' }, event);
    }

    if (!email || !password) {
      return corsResponse(400, { error: 'MISSING_FIELDS', message: '邮箱和密码不能为空' }, event);
    }

    const docClient = getDocClient();

    // Query user
    const userResult = await docClient.send(new GetCommand({
      TableName: TABLES.USERS,
      Key: { email },
    }));

    const user = userResult.Item;
    if (!user || !user.passwordHash) {
      return corsResponse(401, { error: 'INVALID_CREDENTIALS', message: '邮箱或密码错误' }, event);
    }

    const isMatch = await bcrypt.compare(password, user.passwordHash);
    if (!isMatch) {
      return corsResponse(401, { error: 'INVALID_CREDENTIALS', message: '邮箱或密码错误' }, event);
    }

    // Get or initialize subscription
    const subResult = await docClient.send(new GetCommand({
      TableName: TABLES.SUBSCRIPTIONS,
      Key: { userId: email },
    }));

    let subscription = subResult.Item;
    const now = new Date();
    const currentPeriod = getCurrentPeriod();

    if (!subscription) {
      subscription = {
        userId: email,
        planType: 'free',
        status: 'active',
        monthlyQuota: 30,
        usedQuota: 0,
        currentPeriod,
        lastEmergencyDate: null,
        expiresAt: null,
        createdAt: now.toISOString(),
        updatedAt: now.toISOString(),
      };
      await docClient.send(new PutCommand({
        TableName: TABLES.SUBSCRIPTIONS,
        Item: subscription,
      }));
    } else {
      // Lazy monthly quota rollover check
      if (subscription.currentPeriod !== currentPeriod) {
        subscription.currentPeriod = currentPeriod;
        subscription.usedQuota = 0;
        subscription.updatedAt = now.toISOString();

        await docClient.send(new UpdateCommand({
          TableName: TABLES.SUBSCRIPTIONS,
          Key: { userId: email },
          UpdateExpression: 'SET currentPeriod = :p, usedQuota = :u, updatedAt = :t',
          ExpressionAttributeValues: {
            ':p': currentPeriod,
            ':u': 0,
            ':t': now.toISOString(),
          },
        }));
      }
    }

    // Optional update last active device and timestamp
    if (deviceId) {
      await docClient.send(new UpdateCommand({
        TableName: TABLES.USERS,
        Key: { email },
        UpdateExpression: 'SET lastDeviceId = :d, lastLoginAt = :t',
        ExpressionAttributeValues: {
          ':d': deviceId,
          ':t': now.toISOString(),
        },
      }));
    }

    const token = generateToken({ email, tokenVersion: user.tokenVersion || 1 });
    const subSummary = buildSubscriptionSummary(subscription, now);

    return corsResponse(200, {
      message: '登录成功',
      token,
      user: { email },
      subscription: subSummary,
    }, event);
  } catch (error) {
    console.error('Login Error:', error);
    return corsResponse(500, { error: sanitizeError(error) }, event);
  }
};

/**
 * GET /api/auth/me
 * Header: Authorization: Bearer <token>
 */
exports.me = async (event) => {
  try {
    const authUser = await verifyAuthUser(event);
    if (!authUser) {
      return corsResponse(401, { error: 'UNAUTHORIZED', message: '登录已失效，请重新登录' }, event);
    }

    const docClient = getDocClient();
    const userResult = await docClient.send(new GetCommand({
      TableName: TABLES.USERS,
      Key: { email: authUser.email },
    }));

    if (!userResult.Item) {
      return corsResponse(404, { error: 'USER_NOT_FOUND', message: '用户不存在' }, event);
    }

    const subResult = await docClient.send(new GetCommand({
      TableName: TABLES.SUBSCRIPTIONS,
      Key: { userId: authUser.email },
    }));

    const subscription = subResult.Item || { planType: 'free', monthlyQuota: 30, usedQuota: 0 };
    const subSummary = buildSubscriptionSummary(subscription, new Date());

    return corsResponse(200, {
      user: {
        email: userResult.Item.email,
        createdAt: userResult.Item.createdAt,
        lastLoginAt: userResult.Item.lastLoginAt || null,
      },
      subscription: subSummary,
    }, event);
  } catch (error) {
    console.error('Me Error:', error);
    return corsResponse(500, { error: sanitizeError(error) }, event);
  }
};

/**
 * POST /api/auth/logout
 * Header: Authorization: Bearer <token>
 * Invalidates all existing JWT tokens for this user by incrementing tokenVersion.
 */
exports.logout = async (event) => {
  try {
    const authUser = await verifyAuthUser(event);
    if (!authUser) {
      return corsResponse(200, { message: '已退出登录' }, event);
    }

    const docClient = getDocClient();
    await docClient.send(new UpdateCommand({
      TableName: TABLES.USERS,
      Key: { email: authUser.email },
      UpdateExpression: 'SET tokenVersion = if_not_exists(tokenVersion, :one) + :one, updatedAt = :t',
      ExpressionAttributeValues: {
        ':one': 1,
        ':t': new Date().toISOString(),
      },
    }));

    return corsResponse(200, { message: '登出成功，已在服务端吊销登录凭据' }, event);
  } catch (error) {
    console.error('Logout Error:', error);
    return corsResponse(500, { error: sanitizeError(error) }, event);
  }
};

module.exports.PLAN_LIMITS = PLAN_LIMITS;
module.exports.buildSubscriptionSummary = buildSubscriptionSummary;
