'use strict'
// The real protocol client is restricted to this newly created loopback fixture.
const assert = require('node:assert/strict')
const { createRequire } = require('node:module')
const path = require('node:path')
const load = createRequire(path.join(process.env.NATIVE_PLAYER_MODULES, 'package.json'))
const mineflayer = load('mineflayer')
const port = Number(process.env.NATIVE_PLAYER_PORT)
const phase = process.env.NATIVE_RECIPE_PHASE
assert(Number.isInteger(port) && port > 1024 && port < 65536)
assert.equal(process.env.NETWORKS_NATIVE_RECIPE, 'disposable')
assert(phase === 'seed' || phase === 'verify')
const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: 'RecipeProbe', auth: 'offline', version: '1.21.11' })
const seen = []
let completed = false
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
bot.on('messagestr', text => { console.log('server:', text); seen.push(text) })
bot.on('error', error => { console.error(error); process.exitCode = 1 })
bot.on('kicked', reason => { if (!completed) { console.error('Kicked:', reason); process.exitCode = 1 } })
const deadline = setTimeout(() => { console.error('Recipe client deadline'); process.exit(1) }, 220000)
async function until(test, description) {
  const limit = Date.now() + 15000
  while (Date.now() < limit) {
    if (seen.some(s => s.startsWith('NATIVERECIPE:FAIL'))) throw new Error('Recipe server-side oracle failed')
    if (test()) return
    await sleep(50)
  }
  throw new Error('Timed out: ' + description)
}
async function command(text, expected) {
  const start = seen.length
  bot.chat('/recipefixture ' + text)
  await until(() => seen.slice(start).some(s => s === 'NATIVERECIPE:' + expected), expected)
}
async function click(slot, button, mode, next, expected) {
  await until(() => bot.currentWindow && bot.currentWindow.inventoryStart === 45, 'actual 45-slot pusher menu')
  await sleep(1100)
  console.log(`Recipe click phase=${phase} window=${bot.currentWindow.id} raw=${slot} button=${button} mode=${mode}`)
  await bot.clickWindow(slot, button, mode)
  await sleep(150)
  await command(next, expected)
}
async function run() {
  await new Promise((resolve, reject) => { bot.once('spawn', resolve); bot.once('error', reject) })
  await command('start', (phase === 'seed' ? 'pickup-' : 'verify-') + '0')
  for (let tier = 0; tier < 3; tier++) {
    if (phase === 'verify') {
      await until(() => bot.currentWindow && bot.currentWindow.inventoryStart === 45, 'reopened native pusher')
      await command('verify', tier < 2 ? 'verify-' + (tier + 1) : 'PASS')
      continue
    }
    const slot = tier < 2 ? 16 : 7
    await click(slot, 0, 0, 'pickup', 'store-' + tier)
    await click(45 + tier, 0, 0, 'store', 'toggle-' + tier)
    await click(slot, 0, 0, 'toggle', 'plus-' + tier)
    await click(slot, 1, 1, 'plus', 'minus-' + tier)
    await click(slot, 0, 1, 'minus', 'exact-' + tier)
    await sleep(1100)
    console.log(`Recipe exact-batch prompt window=${bot.currentWindow.id} raw=${slot}`)
    await bot.clickWindow(slot, 1, 0)
    await until(() => !bot.currentWindow, 'ordinary chat prompt closes inventory')
    bot.chat('5')
    await until(() => bot.currentWindow && bot.currentWindow.inventoryStart === 45, 'ordinary batch chat reopens inventory')
    await command('exact', 'off-' + tier)
    await click(slot, 0, 0, 'off', 'on-' + tier)
    await click(slot, 0, 0, 'on', 'close-' + tier)
    bot.closeWindow(bot.currentWindow)
    await command('reopen', tier < 2 ? 'pickup-' + (tier + 1) : 'PASS')
  }
  completed = true
  console.log('PASS: actual Minecraft client completed native recipe ' + phase)
  clearTimeout(deadline)
  bot.quit('Recipe fixture complete')
}
run().catch(error => { console.error(error); bot.end(); clearTimeout(deadline); process.exitCode = 1 })
