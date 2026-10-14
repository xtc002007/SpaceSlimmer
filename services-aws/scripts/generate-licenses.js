const crypto = require('crypto');
const { DynamoDBClient } = require('@aws-sdk/client-dynamodb');
const { DynamoDBDocumentClient, PutCommand } = require('@aws-sdk/lib-dynamodb');

function generateLicenseKey(prefix = 'SS') {
  const p1 = crypto.randomBytes(2).toString('hex').toUpperCase();
  const p2 = crypto.randomBytes(4).toString('hex').toUpperCase();
  const p3 = crypto.randomBytes(4).toString('hex').toUpperCase();
  return `${prefix}-${p1}-${p2}-${p3}`;
}

async function main() {
  const args = process.argv.slice(2);
  const planArg = args[0] || 'yearly'; // 'monthly', 'yearly', 'lifetime'
  const countArg = parseInt(args[1] || '5', 10);
  const saveToDb = args.includes('--save');

  const planMap = {
    monthly: 'pro-monthly',
    yearly: 'pro-yearly',
    lifetime: 'pro-lifetime',
  };

  const planType = planMap[planArg.toLowerCase()] || 'pro-yearly';
  console.log(`\n🔑 Generating ${countArg} ${planType} licenses...`);

  let docClient = null;
  if (saveToDb) {
    const client = new DynamoDBClient({ region: process.env.AWS_REGION || 'us-east-2' });
    docClient = DynamoDBDocumentClient.from(client);
  }

  const generated = [];
  const now = new Date().toISOString();

  for (let i = 0; i < countArg; i++) {
    const key = generateLicenseKey();
    const item = {
      licenseKey: key,
      planType,
      status: 'available',
      boundUser: null,
      validUntil: null,
      createdAt: now,
      updatedAt: now,
    };

    if (saveToDb && docClient) {
      await docClient.send(new PutCommand({
        TableName: 'spaceslimmer-licenses',
        Item: item,
      }));
    }
    generated.push(item);
  }

  console.table(generated.map(g => ({ LicenseKey: g.licenseKey, Plan: g.planType, Status: g.status })));

  if (saveToDb) {
    console.log(`✓ Successfully saved ${countArg} licenses to DynamoDB 'spaceslimmer-licenses' table!\n`);
  } else {
    console.log(`ℹ Run with '--save' to insert directly into DynamoDB (e.g., node scripts/generate-licenses.js yearly 10 --save)\n`);
  }
}

if (require.main === module) {
  main().catch(console.error);
}

module.exports = { generateLicenseKey };
