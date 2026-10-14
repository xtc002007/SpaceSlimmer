const { GetCommand, PutCommand, UpdateCommand } = require('@aws-sdk/lib-dynamodb');
const {
  getDocClient,
  TABLES,
  corsResponse,
  getBody,
  verifyAuthUser,
  sanitizeError,
} = require('./shared/db');
const { checkRateLimit, getClientIp } = require('./shared/rateLimit');
const { PLAN_LIMITS, buildSubscriptionSummary } = require('./auth');

const LICENSE_KEY_REGEX = /^[A-Z0-9_-]{6,64}$/;

function getCurrentPeriod() {
  return new Date().toISOString().slice(0, 7); // 'YYYY-MM'
}

function getTodayDate() {
  return new Date().toISOString().slice(0, 10); // 'YYYY-MM-DD'
}

/**
 * Helper to ensure monthly quota is properly initialized/reset for a new month.
 */
async function ensureMonthlyQuota(docClient, subscription, userId, now) {
  const currentPeriod = getCurrentPeriod();
  if (subscription.currentPeriod !== currentPeriod) {
    subscription.currentPeriod = currentPeriod;
    subscription.usedQuota = 0;
    subscription.updatedAt = now.toISOString();

    await docClient.send(new UpdateCommand({
      TableName: TABLES.SUBSCRIPTIONS,
      Key: { userId },
      UpdateExpression: 'SET currentPeriod = :p, usedQuota = :u, updatedAt = :t',
      ExpressionAttributeValues: {
        ':p': currentPeriod,
        ':u': 0,
        ':t': now.toISOString(),
      },
    }));
  }
  return subscription;
}

/**
 * Check if the subscription is PRO and active.
 */
function evaluateProStatus(subscription, now = new Date()) {
  const isProPlan = Boolean(subscription.planType && subscription.planType.startsWith('pro-'));
  if (!isProPlan) {
    return { isPro: false, status: subscription.status || 'active' };
  }

  // Lifetime never expires
  if (subscription.planType === 'pro-lifetime') {
    return { isPro: true, status: 'active' };
  }

  if (subscription.expiresAt) {
    const isExpired = new Date(subscription.expiresAt) <= now;
    if (isExpired) {
      return { isPro: false, status: 'expired' };
    }
  }

  return { isPro: true, status: 'active' };
}

/**
 * GET /api/subscription/status
 * Header: Authorization: Bearer <token>
 */
exports.getStatus = async (event) => {
  try {
    const authUser = await verifyAuthUser(event);
    if (!authUser) {
      return corsResponse(401, { error: 'UNAUTHORIZED', message: '登录已失效，请重新登录' }, event);
    }

    const docClient = getDocClient();
    const subResult = await docClient.send(new GetCommand({
      TableName: TABLES.SUBSCRIPTIONS,
      Key: { userId: authUser.email },
    }));

    const now = new Date();
    let subscription = subResult.Item;
    const currentPeriod = getCurrentPeriod();

    if (!subscription) {
      subscription = {
        userId: authUser.email,
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
      subscription = await ensureMonthlyQuota(docClient, subscription, authUser.email, now);
    }

    const subSummary = buildSubscriptionSummary(subscription, now);
    return corsResponse(200, subSummary, event);
  } catch (error) {
    console.error('GetStatus Error:', error);
    return corsResponse(500, { error: sanitizeError(error) }, event);
  }
};

/**
 * POST /api/subscription/consume
 * Header: Authorization: Bearer <token>
 * Request body: { count: 1, mediaType: 'video' | 'photo' | 'audio', deviceId? }
 */
exports.consume = async (event) => {
  try {
    const authUser = await verifyAuthUser(event);
    if (!authUser) {
      return corsResponse(401, { error: 'UNAUTHORIZED', message: '登录已失效，请重新登录' }, event);
    }

    const body = getBody(event);
    const rawCount = Number(body.count);
    const count = Number.isInteger(rawCount) && rawCount > 0 ? Math.min(rawCount, 500) : 1;
    const now = new Date();
    const today = getTodayDate();
    const docClient = getDocClient();

    const subResult = await docClient.send(new GetCommand({
      TableName: TABLES.SUBSCRIPTIONS,
      Key: { userId: authUser.email },
    }));

    let subscription = subResult.Item;
    if (!subscription) {
      subscription = {
        userId: authUser.email,
        planType: 'free',
        status: 'active',
        monthlyQuota: 30,
        usedQuota: 0,
        currentPeriod: getCurrentPeriod(),
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
      subscription = await ensureMonthlyQuota(docClient, subscription, authUser.email, now);
    }

    const { isPro } = evaluateProStatus(subscription, now);

    // If PRO user: unlimited quota, always succeed
    if (isPro) {
      return corsResponse(200, {
        success: true,
        isPro: true,
        consumed: count,
        quota: {
          period: subscription.currentPeriod,
          total: 999999,
          used: subscription.usedQuota || 0,
          remaining: 999999,
          dailyEmergencyAvailable: false,
        },
      }, event);
    }

    // Free tier: check monthly quota limit
    const total = subscription.monthlyQuota || 30;
    const currentUsed = subscription.usedQuota || 0;
    const remaining = Math.max(0, total - currentUsed);

    // Case 1: Normal monthly quota consumption
    if (currentUsed + count <= total) {
      const updateResult = await docClient.send(new UpdateCommand({
        TableName: TABLES.SUBSCRIPTIONS,
        Key: { userId: authUser.email },
        UpdateExpression: 'SET usedQuota = if_not_exists(usedQuota, :zero) + :c, updatedAt = :t',
        ConditionExpression: 'attribute_not_exists(usedQuota) OR usedQuota <= :maxAllowed',
        ExpressionAttributeValues: {
          ':zero': 0,
          ':c': count,
          ':maxAllowed': total - count,
          ':t': now.toISOString(),
        },
        ReturnValues: 'ALL_NEW',
      }));

      const newUsed = updateResult.Attributes?.usedQuota || (currentUsed + count);
      return corsResponse(200, {
        success: true,
        isPro: false,
        isEmergency: false,
        consumed: count,
        quota: {
          period: subscription.currentPeriod,
          total,
          used: newUsed,
          remaining: Math.max(0, total - newUsed),
          dailyEmergencyAvailable: false,
        },
      }, event);
    }

    // Case 2: Monthly quota exhausted -> Check if Daily Emergency Quota is available
    const emergencyAvailable = subscription.lastEmergencyDate !== today;
    if (count === 1 && emergencyAvailable) {
      // Grant 1 emergency compression for today
      await docClient.send(new UpdateCommand({
        TableName: TABLES.SUBSCRIPTIONS,
        Key: { userId: authUser.email },
        UpdateExpression: 'SET lastEmergencyDate = :today, updatedAt = :t',
        ExpressionAttributeValues: {
          ':today': today,
          ':t': now.toISOString(),
        },
      }));

      return corsResponse(200, {
        success: true,
        isPro: false,
        isEmergency: true,
        consumed: 1,
        message: '当月免费额度已用尽，已为您激活今日 1 次应急免费压缩！',
        quota: {
          period: subscription.currentPeriod,
          total,
          used: currentUsed,
          remaining: 0,
          dailyEmergencyAvailable: false,
        },
      }, event);
    }

    // Case 3: Both monthly and daily emergency quota exhausted
    return corsResponse(403, {
      error: 'QUOTA_EXCEEDED',
      message: '当月免费压缩额度及今日应急额度已用尽，请升级至PRO版本以享受无限制压缩',
      isPro: false,
      quota: {
        period: subscription.currentPeriod,
        total,
        used: currentUsed,
        remaining: 0,
        dailyEmergencyAvailable: false,
      },
    }, event);
  } catch (error) {
    if (error.name === 'ConditionalCheckFailedException') {
      return corsResponse(403, {
        error: 'QUOTA_EXCEEDED',
        message: '并发压缩超出当月免费额度，请升级至PRO版本',
      }, event);
    }
    console.error('Consume Error:', error);
    return corsResponse(500, { error: sanitizeError(error) }, event);
  }
};

/**
 * POST /api/subscription/activate
 * Header: Authorization: Bearer <token>
 * Request body: { licenseKey: 'SS-XXXXXXXX-XXXXXXXX' }
 */
exports.activate = async (event) => {
  try {
    const ip = getClientIp(event);
    const authUser = await verifyAuthUser(event);
    if (!authUser) {
      return corsResponse(401, { error: 'UNAUTHORIZED', message: '登录已失效，请重新登录' }, event);
    }

    // Brute-force protection: max 10 activation attempts per 10 minutes per IP/account
    const rateCheck = checkRateLimit(`activate:${ip}#${authUser.email}`, 10, 600000);
    if (!rateCheck.ok) {
      return corsResponse(429, { error: 'TOO_MANY_REQUESTS', message: '激活尝试过多，请稍后再试' }, event);
    }

    const body = getBody(event);
    const rawKey = (body.licenseKey || '').trim().toUpperCase();

    if (!rawKey) {
      return corsResponse(400, { error: 'LICENSE_KEY_REQUIRED', message: '请输入激活码' }, event);
    }

    if (!LICENSE_KEY_REGEX.test(rawKey)) {
      return corsResponse(400, { error: 'INVALID_LICENSE_FORMAT', message: '激活码格式不正确' }, event);
    }

    const docClient = getDocClient();

    // Query license
    const licenseResult = await docClient.send(new GetCommand({
      TableName: TABLES.LICENSES,
      Key: { licenseKey: rawKey },
    }));

    const license = licenseResult.Item;
    if (!license) {
      return corsResponse(404, { error: 'INVALID_LICENSE', message: '激活码无效，请检查后重试' }, event);
    }

    // Check if license is already used
    if (license.status === 'used') {
      return corsResponse(409, { error: 'LICENSE_ALREADY_USED', message: '该激活码已被使用' }, event);
    }

    const now = new Date();

    // Check if license itself is expired
    if (license.validUntil && new Date(license.validUntil) < now) {
      return corsResponse(400, { error: 'LICENSE_EXPIRED', message: '该激活码已过使用有效期' }, event);
    }

    // Get current subscription to calculate new expiration date
    const subResult = await docClient.send(new GetCommand({
      TableName: TABLES.SUBSCRIPTIONS,
      Key: { userId: authUser.email },
    }));

    const currentSub = subResult.Item;
    const planType = license.planType || 'pro-yearly';
    let newExpiresAt = null;

    if (planType === 'pro-monthly') {
      const baseTime = (currentSub?.expiresAt && new Date(currentSub.expiresAt) > now)
        ? new Date(currentSub.expiresAt).getTime()
        : now.getTime();
      newExpiresAt = new Date(baseTime + 30 * 24 * 60 * 60 * 1000).toISOString();
    } else if (planType === 'pro-yearly') {
      const baseTime = (currentSub?.expiresAt && new Date(currentSub.expiresAt) > now)
        ? new Date(currentSub.expiresAt).getTime()
        : now.getTime();
      newExpiresAt = new Date(baseTime + 365 * 24 * 60 * 60 * 1000).toISOString();
    } else if (planType === 'pro-lifetime') {
      newExpiresAt = null; // Lifetime
    }

    // 1. Mark license as used
    await docClient.send(new UpdateCommand({
      TableName: TABLES.LICENSES,
      Key: { licenseKey: rawKey },
      UpdateExpression: 'SET #st = :used, boundUser = :u, activatedAt = :t, updatedAt = :t',
      ExpressionAttributeNames: { '#st': 'status' },
      ExpressionAttributeValues: {
        ':used': 'used',
        ':u': authUser.email,
        ':t': now.toISOString(),
      },
    }));

    // 2. Update user's subscription record
    await docClient.send(new UpdateCommand({
      TableName: TABLES.SUBSCRIPTIONS,
      Key: { userId: authUser.email },
      UpdateExpression: 'SET planType = :pt, #st = :act, expiresAt = :exp, licenseKey = :lk, updatedAt = :t',
      ExpressionAttributeNames: { '#st': 'status' },
      ExpressionAttributeValues: {
        ':pt': planType,
        ':act': 'active',
        ':exp': newExpiresAt,
        ':lk': rawKey,
        ':t': now.toISOString(),
      },
    }));

    return corsResponse(200, {
      success: true,
      message: 'PRO 版本激活成功！已解锁全部高级压缩特权。',
      subscription: {
        isPro: true,
        planType,
        status: 'active',
        expiresAt: newExpiresAt,
        limits: PLAN_LIMITS.pro,
      },
    }, event);
  } catch (error) {
    console.error('Activate Error:', error);
    return corsResponse(500, { error: sanitizeError(error) }, event);
  }
};
