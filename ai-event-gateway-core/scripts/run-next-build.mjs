#!/usr/bin/env node
import { rm } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import process from 'node:process';

const require = createRequire(import.meta.url);

let nextCli;
try {
  nextCli = require.resolve('next/dist/bin/next');
} catch {
  console.error('Cannot find Next.js CLI in node_modules.');
  console.error('Run `npm ci` from the project root, then run `npm run build` again.');
  process.exit(1);
}

const configuredTimeout = Number(process.env.NEXT_BUILD_TIMEOUT_MS ?? 600000);
const hasTimeout = Number.isFinite(configuredTimeout) && configuredTimeout > 0;

await rm('.next', { recursive: true, force: true });

const child = spawn(process.execPath, [nextCli, 'build'], {
  stdio: 'inherit',
  env: {
    ...process.env,
    NEXT_TELEMETRY_DISABLED: process.env.NEXT_TELEMETRY_DISABLED ?? '1'
  }
});

let timer;
if (hasTimeout) {
  timer = setTimeout(() => {
    console.error(`next build exceeded ${configuredTimeout}ms; terminating build process.`);
    console.error('Set NEXT_BUILD_TIMEOUT_MS=0 to disable this guard on slower machines/CI agents.');
    child.kill('SIGTERM');
  }, configuredTimeout);
  timer.unref?.();
}

child.on('error', (error) => {
  if (timer) clearTimeout(timer);
  console.error(error);
  process.exit(1);
});

child.on('exit', (code, signal) => {
  if (timer) clearTimeout(timer);
  if (signal) {
    console.error(`next build terminated by signal ${signal}.`);
    process.exit(1);
  }
  process.exit(code ?? 1);
});
