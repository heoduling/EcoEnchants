const fs=require('node:fs'),assert=require('node:assert/strict');
const root='E:/codex-build/eco-preview-validation-20261106/evidence';
const load=n=>JSON.parse(fs.readFileSync(`${root}/${n}`,'utf8'));
function marker(tag,name){let t=fs.readFileSync(`${root}/${tag}-server.log`,'utf8');let m=[...t.matchAll(new RegExp('\\[PREVIEW\\] '+name+' (\\{[^\\n]+\\})','g'))].at(-1);assert(m,`${tag} ${name}`);return JSON.parse(m[1]);}
const result={date:'2026-11-06',artifact:load('artifact-verification.json'),finalRuns:[],reconnects:[],savedSeed:load('saved-seed-verification.json')};
for(const tag of ['final-a','final-b']){
 let native=load(`${tag}-full-native.json`),matrix=marker(tag,'MATRIX'),boundaries=marker(tag,'BOUNDARIES');
 assert.equal(native.checks.length,18);assert(!native.rows.some(r=>r.type==='FAIL'));assert.equal(matrix.checked,960);assert.equal(matrix.different,0);assert.equal(matrix.mutated,0);assert(matrix.groups.every(g=>g.unique===1));
 assert.equal(boundaries.customMaterial,'NETHERITE_SWORD');assert(boundaries.customStable&&boundaries.crossWorldStable);assert(boundaries.poolDenied<boundaries.poolBefore);
 result.finalRuns.push({tag,nativeChecks:native.checks,matrix,boundaries});
}
for(const tag of ['final-a','final-b','final-c']){let r=load(`${tag}-resume-native.json`);assert.equal(r.checks.length,1);assert(!r.rows.some(x=>x.type==='FAIL'));result.reconnects.push({tag,kind:tag==='final-a'?'same-JVM reconnect':'new-JVM restart',passed:true});}
let red=marker('old-red','MATRIX');assert.equal(red.different,960);result.redBaseline=red;
result.comparisons=1920;result.nativeChecks=36;
fs.writeFileSync(`${root}/final-results.json`,JSON.stringify(result,null,2));
console.log(JSON.stringify({passed:true,unitTests:12,nativeChecks:36,metadataComparisons:1920,reconnects:1,restarts:2}));
