const { DynamoDBClient } = require('@aws-sdk/client-dynamodb');
const { DynamoDBDocumentClient, GetCommand } = require('@aws-sdk/lib-dynamodb');
const crypto = require('crypto');
const { getJwtSecret, timingSafeEqualString } = require('./security');

let client;
let docClient;

function getClient() {
  if (!client) {
    const config = {
      region: process.env.AWS_REGION || 'us-east-2',
    };
    if (process.env.DYNAMODB_ENDPOINT) {
      config.endpoint = process.env.DYNAMODB_ENDPOINT;
    }
    client = new DynamoDBClient(config);
  }
  return client;
}

function getDocClient() {
  if (!docClient) {
    docClient = DynamoDBDocumentClient.from(getClient());
  }
  return docClient;
}

// Allow injecting mock client for automated testing
function setMockDocClient(mock) {
  docClient = mock;
}

const TABLES = {
  USERS: 'spaceslimmer-users',
  SUBSCRIPTIONS: 'spaceslimmer-subscriptions',
  LICENSES: 'spaceslimmer-licenses',
};

function sanitizeError(error) {
  const message = error?.message || String(error);
  console.error('[SpaceSlimmer Error]:', message);
  if (process.env.NODE_ENV === 'production') {
    return 'An internal error occurred';
  }
  return message;
}

function corsResponse(statusCode, body, event = null) {
  return {
    statusCode,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Content-Type,Authorization,X-Client-Version,X-Device-Id',
      'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS',
    },
    body: JSON.stringify(body),
  };
}

function getBody(event) {
  if (!event || !event.body) return {};
  if (typeof event.body === 'object') return event.body;
  try {
    return JSON.parse(event.body);
  } catch {
    return {};
  }
}

function generateToken(payload) {
  const secret = getJwtSecret();
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const now = Math.floor(Date.now() / 1000);
  const tokenPayload = { ...payload, iat: now, exp: now + 86400 }; // 24-hour expiry
  const payloadEnc = Buffer.from(JSON.stringify(tokenPayload)).toString('base64url');
  const signature = crypto
    .createHmac('sha256', secret)
    .update(`${header}.${payloadEnc}`)
    .digest('base64url');
  return `${header}.${payloadEnc}.${signature}`;
}

function verifyToken(token) {
  try {
    const secret = getJwtSecret();
    if (!token) return null;
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const [header, payload, signature] = parts;

    let parsedHeader;
    try {
      parsedHeader = JSON.parse(Buffer.from(header, 'base64url').toString());
    } catch {
      return null;
    }
    if (parsedHeader?.alg !== 'HS256' || parsedHeader?.typ !== 'JWT') return null;

    const expectedSig = crypto
      .createHmac('sha256', secret)
      .update(`${header}.${payload}`)
      .digest('base64url');
    if (!timingSafeEqualString(signature, expectedSig)) return null;

    const decoded = JSON.parse(Buffer.from(payload, 'base64url').toString());
    if (decoded.exp && decoded.exp < Math.floor(Date.now() / 1000)) return null;
    return decoded;
  } catch {
    return null;
  }
}

function getAuthUser(event) {
  const authHeader = event?.headers?.authorization || event?.headers?.Authorization || '';
  if (!authHeader.startsWith('Bearer ')) return null;
  return verifyToken(authHeader.slice(7).trim());
}

async function verifyAuthUser(event) {
  const payload = getAuthUser(event);
  if (!payload || !payload.email) return null;

  try {
    const client = getDocClient();
    const result = await client.send(new GetCommand({
      TableName: TABLES.USERS,
      Key: { email: payload.email.toLowerCase().trim() },
    }));
    if (!result.Item) return null;

    const dbVersion = result.Item.tokenVersion || 0;
    const tokenVersion = payload.tokenVersion || 0;
    if (tokenVersion < dbVersion) return null;

    return { ...payload, email: result.Item.email };
  } catch (err) {
    console.error('verifyAuthUser error:', err);
    return null;
  }
}

module.exports = {
  getDocClient,
  setMockDocClient,
  TABLES,
  corsResponse,
  getBody,
  generateToken,
  verifyToken,
  getAuthUser,
  verifyAuthUser,
  sanitizeError,
};
