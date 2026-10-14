const crypto = require('crypto');

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function timingSafeEqualString(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  if (left.length !== right.length) return false;
  return crypto.timingSafeEqual(left, right);
}

function getJwtSecret() {
  const secret = process.env.JWT_SECRET;
  if (!secret) return 'spaceslimmer-dev-secret-key-2026';
  return secret;
}

function isValidEmail(email) {
  return typeof email === 'string' && EMAIL_PATTERN.test(email.trim()) && email.length <= 254;
}

function isValidPassword(password) {
  return typeof password === 'string' && password.length >= 6 && password.length <= 200;
}

module.exports = {
  timingSafeEqualString,
  getJwtSecret,
  isValidEmail,
  isValidPassword,
};
