// Check the generated native component graph before opening or sharing a build.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(process.env.UNI_OUTPUT_DIR || 'dist/build/mp-weixin');
const files = new Set();
function scan(dir) {
  for (const entry of fs.readdirSync(path.join(root, dir), { withFileTypes: true })) {
    const file = path.posix.join(dir, entry.name);
    if (entry.isDirectory()) scan(file);
    else files.add(file);
  }
}
scan('');
function read(file) {
  assert(files.has(file), `Missing native config: ${file}`);
  return JSON.parse(fs.readFileSync(path.join(root, file), 'utf8'));
}
const components = new Set();
function inspect(from, config) {
  for (const [tag, ref] of Object.entries(config.usingComponents || {})) {
    if (ref.startsWith('plugin://')) continue;
    const target = ref.startsWith('/') ? ref.slice(1) : path.posix.join(path.posix.dirname(from), ref);
    if (components.has(target)) continue;
    for (const ext of ['.js', '.json', '.wxml']) {
      assert(files.has(target + ext), `${from} <${tag}> refers to missing ${target + ext} (paths are case sensitive)`);
    }
    components.add(target);
    const component = read(target + '.json');
    assert(component.component === true, `${target}.json must declare component: true`);
    inspect(target + '.json', component);
  }
}
const app = read('app.json');
inspect('app.json', app);
const pages = [
  ...app.pages,
  ...(app.subPackages || app.subpackages || []).flatMap(pkg => pkg.pages.map(page => path.posix.join(pkg.root, page))),
];
for (const page of pages) inspect(page + '.json', read(page + '.json'));
console.log(`Native component paths verified: ${pages.length} pages, ${components.size} components`);
