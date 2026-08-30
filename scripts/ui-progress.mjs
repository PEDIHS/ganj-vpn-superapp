#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const ledgerPath = process.argv[2] ?? 'docs/ui/UI_100_PERCENT_PROGRESS.fa.md';
const absolute = path.resolve(process.cwd(), ledgerPath);
const source = fs.readFileSync(absolute, 'utf8');
const lines = source.split(/\r?\n/);

let section = null;
let total = 0;
let done = 0;
const bySection = new Map();

for (const line of lines) {
  const heading = line.match(/^#\s+(\d+)\)/);
  if (heading) section = Number(heading[1]);
  if (section === null || section < 1 || section > 26) continue;
  const checkbox = line.match(/^- \[([ xX])\]\s+/);
  if (!checkbox) continue;
  const completed = checkbox[1].toLowerCase() === 'x';
  total += 1;
  if (completed) done += 1;
  const current = bySection.get(section) ?? { total: 0, done: 0 };
  current.total += 1;
  if (completed) current.done += 1;
  bySection.set(section, current);
}

if (total === 0) {
  console.error('No executable UI checklist items found in sections 1–26.');
  process.exit(2);
}

const percent = Number(((done / total) * 100).toFixed(1));
const remaining = total - done;
console.log(JSON.stringify({ done, total, remaining, percent }, null, 2));

if (process.argv.includes('--sections')) {
  for (const [id, value] of [...bySection.entries()].sort((a, b) => a[0] - b[0])) {
    const sectionPercent = Number(((value.done / value.total) * 100).toFixed(1));
    console.log(`Section ${id}: ${value.done}/${value.total} (${sectionPercent}%)`);
  }
}
