'use strict'
// This actual protocol client accepts only the fresh loopback fixture.
const assert = require('node:assert/strict')
const { createRequire } = require('node:module')
const path = require('node:path')
const load = createRequire(path.join(process.env.NATIVE_PLAYER_MODULES, 'package.json'))
const mineflayer = load('mineflayer')
const port = Number(process.env.NATIVE_PLAYER_PORT)
assert(Number.isInteger(port) && port > 1024 && port < 65536)
assert.equal(process.env.NETWORKS_NATIVE_IE2, 'disposable')
const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: 'NetworksProbe', auth: 'offline', version: '1.21.11' })
const seen = []
let completed = false
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
bot.on('messagestr', text => { console.log('server:', text); seen.push(text) })
bot.on('error', error => { console.error(error); process.exitCode = 1 })
bot.on('kicked', reason => { if (!completed) { console.error('Kicked:', reason); process.exitCode = 1 } })
const deadline = setTimeout(() => { console.error('IE2 client deadline'); process.exit(1) }, 180000)
async function until(test, description) {
  const limit = Date.now() + 15000
  while (Date.now() < limit) {
    if (seen.some(s => s.startsWith('NATIVEIE2:FAIL'))) throw new Error('IE2 server-side ledger failed')
    if (test()) return
    await sleep(50)
  }
  throw new Error('Timed out: ' + description)
}
async function command(text, expected) {
  const start = seen.length
  bot.chat('/ie2fixture ' + text)
  await until(() => seen.slice(start).some(s => s === 'NATIVEIE2:' + expected), expected)
}
async function click(slot, next, expected) {
  await until(() => bot.currentWindow && bot.currentWindow.inventoryStart === 36, 'actual 36-slot IE2 Storage Unit')
  // Respect the configured interaction cooldown; do not use a flood to pass this test.
  await sleep(1100)
  console.log(`IE2 click window=${bot.currentWindow.id} raw=${slot}`)
  await bot.clickWindow(slot, 0, 0)
  await sleep(150)
  await command(next, expected)
}
async function run() {
  await new Promise((resolve, reject) => { bot.once('spawn', resolve); bot.once('error', reject) })
  await command('start', 'deposit-ready')
  await click(28, 'deposit', 'single-ready')
  await click(34, 'single', 'all-ready')
  await click(35, 'all', 'close-ready')
  bot.closeWindow(bot.currentWindow)
  await command('reopen', 'partial-prepare')
  await command('partial-prepare', 'partial-deposit-ready')
  await click(28, 'partial-deposit', 'partial-single-ready')
  await click(34, 'partial-single', 'full-single-ready')
  await click(34, 'full-single', 'full-all-ready')
  await click(35, 'full-all', 'limited-all-ready')
  await click(35, 'limited-all', 'wrong-owner-ready')
  await click(28, 'wrong-owner', 'held-close-ready')
  bot.closeWindow(bot.currentWindow)
  await command('held-reopen', 'held-single-ready')
  await click(34, 'held-single', 'held-all-ready')
  await click(35, 'held-all', 'held-deposit-ready')
  await click(28, 'held-deposit', 'held-void-ready')
  await click(27, 'finish', 'PASS')
  completed = true
  console.log('PASS: actual Minecraft client completed every IE2 button ledger')
  clearTimeout(deadline)
  bot.quit('IE2 fixture complete')
}
run().catch(error => { console.error(error); bot.end(); clearTimeout(deadline); process.exitCode = 1 })
