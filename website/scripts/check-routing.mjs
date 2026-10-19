import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
const source=await fs.readFile(new URL('../dist/server/index.js',import.meta.url),'utf8');
const {default:site}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
for(const path of ['/','/privacy.html?from=old','/assets/SP_GREEN.png']) {
 const r=await site.fetch(new Request('https://camerastats.squidproductions.org'+path));assert.equal(r.status,308);assert.equal(r.headers.get('location'),'https://shotreporter.squidproductions.org'+path);
}
for(const path of ['/','/privacy.html']) {const r=await site.fetch(new Request('https://shotreporter.squidproductions.org'+path));assert.equal(r.status,200);assert.match(await r.text(),/Shot Reporter/);}
const image=await site.fetch(new Request('https://shotreporter.squidproductions.org/assets/SP_GREEN.png'));assert.equal(image.status,200);assert.equal(image.headers.get('content-type'),'image/png');
assert.equal((await site.fetch(new Request('https://shotreporter.squidproductions.org/missing'))).status,404);
console.log('PASS: old-host 308 keeps path/query; canonical pages/assets render; missing routes stay 404.');
