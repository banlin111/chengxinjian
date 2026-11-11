const fs = require('node:fs');
const path = require('node:path');
const postcss = require('postcss');
const tailwind = require('tailwindcss');
const selectorParser = require('postcss-selector-parser');
const root = path.resolve(__dirname, '..');
const classes = require('../src/styles/prototype-classes.json');

// Keep the source prototype's utilities, but emit WXSS-safe class identifiers.
const config = {
  content: [{ raw: Object.keys(classes).join(' '), extension: 'html' }],
  corePlugins: { preflight: false },
  theme: { extend: { opacity: { 6: '.06', 8: '.08', 12: '.12', 15: '.15', 85: '.85' }, colors: {
    night: '#0F1D1F', deep: '#16302F', ocean: '#1E4744', pine: '#2C8C82',
    jade: '#3FB3A3', sage: '#A8C5BD', mist: '#F2EEE4', gold: '#E9C08A',
  } } },
};
postcss([tailwind(config)]).process('@tailwind base;\n@tailwind utilities;', { from: undefined }).then(result => {
  result.root.walkRules(rule => {
    if (!rule.selector.includes('.')) {
      rule.selector = '.pdf-screen, .pdf-screen view, .pdf-screen text, .pdf-screen button, .pdf-screen image, .pdf-screen input';
      return;
    }
    rule.selector = selectorParser(selectors => {
      selectors.walkClasses(node => { if (classes[node.value]) node.value = classes[node.value]; });
    }).processSync(rule.selector);
    rule.selector = rule.selector.split(',').map(selector => `.pdf-screen ${selector.trim()}`).join(',');
    // Emit simple native selectors and spacing values for forward-only view lists.
    rule.selector = rule.selector.replace(/:not\(\[hidden\]\)/g, 'view:not([hidden])');
    const spacing = { 'u-space-y-2': '8px', 'u-space-y-2_5': '10px', 'u-space-y-3': '12px' };
    const matched = Object.keys(spacing).find(name => rule.selector.includes(`.${name} >`));
    if (matched) {
      rule.removeAll(); rule.append({ prop: 'margin-top', value: spacing[matched] });
    }
    if (rule.selector.includes('.u-divide-x >')) {
      rule.removeAll(); rule.append({ prop: 'border-left-width', value: '1px' });
    }
    if (rule.selector.includes('.u-divide-y >')) {
      rule.removeAll(); rule.append({ prop: 'border-top-width', value: '1px' });
    }
    rule.selector = rule.selector.replace(/> view:not\(\[hidden\]\) ~ view:not\(\[hidden\]\)/g, '> view:nth-child(n+2)');
  });
  fs.writeFileSync(path.join(root, 'src/styles/prototype-utilities.css'), result.root.toString());
});
