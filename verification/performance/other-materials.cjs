const fs = require('fs');
const http = require('http');
const assert = require('assert/strict');
const moduleRoot = 'E:/解决服务器问题新盘/FXVault-runtime-test-20260920/bot';
const mineflayer = require(require.resolve('mineflayer', {paths: [moduleRoot]}));
const data = require(require.resolve('minecraft-data', {paths: [moduleRoot]}));
const {Vec3} = require(require.resolve('vec3', {paths: [moduleRoot]}));
data('26.1').version.version = 776;
data.versionsByMinecraftVersion.pc['26.1'].version = 776;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const logFile = 'E:/EcoEnchants/audit/optimization-20261110/evidence/'+process.argv[2]+'-server.log';
const outputFile = 'E:/EcoEnchants/audit/optimization-20261110/evidence/'+process.argv[2]+'-other-material-results.json';
const rows = [];
let bot, registry;
async function until(fn, timeout = 12000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) { const result = fn(); if (result) return result; await sleep(50); }
  throw Error('Timeout: ' + fn.toString());
}
function save() { fs.writeFileSync(outputFile, JSON.stringify({rows}, null, 2), 'utf8'); }
async function command(text, label) {
  const offset = fs.statSync(logFile).size;
  await new Promise((resolve, reject) => {
    const request = http.request('http://127.0.0.1:25693', {method: 'POST'}, response => {
      response.resume(); response.on('end', resolve);
    });
    request.on('error', reject); request.end(text);
  });
  if (label) return until(() => {
    const text = fs.readFileSync(logFile).subarray(offset).toString('utf8');
    const match = text.match(new RegExp('\\[PREVIEW\\] ' + label + ' (\\{[^\\n]+\\}|[^\\n]+)'));
    return match && {value: JSON.parse(match[1])};
  }).then(r => r.value);
  await sleep(200);
}
async function preview(slot, material, seed, family, repeat) {
  const window = bot.currentWindow;
  const home = window.hotbarStart + slot;
  await window.putTargetItem(window.slots[home]);
  await until(() => window.slots[0] && window.enchantments.every(e => e.level > 0 && e.expected.enchant >= 0));
  await sleep(150);
  const result = {seed, family, material, repeat, level: bot.experience.level,
    lapis: window.slots[1]?.count, offers: window.enchantments.map(e => ({
      key: registry.entries[e.expected.enchant]?.key, level: e.expected.level, cost: e.level
    }))};
  rows.push(result); save();
  await bot.moveSlotItem(0, home);
  await until(() => !window.slots[0] && window.slots[home] && !window.selectedItem);
  await sleep(120);
  return result;
}
async function main() {
  bot = mineflayer.createBot({host: '127.0.0.1', port: 25692, username: 'MatAudit1106', auth: 'offline', version: '26.1', plugins: {resource_pack: false}, hideErrors: true});
  bot.on('error', error => console.error(error.message));
  bot._client.on('registry_data', p => { if (String(p.id ?? p.registryId).includes('enchantment')) registry = p; });
  bot._client.on('add_resource_pack', p => bot._client.write('resource_pack_receive', {uuid: p.uuid, result: 1}));
  await new Promise((resolve, reject) => { const timer = setTimeout(() => reject(Error('Spawn timeout')), 30000); bot.once('spawn', () => {clearTimeout(timer); resolve();}); });
  await command('ep setup ' + bot.username, 'SETUP');
  const before = await command('ep state ' + bot.username, 'STATE');
  await command('ep families '+bot.username,'FAMILIES');
  const groups = [];
  for (const family of ['axe','shovel','hoe','spear','helmet','chestplate','leggings','boots']) {
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    await sleep(250);
    await command('minecraft:clear ' + bot.username);
    const tiers=['helmet','chestplate','leggings','boots'].includes(family)?['leather','chainmail','copper','iron','golden','diamond','netherite']:['wooden','stone','copper','iron','golden','diamond','netherite']; const materials=tiers.map(t=>t+'_'+family);
    for (const material of materials) await command('minecraft:give ' + bot.username + ' minecraft:' + material + ' 1');
    await command('minecraft:give ' + bot.username + ' minecraft:lapis_lazuli 64');
    await until(() => bot.inventory.items().length === 8);
    for (const seed of [123456789]) {
      await command('ep seed ' + bot.username + ' ' + seed, 'SEED');
      const window = await bot.openEnchantmentTable(bot.blockAt(new Vec3(8, 101, 8)));
      await window.putLapis(window.slots[window.hotbarStart + 7]);
      const signatures = [];
      for (let slot = 0; slot < materials.length; slot++) {
        const a = await preview(slot, materials[slot], seed, family, 0);
        const b = await preview(slot, materials[slot], seed, family, 1);
        assert.deepEqual(a.offers, b.offers, 'Same material must remain stable');
        signatures.push(JSON.stringify(a.offers.map(o => [o.key, o.level])));
      }
      const state = await command('ep state ' + bot.username, 'STATE');
      assert.equal(state.seed, seed, 'Preview must not advance round');
      assert.equal(state.level, before.level, 'Preview must not cost XP');
      assert.equal(window.slots[1].count, 64, 'Preview must not cost lapis');
      const unique = new Set(signatures).size;
      assert.equal(unique, 1, 'Same family must share one preview');
      const group = {seed, family, unique, materials}; groups.push(group);
      console.log(JSON.stringify(group));
      bot.closeWindow(window); await sleep(250);
    }
  }
  const paid=[];
  await command('ep seed ' + bot.username + ' ' + before.seed, 'SEED');
  await command('minecraft:clear ' + bot.username);
  bot.quit();
  fs.writeFileSync(outputFile, JSON.stringify({passed: true, before, rows, groups,
    totalPreviews: rows.length, sameMaterialComparisons: rows.length / 2,
    materialVariationConfirmed: true, paidEnchantsPerformed: paid.length, paid}, null, 2), 'utf8');
}
main().then(() => setTimeout(() => process.exit(0), 500)).catch(error => {
  save(); console.error(error.stack); if (bot) bot.quit(); setTimeout(() => process.exit(1), 500);
});
