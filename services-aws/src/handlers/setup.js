const {
  DynamoDBClient,
  CreateTableCommand,
  ListTablesCommand,
  DescribeTableCommand,
} = require('@aws-sdk/client-dynamodb');
const { DynamoDBDocumentClient, PutCommand } = require('@aws-sdk/lib-dynamodb');

const client = new DynamoDBClient({
  region: process.env.AWS_REGION || 'us-east-2',
  ...(process.env.DYNAMODB_ENDPOINT ? { endpoint: process.env.DYNAMODB_ENDPOINT } : {}),
});
const docClient = DynamoDBDocumentClient.from(client);

const TABLES = [
  {
    TableName: 'spaceslimmer-users',
    KeySchema: [{ AttributeName: 'email', KeyType: 'HASH' }],
    AttributeDefinitions: [{ AttributeName: 'email', AttributeType: 'S' }],
    BillingMode: 'PAY_PER_REQUEST',
  },
  {
    TableName: 'spaceslimmer-subscriptions',
    KeySchema: [{ AttributeName: 'userId', KeyType: 'HASH' }],
    AttributeDefinitions: [{ AttributeName: 'userId', AttributeType: 'S' }],
    BillingMode: 'PAY_PER_REQUEST',
  },
  {
    TableName: 'spaceslimmer-licenses',
    KeySchema: [{ AttributeName: 'licenseKey', KeyType: 'HASH' }],
    AttributeDefinitions: [{ AttributeName: 'licenseKey', AttributeType: 'S' }],
    BillingMode: 'PAY_PER_REQUEST',
  },
];

const SEED_LICENSES = [
  {
    licenseKey: 'SS-PRO-1M-DEMO01',
    planType: 'pro-monthly',
    status: 'available',
    boundUser: null,
    validUntil: null,
    note: 'Demo 1 Month PRO License',
  },
  {
    licenseKey: 'SS-PRO-1Y-DEMO01',
    planType: 'pro-yearly',
    status: 'available',
    boundUser: null,
    validUntil: null,
    note: 'Demo 1 Year PRO License',
  },
  {
    licenseKey: 'SS-PRO-LIFE-DEMO01',
    planType: 'pro-lifetime',
    status: 'available',
    boundUser: null,
    validUntil: null,
    note: 'Demo Lifetime PRO License',
  },
];

async function waitForTableActive(tableName) {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const desc = await client.send(new DescribeTableCommand({ TableName: tableName }));
    if (desc.Table?.TableStatus === 'ACTIVE') return;
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error(`Timed out waiting for ${tableName} to become ACTIVE`);
}

async function setupTables() {
  console.log('🚀 Starting SpaceSlimmer DynamoDB Setup...');
  const existingTables = await client.send(new ListTablesCommand({}));
  const tableNames = new Set(existingTables.TableNames || []);

  for (const table of TABLES) {
    if (tableNames.has(table.TableName)) {
      console.log(`  ✓ Table already exists: ${table.TableName}`);
    } else {
      console.log(`  + Creating table: ${table.TableName}...`);
      await client.send(new CreateTableCommand(table));
      console.log(`  ✓ Created: ${table.TableName}`);
    }
  }

  // Wait for spaceslimmer-licenses to become ACTIVE
  await waitForTableActive('spaceslimmer-licenses');

  // Seed sample licenses
  console.log('\n🔑 Seeding demo PRO licenses...');
  const now = new Date().toISOString();
  for (const lic of SEED_LICENSES) {
    await docClient.send(new PutCommand({
      TableName: 'spaceslimmer-licenses',
      Item: {
        ...lic,
        createdAt: now,
        updatedAt: now,
      },
    }));
    console.log(`  ✓ Seeded License: ${lic.licenseKey} (${lic.planType})`);
  }

  console.log('\n🎉 SpaceSlimmer Backend Setup Completed Successfully!\n');
}

if (require.main === module) {
  setupTables().catch((err) => {
    console.error('Setup failed:', err);
    process.exit(1);
  });
}

module.exports = { setupTables };
