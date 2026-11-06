const fs=require('node:fs'),assert=require('node:assert/strict');
const root='E:/codex-build/eco-material-validation-20261106/evidence';
const load=name=>JSON.parse(fs.readFileSync(`${root}/${name}`,'utf8'));
function marker(tag,label){const text=fs.readFileSync(`${root}/${tag}-server.log`,'utf8');let found=[...text.matchAll(new RegExp('\\[PREVIEW\\] '+label+' (\\{[^\\n]+\\})','g'))].at(-1);assert(found,label);return JSON.parse(found[1]);}
function native(name,want){let r=load(name);assert(r.passed);assert.equal(r.rows.length,want);for(const g of r.groups){assert.equal(g.unique,1);let rows=r.rows.filter(x=>x.seed===g.seed&&x.family===g.family);let signatures=new Set(rows.map(x=>JSON.stringify(x.offers)));assert.equal(signatures.size,1,'Hints and displayed costs diverged');for(const row of rows){assert.equal(row.level,60);assert.equal(row.lapis,64);}}return r;}
let old=marker('baseline','FAMILIES'),out={artifact:load('artifact-verification.json'),baselineFamilies:old.groups.map(g=>({family:g.family,poolVariants:g.poolVariants,previewVariants:g.previewVariants})),runs:[]};
for(const tag of ['final-a','final-b']){
 let m=native(`${tag}-native-material-results.json`,84);assert.equal(m.paid.length,3);for(const [i,p]of m.paid.entries()){assert.equal(p.button,i);assert.equal(p.levels,i+1);assert.equal(p.lapis,i+1);}
 let f=marker(tag,'FAMILIES');for(const g of f.groups){let base=old.groups.find(x=>x.family===g.family);assert.equal(g.poolVariants,1);assert.equal(g.previewVariants,1);for(const item of g.items){assert.deepEqual(item.pool,base.items.find(x=>x.material===item.material).pool,'Eligibility changed');}}
 let r=load(`${tag}-full-native.json`);assert.equal(r.checks.length,18);assert(!r.rows.some(x=>x.type==='FAIL'));out.runs.push({tag,groups:m.groups,paid:m.paid,nativeRegressionChecks:r.checks,familyScan:f.groups.map(g=>({family:g.family,poolVariants:g.poolVariants,previewVariants:g.previewVariants}))});
}
out.otherClasses=native('final-a-other-material-results.json',112).groups;
out.turtleHelmet=native('final-a-turtle-material-results.json',16).groups;
out.metadata=marker('final-a','MATRIX');assert.equal(out.metadata.checked,960);assert.equal(out.metadata.different,0);assert.equal(out.metadata.mutated,0);
out.boundaries=marker('final-a','BOUNDARIES');assert(out.boundaries.customStable&&out.boundaries.crossWorldStable);
out.reconnects=[];for(const tag of ['final-a','final-b','final-c']){let r=load(`${tag}-resume-native.json`);assert.equal(r.checks.length,1);assert(!r.rows.some(x=>x.type==='FAIL'));out.reconnects.push({tag,kind:tag==='final-a'?'same-JVM reconnect':'new-JVM restart',passed:true});}
out.nativeMaterialPreviews=296;out.unitTests=out.artifact.tests.reduce((n,t)=>n+Number(t.tests),0);assert.equal(out.unitTests,15);assert(out.artifact.tests.every(t=>Number(t.failures)===0&&Number(t.errors)===0&&Number(t.skipped)===0));
fs.writeFileSync(`${root}/final-results.json`,JSON.stringify(out,null,2));
console.log(JSON.stringify({passed:true,unitTests:out.unitTests,materialPreviews:296,paidTierChecks:6,nativeRegressionChecks:36,metadataComparisons:960,reconnects:1,restarts:2}));
