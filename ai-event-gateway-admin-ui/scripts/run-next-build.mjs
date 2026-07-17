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
const buildDir = (process.env.NEXT_DIST_DIR ?? '.next').trim() || '.next';

try {
  await rm(buildDir, { recursive: true, force: true });
} catch (error) {
  if (error?.code === 'EACCES' || error?.code === 'EPERM') {
    console.error(`Cannot remove ${buildDir} before building because it is not writable by the current user.`);
    console.error('This usually means a previous Docker runtime created or mounted the Admin UI build directory with a different UID.');
    console.error('CI/CD should use NEXT_DIST_DIR=.next-ci so host builds never depend on Docker-owned .next.');
    console.error('Run `make clean-ci` or `scripts/ci/admin-ui-clean-generated.sh`, then retry the build.');
  }
  throw error;
}

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
