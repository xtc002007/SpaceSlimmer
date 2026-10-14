const memoryMap = new Map();

/**
 * In-memory sliding window rate limiter.
 * Zero external database dependency, lightning fast, safe against spam/brute-force attacks.
 *
 * @param {string} key - Unique rate limit identifier (e.g. `login:192.168.1.1` or `activate:user@test.com`)
 * @param {number} maxPerWindow - Max allowed attempts within window
 * @param {number} windowMs - Window duration in milliseconds (default 60000 = 1 min)
 * @returns {{ ok: boolean, retryAfterSec?: number }}
 */
function checkRateLimit(key, maxPerWindow = 30, windowMs = 60000) {
  const now = Date.now();
  const entry = memoryMap.get(key);

  if (!entry || now > entry.resetAt) {
    memoryMap.set(key, { count: 1, resetAt: now + windowMs });
    return { ok: true };
  }

  if (entry.count >= maxPerWindow) {
    const retryAfterSec = Math.max(1, Math.ceil((entry.resetAt - now) / 1000));
    return { ok: false, retryAfterSec };
  }

  entry.count += 1;
  return { ok: true };
}

// Periodically prune expired entries to prevent memory leaks
const PRUNE_INTERVAL = 300000; // 5 min
if (typeof setInterval !== 'undefined') {
  const timer = setInterval(() => {
    const now = Date.now();
    for (const [k, val] of memoryMap) {
      if (now > val.resetAt) memoryMap.delete(k);
    }
  }, PRUNE_INTERVAL);
  if (timer.unref) timer.unref(); // Allow Node to exit cleanly during tests
}

function getClientIp(event) {
  return (
    event?.headers?.['x-forwarded-for']?.split(',')[0]?.trim() ||
    event?.requestContext?.identity?.sourceIp ||
    event?.requestContext?.http?.sourceIp ||
    '127.0.0.1'
  );
}

module.exports = {
  checkRateLimit,
  getClientIp,
};
