// typescript-json-schema 0.65.1 drops referenced members while flattening intersections
// with noExtraProps + refs. Repair referenced trees from an independent inline pass,
// retain reusable named models, and check field sets against the TypeScript compiler.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const tjs = require('typescript-json-schema');
const { createRequire } = require('node:module');
const ts = createRequire(require.resolve('typescript-json-schema'))('typescript');
const root = path.resolve(__dirname, '..');
const program = tjs.programFromConfig(path.join(root, 'tsconfig.json'));
const options = { required: true, noExtraProps: true };
const named = tjs.generateSchema(program, 'Api', { ...options, ref: true });
const inline = tjs.generateSchema(program, 'Api', { ...options, ref: false });
if (!named || !inline) throw new Error('API schema generation failed');
const schema = { ...named, definitions: { ...named.definitions, ...inline.definitions } };
const activeRefs = new Set();
function repair(node, expanded) {
  if (!node) return expanded;
  if (node.$ref) {
    const name = node.$ref.split('/').at(-1);
    if (!activeRefs.has(name)) {
      activeRefs.add(name);
      schema.definitions[name] = repair(schema.definitions[name], expanded);
      activeRefs.delete(name);
    }
    return node;
  }
  const fixed = { ...node };
  if (expanded.properties) {
    fixed.properties = Object.fromEntries(Object.entries(expanded.properties).map(([name, value]) =>
      [name, repair(node.properties?.[name], value)]));
    if (expanded.required) fixed.required = expanded.required;
    else delete fixed.required;
  }
  if (expanded.items) fixed.items = repair(node.items, expanded.items);
  if (expanded.anyOf) fixed.anyOf = expanded.anyOf.map((value, i) => repair(node.anyOf?.[i], value));
  if (expanded.allOf) fixed.allOf = expanded.allOf.map((value, i) => repair(node.allOf?.[i], value));
  return fixed;
}
schema.properties = repair(schema, inline).properties;
const checker = program.getTypeChecker();
const source = program.getSourceFile(path.join(root, 'contracts/api.ts'));
const apiSymbol = checker.getExportsOfModule(checker.getSymbolAtLocation(source)).find(s => s.name === 'Api');
const api = checker.getDeclaredTypeOfSymbol(apiSymbol);
const typeOf = symbol => checker.getTypeOfSymbolAtLocation(symbol, symbol.valueDeclaration || symbol.declarations?.[0] || source);
function resolve(s) { return s?.$ref ? resolve(schema.definitions[s.$ref.split('/').at(-1)]) : s; }
// The generator also closes mapped string dictionaries even without noExtraProps.
// Reconstruct their value schema from the compiler's index signature, preserving
// closed ordinary DTOs. Fail explicitly when an unsupported value type is added.
function repairDictionaries(type, node, depth = 0) {
  const s = resolve(node);
  if (!s || depth > 20) return;
  if (checker.isArrayType(type)) { repairDictionaries(checker.getTypeArguments(type)[0], s.items, depth + 1); return; }
  if (!(type.flags & ts.TypeFlags.Object) && !(type.flags & ts.TypeFlags.Intersection)) return;
  const indexed = checker.getIndexTypeOfType(type, ts.IndexKind.String);
  if (indexed) {
    assert.ok(indexed.flags & ts.TypeFlags.String, 'Review unsupported dictionary value type');
    s.additionalProperties = { type: 'string' };
  }
  for (const field of checker.getPropertiesOfType(type)) repairDictionaries(typeOf(field), s.properties?.[field.name], depth + 1);
}
for (const op of checker.getPropertiesOfType(api)) repairDictionaries(typeOf(op), schema.properties[op.name]);
let checked = 0;
function check(type, s, label, depth = 0) {
  s = resolve(s);
  assert.ok(s, label);
  if (depth > 20) throw new Error('Unexpected recursive API shape: ' + label);
  if (type.flags & ts.TypeFlags.String) { assert.equal(s.type, 'string', label + ': string value'); return; }
  if (checker.isArrayType(type)) { check(checker.getTypeArguments(type)[0], s.items, label + '[]', depth + 1); return; }
  if (!(type.flags & ts.TypeFlags.Object) && !(type.flags & ts.TypeFlags.Intersection)) return;
  const fields = checker.getPropertiesOfType(type);
  const indexed = checker.getIndexTypeOfType(type, ts.IndexKind.String);
  if (indexed) {
    assert.equal(typeof s.additionalProperties, 'object', label + ': dictionary value schema');
    check(indexed, s.additionalProperties, label + '[key]', depth + 1);
  }
  assert.deepEqual(Object.keys(s.properties || {}).sort(), fields.map(f => f.name).sort(), label + ': fields');
  assert.deepEqual([...(s.required || [])].sort(), fields.filter(f => !(f.flags & ts.SymbolFlags.Optional)).map(f => f.name).sort(), label + ': required');
  for (const f of fields) check(typeOf(f), s.properties[f.name], label + '.' + f.name, depth + 1);
  checked++;
}
for (const op of checker.getPropertiesOfType(api)) check(typeOf(op), schema.properties[op.name], op.name);
assert.equal(Object.keys(schema.properties).length, 224, 'Operation count changed: review contract scope');
fs.mkdirSync(path.join(root, 'research'), { recursive: true });
fs.writeFileSync(path.join(root, 'research/api-schema.json'), JSON.stringify(schema, null, 2) + '\n');
console.log(`Checked ${checked} object shapes across all 224 operations against TypeScript`);
