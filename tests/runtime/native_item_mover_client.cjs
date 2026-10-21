'use strict'
const assert = require('node:assert/strict')
const { createRequire } = require('node:module')
const path = require('node:path')
const load = createRequire(path.join(process.env.NATIVE_PLAYER_MODULES, 'package.json'))
const mineflayer = load('mineflayer')
const { Vec3 } = load('vec3')
const port = Number(process.env.NATIVE_PLAYER_PORT)
assert(Number.isInteger(port) && port > 1024 && port < 65536)
assert.equal(process.env.ITEM_MOVER_PROBE, 'disposable')
const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: 'ItemMoverProbe', auth: 'offline', version: '1.21.11' })
const seen = []
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
let completed = false
bot.on('messagestr', message => { console.log('server:', message); seen.push(message) })
bot.on('error', error => { console.error(error); process.exitCode = 1 })
bot.on('kicked', reason => { if (!completed) { console.error(reason); process.exitCode = 1 } })
const deadline = setTimeout(() => { console.error('Native ItemMover client deadline'); process.exit(1) }, 150000)
async function until(test, description) {
  const limit = Date.now() + 30000
  while (Date.now() < limit) {
    if (seen.includes('NATIVEMOVER:FAIL')) throw new Error('Server ItemMover ledger failed')
    if (test()) return
    await pause(50)
  }
  throw new Error('Timed out: ' + description)
}
async function click(gesture, offhand, sneak) {
  await until(() => seen.includes('NATIVEMOVER:' + gesture + '-ready'), gesture + ' ready')
  await until(() => bot.inventory.slots[offhand ? 45 : 36]?.name === 'debug_stick', 'actual ItemMover in selected hand')
  const position = new Vec3(160, 100, 166)
  await until(() => bot.blockAt(position) != null, 'native Quantum block loaded')
  const block = bot.blockAt(position)
  bot.setControlState('sneak', sneak)
  await pause(150)
  if (offhand) {
    // Mineflayer's activateBlock sends hand0 only. This is its same native
    // 1.21.11 interaction packet with hand1, not a synthesized Bukkit event.
    await bot.lookAt(position.offset(0.5, 0.5, 0.5), false)
    bot._client.write('block_place', {
      location: position, direction: 1, hand: 1,
      cursorX: 0.5, cursorY: 0.5, cursorZ: 0.5,
      insideBlock: false, sequence: 0, worldBorderHit: false
    })
  } else {
    await bot.activateBlock(block)
  }
  await pause(250)
  bot.chat('/moverfixture ' + gesture)
}
async function run() {
  await new Promise((resolve, reject) => { bot.once('spawn', resolve); bot.once('error', reject) })
  await until(() => bot.entity.position.distanceTo(new Vec3(160.5, 100, 168.5)) < 1, 'fixture teleport acknowledged')
  for (const [gesture, offhand, sneak] of [
    ['take-main', false, false], ['return-main', false, true],
    ['take-off', true, false], ['return-off', true, true], ['stale-main', false, false]
  ]) await click(gesture, offhand, sneak)
  await until(() => seen.includes('NATIVEMOVER:PASS'), 'exact actual-hand ledgers')
  completed = true
  console.log('PASS: five actual native client interactions verified main/off hand and stale-event conservation')
  clearTimeout(deadline)
  bot.quit('Native ItemMover fixture complete')
}
run().catch(error => { console.error(error); clearTimeout(deadline); bot.end(); process.exitCode = 1 })
