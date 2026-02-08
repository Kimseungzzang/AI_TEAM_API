import http from 'http';
import process from 'process';
 
import { WebSocketServer } from 'ws';
import { spawn } from 'child_process';

const PORT = process.env.PORT ? Number(process.env.PORT) : 8091;

const server = http.createServer();
const wss = new WebSocketServer({ server });

console.log(`node version: ${process.version}`);

const CLI_COMMANDS = {
  claude: {
    cmd: 'claude',
    args: (prompt) => ['-p', prompt, '--output-format', 'json']
  },
  codex: {
    cmd: 'codex',
    args: (prompt) => ['exec', prompt]
  },
  gemini: {
    cmd: 'gemini',
    args: (prompt) => ['-p', prompt, '--output-format', 'json']
  }
};

function quoteCmdArg(arg) {
  const text = String(arg ?? '');
  if (!text) return '""';
  if (!/[ \t"]/g.test(text)) return text;
  return `"${text.replace(/"/g, '""')}"`;
}

function buildWindowsCommand(cmd, args) {
  const parts = [cmd, ...args].map(quoteCmdArg).join(' ');
  // Wrap in an extra pair of quotes so cmd.exe /c treats it as a single command string
  return `"${parts}"`;
}

function extractTextFromContent(content) {
  if (!content) return '';
  if (Array.isArray(content)) {
    return content.map((c) => (typeof c === 'string' ? c : c.text || '')).join('');
  }
  if (typeof content === 'string') return content;
  if (typeof content.text === 'string') return content.text;
  return '';
}

function parseClaudeOutput(stdout) {
  try {
    const obj = JSON.parse(stdout);
    if (typeof obj.result === 'string') return obj.result;
    if (typeof obj.response === 'string') return obj.response;
    if (obj.message && obj.message.content) return extractTextFromContent(obj.message.content);
    if (obj.content) return extractTextFromContent(obj.content);
  } catch (e) {
    return '';
  }
  return '';
}

function parseGeminiOutput(stdout) {
  try {
    const obj = JSON.parse(stdout);
    if (typeof obj.response === 'string') return obj.response;
  } catch (e) {
    return '';
  }
  return '';
}

function parseCodexJsonl(stdout) {
  const lines = String(stdout || '')
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l.startsWith('{') && l.endsWith('}'));
  let last = '';
  for (const line of lines) {
    try {
      const obj = JSON.parse(line);
      const item = obj.item || obj.message || obj;
      const role = item.role || obj.role;
      if (role && role !== 'assistant') continue;
      const content = item.content || item.message?.content || obj.content;
      const text = extractTextFromContent(content);
      if (text) last = text;
      if (typeof obj.response === 'string') last = obj.response;
    } catch (e) {
      continue;
    }
  }
  return last;
}

function normalizeOutput(cli, stdout) {
  if (cli === 'codex') return parseCodexJsonl(stdout);
  if (cli === 'gemini') return parseGeminiOutput(stdout);
  return parseClaudeOutput(stdout);
}

function runCli(ws, cli, prompt, prefix) {
  const spec = CLI_COMMANDS[cli] || CLI_COMMANDS.claude;
  const cleaned = String(prompt || '').replace(/\r\n/g, '\n').trim();
  if (!cleaned) {
    ws.send(JSON.stringify({ type: 'terminal', data: `[${cli}] empty prompt\\n` }));
    return;
  }
  const isWin = process.platform === 'win32';
  if (cli === 'codex') {
    if (isWin) {
      const commandLine = 'codex exec -';
      console.log(`[codex] cmd.exe /c ${commandLine} (stdin)`);
      const child = spawn('cmd.exe', ['/d', '/s', '/c', commandLine], {
        env: { ...process.env, PYTHONIOENCODING: 'utf-8' },
        windowsHide: true,
        shell: false
      });
      child.stdin.write(cleaned + '\n');
      child.stdin.end();
      attachChild(ws, cli, child, null, prefix);
      return;
    }
    const child = spawn('codex', ['exec', '-'], {
      env: process.env,
      windowsHide: true,
      shell: false
    });
    child.stdin.write(cleaned + '\n');
            child.stdin.end();
            attachChild(ws, cli, child, null, prefix);
            return;  }

  if (isWin) {
    const commandLine = buildWindowsCommand(spec.cmd, spec.args(cleaned));
    const child = spawn('cmd.exe', ['/d', '/s', '/c', commandLine], {
      env: { ...process.env, PYTHONIOENCODING: 'utf-8' },
      windowsHide: true,
      shell: false
          });
          attachChild(ws, cli, child, null, prefix);
          return;  }
  const child = spawn(spec.cmd, spec.args(cleaned), {
    env: process.env,
    windowsHide: true,
    shell: false
        });
        attachChild(ws, cli, child, null, prefix);
      }
function attachChild(ws, cli, child, outPath, prefix) {
  let stdout = '';
  let stderr = '';

  if (child.stdout) child.stdout.setEncoding('utf8');
  if (child.stderr) child.stderr.setEncoding('utf8');

  child.stdout.on('data', (data) => {
    stdout += data.toString();
  });
  child.stderr.on('data', (data) => {
    const chunk = data.toString();
    // Codex may print non-fatal stdin notices; drop them.
    if (cli === 'codex' && /Reading prompt from stdin/i.test(chunk)) {
      return;
    }
    stderr += chunk;
  });

  child.on('error', (err) => {
    ws.send(JSON.stringify({ type: 'terminal', data: `[${cli}] spawn error: ${err?.message || err}\\n` }));
  });

  child.on('close', (code) => {
    let output = normalizeOutput(cli, stdout);
    if (!output && stdout) output = stdout;
    if (!output && stderr) output = stderr;
    if (!output) output = `[${cli}] (no output, exit ${code})`;
    ws.send(JSON.stringify({ type: 'terminal', data: prefix + output }));
  });
}

wss.on('connection', (ws) => {
  ws.on('message', (msg) => {
    let payload = null;
    try {
      payload = JSON.parse(msg.toString());
    } catch (e) {
      payload = null;
    }
    if (payload && payload.type === 'input') {
      runCli(ws, payload.cli || 'claude', payload.data || '', payload.prefix || '');
    }
  });
});

server.listen(PORT, () => {
  console.log(`node-pty server listening on ws://localhost:${PORT}`);
});
