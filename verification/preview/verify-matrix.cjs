const fs=require('node:fs'),assert=require('node:assert/strict');
const name=process.argv[2];
const text=fs.readFileSync(`E:/codex-build/eco-preview-validation-20261106/evidence/${name}-server.log`,'utf8');
const rows=[...text.matchAll(/\[PREVIEW\] MATRIX (\{[^\n]+\})/g)].map(x=>JSON.parse(x[1]));
assert(rows.length>0,'No completed matrix');
for(const r of rows){assert.equal(r.checked,960);assert.equal(r.mutated,0,'Preview modified original item');assert.equal(r.different,0,'Metadata variants reroll previews');for(const g of r.groups)assert.equal(g.unique,1);}
console.log(JSON.stringify({passed:true,runs:rows.length,comparisons:rows.reduce((n,r)=>n+r.checked,0)}));
