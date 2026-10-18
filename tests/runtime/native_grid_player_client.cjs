'use strict'
// A genuine local client. No remote host, credentials, account or server is accepted.
const assert = require('node:assert/strict')
const { createRequire } = require('node:module')
const path = require('node:path')
const load = createRequire(path.join(process.env.NATIVE_PLAYER_MODULES, 'package.json'))
const mineflayer = load('mineflayer')
const port = Number(process.env.NATIVE_PLAYER_PORT)
assert(Number.isInteger(port) && port > 1024 && port < 65536)
assert.equal(process.env.NETWORKS_NATIVE_PLAYER, 'disposable')
const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: 'NetworksProbe', auth: 'offline', version: '1.21.11' })
let completed = false
const seen = []
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
bot.on('messagestr', text => { console.log('server:', text); seen.push(text) })
bot.on('error', error => { console.error(error); process.exitCode = 1 })
bot.on('kicked', reason => { if (!completed) { console.error('Kicked:', reason); process.exitCode = 1 } })
const deadline = setTimeout(() => { console.error('Client deadline exceeded'); process.exit(1) }, 180000)
async function until(test, description) {
  const limit = Date.now() + 15000
  while (Date.now() < limit) {
    if (seen.some(text => text.startsWith('NATIVEGRID:FAIL'))) throw new Error('Server-side item ledger failed')
    if (test()) return
    await sleep(50)
  }
  throw new Error('Timed out: ' + description)
}
async function command(text, expected) {
  const start = seen.length
  bot.chat('/networksfixture ' + text)
  await until(() => seen.slice(start).some(line => line === 'NATIVEGRID:' + expected), expected)
}
async function click(slot, mode = 0) {
  assert(bot.currentWindow, 'Expected the actual Grid window')
  console.log(`click window=${bot.currentWindow.id} slot=${slot} mode=${mode}`)
  await bot.clickWindow(slot, 0, mode)
  await sleep(150)
}
async function run() {
  await new Promise((resolve, reject) => { bot.once('spawn', resolve); bot.once('error', reject) })
  await command('start', 'shift-ready')
  await until(() => bot.currentWindow && bot.currentWindow.inventoryStart === 54, 'real 54-slot Grid')
  assert.equal(bot.currentWindow.slots[54]?.name, 'diamond', 'Seeded inventory slot must be visible to the client')
  await click(54, 1)
  await command('check-shift', 'withdraw-ready')
  const displaySlots = [9, 10, 11, 12, 13, 18, 19, 20, 21, 22, 27, 28, 29, 30, 31, 36, 37, 38, 39, 40]
  let display = -1
  await until(() => {
    display = displaySlots.find(slot => bot.currentWindow?.slots[slot]?.name === 'diamond') ?? -1
    return display >= 0
  }, 'storage item rendered by the actual Grid')
  await click(display)
  await command('check-cursor', 'close-ready')
  bot.closeWindow(bot.currentWindow)
  await command('check-close', 'transfer-pass')
  await command('prepare-craft', 'craft-ready')
  await click(34)
  await command('check-craft', 'second-craft-ready')
  await click(34)
  await command('check-second', 'craft-pass')
  await command('prepare-cancel', 'cancel-ready')
  await click(34)
  await command('check-cancel', 'reentry-ready')
  await click(34)
  await command('check-reentry', 'reopen-ready')
  bot.closeWindow(bot.currentWindow)
  await command('reopen', 'output-ready')
  assert.equal(bot.currentWindow.slots[43]?.name, 'stick')
  await click(43, 1)
  await command('finish', 'PASS')
  completed = true
  console.log('PASS: real Minecraft client completed every server-verified Grid ledger')
  clearTimeout(deadline)
  bot.quit('Native fixture complete')
}
run().catch(error => { console.error(error); bot.end(); clearTimeout(deadline); process.exitCode = 1 })
