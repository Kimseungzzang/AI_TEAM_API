import http from 'http';
import os from 'os';
import process from 'process';
import { WebSocketServer } from 'ws';
import * as pty from 'node-pty';

const PORT = process.env.PORT ? Number(process.env.PORT) : 8090;

const server = http.createServer();
const wss = new WebSocketServer({ server });

console.log(`node version: ${process.version}`);

function resolveShellCandidates() {
  if (process.platform === 'win32') {
    const systemRoot = process.env.SystemRoot || 'C:\\\\Windows';
    return [
      { shell: 'cmd.exe', args: [] },
      { shell: 'powershell.exe', args: ['-NoLogo'] },
      { shell: `${systemRoot}\\\\System32\\\\WindowsPowerShell\\\\v1.0\\\\powershell.exe`, args: ['-NoLogo'] }
    ];
  }
  return [
    { shell: '/bin/bash', args: ['--noprofile', '--norc', '-i'] },
    { shell: '/bin/zsh', args: ['-f', '-i'] },
    { shell: '/bin/sh', args: ['-i'] }
  ];
}

wss.on('connection', (ws) => {
  const cols = 80;
  const rows = 24;
  const cwd = process.env.HOME || process.cwd();
  const env = {
    ...process.env
  };
  if (process.platform !== 'win32') {
    env.TERM = 'xterm-256color';
    env.LANG = process.env.LANG || 'en_US.UTF-8';
    env.ZDOTDIR = '/tmp';
  }

  let ptyProcess = null;
  let lastError = null;
  for (const candidate of resolveShellCandidates()) {
    try {
      ptyProcess = pty.spawn(candidate.shell, candidate.args, {
        name: process.platform === 'win32' ? 'xterm' : 'xterm-256color',
        cols,
        rows,
        cwd,
        env
      });
      console.log(`spawned shell: ${candidate.shell} ${candidate.args.join(' ')}`);
      break;
    } catch (err) {
      lastError = err;
      console.error(`spawn failed for ${candidate.shell}:`, err?.message || err);
    }
  }

  if (!ptyProcess) {
    ws.send(JSON.stringify({ type: 'terminal', data: `spawn failed: ${lastError?.message || lastError}\\n` }));
    ws.close();
    return;
  }

  ws.send(JSON.stringify({ type: 'ready', data: 'shell' }));

  ptyProcess.onData((data) => {
    ws.send(JSON.stringify({ type: 'terminal', data }));
  });

  ws.on('message', (msg) => {
    let payload = null;
    try {
      payload = JSON.parse(msg.toString());
    } catch (e) {
      payload = null;
    }
    if (payload && payload.type === 'input') {
      ptyProcess.write(payload.data || '');
    } else if (payload && payload.type === 'resize') {
      const c = Number(payload.cols || 0);
      const r = Number(payload.rows || 0);
      if (c > 0 && r > 0) {
        ptyProcess.resize(c, r);
      }
    }
  });

  ws.on('close', () => {
    ptyProcess.kill();
  });
});

server.listen(PORT, () => {
  console.log(`node-pty server listening on ws://localhost:${PORT}`);
});
