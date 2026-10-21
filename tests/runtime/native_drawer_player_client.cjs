'use strict'
// Actual protocol clicks, restricted to the runner's new loopback fixture.
const assert = require('node:assert/strict')
const { createRequire } = require('node:module')
const path = require('node:path')
const load = createRequire(path.join(process.env.NATIVE_PLAYER_MODULES, 'package.json'))
const mineflayer = load('mineflayer')
const port = Number(process.env.NATIVE_PLAYER_PORT)
const phase = process.env.NATIVE_DRAWER_PHASE
assert(Number.isInteger(port) && port > 1024 && port < 65536)
assert.equal(process.env.NETWORKS_NATIVE_DRAWER, 'disposable')
assert(phase === 'seed' || phase === 'verify')
const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: 'DrawerProbe', auth: 'offline', version: '1.21.11' })
const seen = []
let completed = false
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
bot.on('messagestr', text => { console.log('server:', text); seen.push(text) })
bot.on('error', error => { console.error(error); process.exitCode = 1 })
bot.on('kicked', reason => { if (!completed) { console.error('Kicked:', reason); process.exitCode = 1 } })
const deadline = setTimeout(() => { console.error('Drawer client deadline'); process.exit(1) }, 220000)
async function until(test, description) {
  const limit = Date.now() + 15000
  while (Date.now() < limit) {
    if (seen.some(s => s.startsWith('NATIVEDRAWER:FAIL'))) throw new Error('Drawer server-side oracle failed')
    if (test()) return
    await sleep(50)
  }
  throw new Error('Timed out: ' + description)
}
async function command(text, expected) {
  const start = seen.length
  bot.chat('/drawerfixture ' + text)
  await until(() => seen.slice(start).some(s => s === 'NATIVEDRAWER:' + expected), expected)
}
async function click(slot, button, mode, commandName, expected) {
  await until(() => bot.currentWindow && bot.currentWindow.inventoryStart === 54, 'actual 54-slot Drawer menu')
  await sleep(400)
  console.log(`Drawer click phase=${phase} window=${bot.currentWindow.id} raw=${slot} button=${button} mode=${mode}`)
  await bot.clickWindow(slot, button, mode)
  await sleep(150)
  await command(commandName, expected)
}
async function run() {
  await new Promise((resolve, reject) => { bot.once('spawn', resolve); bot.once('error', reject) })
  await command('start', phase === 'seed' ? 'lock' : 'verify-withdraw')
  if (phase === 'verify') {
    await click(10, 0, 0, 'verify-withdraw', 'verify-deposit')
    await click(10, 1, 1, 'verify-deposit', 'PASS')
  } else {
    const steps = [
      [8, 0, 0, 'lock', 'void'],
      [7, 0, 0, 'void', 'partial'],
      [10, 1, 0, 'partial', 'full'],
      [10, 1, 0, 'full', 'bulk'],
      [10, 1, 1, 'bulk', 'cursor-pickup'],
      [27, 0, 0, 'cursor-pickup', 'cursor-deposit'],
      [11, 0, 0, 'cursor-deposit', 'choose-withdraw'],
      [10, 0, 0, 'choose-withdraw', 'choose-pickup'],
      [81, 0, 0, 'choose-pickup', 'choose-place'],
      [27, 0, 0, 'choose-place', 'quantum-from'],
      [18, 0, 0, 'quantum-from', 'quantum-toggle'],
      [18, 1, 0, 'quantum-toggle', 'quantum-to'],
      [18, 0, 0, 'quantum-to', 'big-pickup'],
      [9, 0, 0, 'big-pickup', 'big-store'],
      [54, 0, 0, 'big-store', 'small-pickup'],
      [80, 0, 0, 'small-pickup', 'small-place'],
      [9, 0, 0, 'small-place', 'small-toggle'],
      [18, 1, 0, 'small-toggle', 'small-drain'],
      [18, 0, 0, 'small-drain', 'PASS']
    ]
    for (const step of steps) await click(...step)
  }
  completed = true
  console.log('PASS: actual Minecraft client completed native Drawer ' + phase)
  clearTimeout(deadline)
  bot.quit('Drawer fixture complete')
}
run().catch(error => { console.error(error); bot.end(); clearTimeout(deadline); process.exitCode = 1 })
