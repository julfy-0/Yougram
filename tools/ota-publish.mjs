#!/usr/bin/env node
/**
 * Публикация сборки Yougram в OTA (Cloudflare R2). Работает на Windows / macOS / Linux, нужен только Node 18+.
 *
 *   node tools/ota-publish.mjs <app.apk> [--notes файл|"текст"] [--banner banner.png]
 *                              [--rollout 1..100] [--manifest-only] [--bucket yougram-ota] [--dry-run]
 *
 * Версию, versionCode и пакет берёт из самого APK (aapt2 из Android SDK), канал — из суффикса версии:
 * «1.4.4-b2» → beta №2, «1.4.4-r» или «1.4.4» → release.
 * Порядок записи: APK → баннер → manifest (manifest последним, чтобы клиенты не видели недозагруженную сборку).
 *
 * Авторизация — стандартная для wrangler: `npx wrangler login` локально или
 * CLOUDFLARE_API_TOKEN + CLOUDFLARE_ACCOUNT_ID в CI.
 *
 * --manifest-only: не заливать APK заново (например, поменять rollout или заметки). Чтобы у сборки остался
 * баннер, укажите --banner и при этом режиме: флаг banner в manifest ставится только вместе с загрузкой файла.
 */
import { createHash } from "node:crypto";
import { createReadStream, existsSync, readFileSync, readdirSync, statSync, writeFileSync, mkdtempSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { tmpdir } from "node:os";
import { extname, join, resolve } from "node:path";

const PACKAGE = "app.yougram";
const win = process.platform === "win32";

function parseArgs(argv) {
  const o = { bucket: "yougram-ota", rollout: 100, manifestOnly: false, dryRun: false };
  const rest = [];
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const next = () => argv[++i] ?? die(`после ${a} нужно значение`);
    if (a === "--notes") o.notes = next();
    else if (a === "--banner") o.banner = next();
    else if (a === "--rollout") o.rollout = parseInt(next(), 10);
    else if (a === "--bucket") o.bucket = next();
    else if (a === "--manifest-only") o.manifestOnly = true;
    else if (a === "--dry-run") o.dryRun = true;
    else if (a.startsWith("--")) die(`неизвестный флаг ${a}`);
    else rest.push(a);
  }
  o.apk = rest[0];
  return o;
}

function die(msg) {
  console.error(`ota-publish: ${msg}`);
  process.exit(1);
}

function findAapt2() {
  const roots = new Set([process.env.ANDROID_HOME, process.env.ANDROID_SDK_ROOT].filter(Boolean));
  try {
    const lp = readFileSync(resolve("local.properties"), "utf8");
    const m = lp.match(/^sdk\.dir=(.+)$/m);
    if (m) roots.add(m[1].trim().replace(/\\:/g, ":").replace(/\\\\/g, "\\"));
  } catch {}
  const exe = win ? "aapt2.exe" : "aapt2";
  for (const root of roots) {
    const bt = join(root, "build-tools");
    if (!existsSync(bt)) continue;
    const versions = readdirSync(bt).sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
    for (const v of versions) {
      const p = join(bt, v, exe);
      if (existsSync(p)) return p;
    }
  }
  return exe; // вдруг лежит в PATH
}

function readBadging(apk) {
  let out;
  try {
    out = execFileSync(findAapt2(), ["dump", "badging", apk], { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
  } catch {
    die("не удалось запустить aapt2 (Android SDK → build-tools). Задайте ANDROID_HOME или sdk.dir в local.properties.");
  }
  const m = out.match(/package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'/);
  if (!m) die("в выводе aapt2 нет информации о пакете — это точно APK?");
  return { pkg: m[1], versionCode: parseInt(m[2], 10), versionName: m[3] };
}

async function sha256(file) {
  const h = createHash("sha256");
  for await (const chunk of createReadStream(file)) h.update(chunk);
  return h.digest("hex");
}

const q = (s) => (win ? `"${s}"` : s); // wrangler на Windows запускается через shell

function put(bucket, key, file, type, dryRun) {
  const args = ["wrangler", "r2", "object", "put", `${bucket}/${key}`, "--file", q(file), "--content-type", type, "--remote"];
  console.log(`→ ${key}`);
  if (dryRun) return;
  execFileSync("npx", args, { stdio: "inherit", shell: win });
}

function notesFrom(arg) {
  if (!arg) return "";
  return (existsSync(arg) ? readFileSync(arg, "utf8") : arg).replace(/\r\n/g, "\n").trim();
}

const o = parseArgs(process.argv.slice(2));
if (!o.apk) die("укажите путь к APK:  node tools/ota-publish.mjs app-release.apk --notes notes.txt");
if (!existsSync(o.apk)) die(`файл не найден: ${o.apk}`);
if (!(o.rollout >= 1 && o.rollout <= 100)) die("--rollout: число от 1 до 100");

const { pkg, versionCode, versionName: fullName } = readBadging(o.apk);
if (pkg !== PACKAGE) die(`пакет ${pkg}, ожидается ${PACKAGE}`);

// «1.4.4-b2» → beta 2, «1.4.4-r» / «1.4.4» → release
const bm = fullName.match(/^(.*?)-b(\d+)$/);
const beta = bm ? parseInt(bm[2], 10) : 0;
const base = bm ? bm[1] : fullName.replace(/-r$/, "");
const channel = beta > 0 ? "beta" : "release";

const size = statSync(o.apk).size;
const hash = await sha256(o.apk);

let bannerFile = null;
let bannerType = "image/png";
if (o.banner) {
  if (!existsSync(o.banner)) die(`баннер не найден: ${o.banner}`);
  const ext = extname(o.banner).toLowerCase();
  bannerType = ext === ".webp" ? "image/webp" : ext === ".jpg" || ext === ".jpeg" ? "image/jpeg" : "image/png";
  bannerFile = o.banner;
}

const manifest = {
  versionName: base,
  versionCode,
  beta,
  notes: notesFrom(o.notes),
  size,
  sha256: hash,
  banner: Boolean(bannerFile),
  rollout: o.rollout,
  publishedAt: new Date().toISOString(),
};

console.log(`Канал: ${channel} · ${fullName} (код ${versionCode}) · ${(size / 1048576).toFixed(1)} МБ · rollout ${o.rollout}%`);
console.log(`sha256 ${hash}`);

const prefix = `${channel}/${versionCode}`;
if (!o.manifestOnly) {
  put(o.bucket, `apk/${prefix}/yougram.apk`, resolve(o.apk), "application/vnd.android.package-archive", o.dryRun);
}
// Баннер маленький — заливаем всегда, когда он указан (в том числе при --manifest-only).
if (bannerFile) put(o.bucket, `banner/${prefix}/banner`, resolve(bannerFile), bannerType, o.dryRun);

const tmp = join(mkdtempSync(join(tmpdir(), "ota-")), "manifest.json");
writeFileSync(tmp, JSON.stringify(manifest, null, 2));
put(o.bucket, `manifest/${channel}.json`, tmp, "application/json", o.dryRun);
console.log(o.dryRun ? "Готово (dry-run, ничего не записано)." : `Готово: ${fullName} опубликована в канале ${channel}.`);
