const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const net = require('node:net');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const root = 'E:/EcoEnchants/audit/optimization-20261110';
const modules = 'E:/解决服务器问题新盘/FXVault-runtime-test-20260920/bot';
const mineflayer = require(require.resolve('mineflayer', { paths: [modules] }));
const data = require(require.resolve('minecraft-data', { paths: [path.dirname(require.resolve('mineflayer', { paths: [modules] }))] }));
data('26.1').version.version = 776;
data.versionsByMinecraftVersion.pc['26.1'].version = 776;
const tag = process.argv[2];
assert(tag && /^[a-z0-9-]+$/.test(tag));
const logPath = `${root}/evidence/${tag}-server.log`;
assert(!fs.existsSync(logPath), 'Use a fresh run tag');
const logStream = fs.createWriteStream(logPath);
let log = '', server, bot, control;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(predicate, timeout, description) {
    const deadline = Date.now() + timeout;
    while (!predicate()) {
        if (Date.now() > deadline) throw new Error('Timeout waiting for ' + description);
        if (server) assert.equal(server.exitCode, null, 'Unexpected server exit');
        await delay(250);
    }
}
async function child(script, args) {
    const child = spawn(process.execPath, [path.join(__dirname, script), ...args], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    const output = fs.createWriteStream(`${root}/evidence/${tag}-${script}-${args[1] || 'default'}.log`);
    child.stdout.on('data', bytes => output.write(bytes));
    child.stderr.on('data', bytes => output.write(bytes));
    const code = await new Promise(resolve => child.on('exit', resolve));
    output.end();
    assert.equal(code, 0, script + ' ' + args.join(' '));
    console.log('PASS', script, args.join(' '));
}
async function main() {
    await new Promise((resolve, reject) => {
        const socket = net.createServer();
        socket.on('error', reject);
        socket.listen(25692, '127.0.0.1', () => socket.close(resolve));
    });
    try {
        server = spawn('D:/java/bin/java.exe', ['-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-Dpaper.disablePluginRemapping=true', '-Xms512M', '-Xmx3G', '-jar', 'server.jar', 'nogui'], { cwd: `${root}/server`, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
        for (const stream of [server.stdout, server.stderr]) stream.on('data', bytes => { const text = bytes.toString('utf8'); log += text; logStream.write(text); });
        control = http.createServer((req, res) => {
            let body = ''; req.on('data', data => body += data);
            req.on('end', () => { server.stdin.write(body + '\n'); res.end('accepted'); });
        }).listen(25693, '127.0.0.1');
        await until(() => log.includes('Done (') && log.includes('[OPT] READY'), 240000, 'server startup');
        console.log('Server ready, exact core and final JAR');
        bot = mineflayer.createBot({ host: '127.0.0.1', port: 25692, username: 'OptPerfA', auth: 'offline', version: '26.1', plugins: { resource_pack: false }, hideErrors: true });
        bot.on('error', e => console.log('CLIENT_ERROR', String(e)));
        bot.on('kicked', e => console.log('CLIENT_KICKED', JSON.stringify(e)));
        await until(() => log.includes('[OPT] COMPLETE') || log.includes('[OPT] FAIL'), 240000, 'old/new oracle and benchmark');
        assert(!log.includes('[OPT] FAIL'), 'Oracle failed');
        const result = JSON.parse(fs.readFileSync(`${root}/evidence/runtime-current.json`, 'utf8'));
        assert(!result.observations.failure);
        fs.writeFileSync(`${root}/evidence/${tag}-results.json`, JSON.stringify(result, null, 2));
        console.log('Oracle PASS', result.checks.length, 'checks', result.observations.displayComparisons, 'display comparisons', result.observations.previewComparisons, 'preview comparisons');
        bot.quit(); bot = null; await delay(1800);
        if (process.argv.includes('--resume')) await child('native-regression.cjs', [tag, 'resume', 'OptNative']);
        if (!process.argv.includes('--oracle-only')) {
            await child('native-regression.cjs', [tag, 'full', 'OptNative']);
            await child('native-regression.cjs', [tag, 'matrix', 'OptMatrix']);
            await child('material-test.cjs', [tag]);
            await child('other-materials.cjs', [tag]);
            await child('turtle-materials.cjs', [tag]);
        }
    } finally {
        if (bot) bot.quit();
        await delay(1800);
        if (server && server.exitCode === null) {
            server.stdin.write('stop\n');
            await new Promise((resolve, reject) => {
                const timeout = setTimeout(() => reject(new Error('Normal shutdown timeout')), 120000);
                server.once('exit', code => { clearTimeout(timeout); code === 0 ? resolve() : reject(new Error('Exit ' + code)); });
            });
            console.log('Normal shutdown exit 0');
        }
        if (control) control.close();
        logStream.end();
    }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
