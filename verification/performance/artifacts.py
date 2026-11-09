"""Bind build, runtime inputs, and changed JAR entries without reading server secrets."""
from pathlib import Path
import hashlib
import json
import subprocess
import zipfile
import xml.etree.ElementTree as ET

repo = Path(__file__).resolve().parents[2]
task = repo.parent / 'audit/optimization-20261110'
baseline = task / 'baseline.jar'
candidate = repo / 'build/libs/EcoEnchants-2026.27-4-all.jar'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

report = {'baselineVersion': '2026.27-3', 'candidateVersion': '2026.27-4',
          'baselineSha256': sha(baseline), 'candidateSha256': sha(candidate),
          'sourceBase': '3d5e286a82ca45cb32113ea259890e99e4d4feda',
          'sourceCommit': subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip()}
assert report['baselineSha256'] == 'fd9d3f46bb9374865eeb11b79b5e18f771fd1752e36c30efdc42ca5c0f73d29f'
with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(candidate) as new:
    assert new.testzip() is None
    changed = [name for name in old.namelist() if name not in new.namelist() or old.read(name) != new.read(name)]
    added = sorted(set(new.namelist()) - set(old.namelist()))
    removed = sorted(set(old.namelist()) - set(new.namelist()))
    report.update(changedEntries=changed, addedEntries=added, removedEntries=removed)
    assert not removed
    assert all(name.endswith('.class') or name in ('plugin.yml', 'META-INF/core-plugin.kotlin_module') for name in changed)
    assert old.read('plugin.yml').replace(b'2026.27-3', b'2026.27-4') == new.read('plugin.yml')
    report['unchangedClasses'] = sum(old.read(name) == new.read(name) for name in old.namelist() if name.endswith('.class'))
    report['bytecodeChangedClasses'] = []
    for name in changed:
        if not name.endswith('.class'):
            continue
        cls = name[:-6].replace('/', '.')
        dumps = [subprocess.check_output(['D:/java/bin/javap.exe', '-p', '-c', '-s', '-constants', '-classpath', str(jar), cls], text=True, encoding='utf-8', errors='replace') for jar in (baseline, candidate)]
        import re
        if re.sub(r'#\d+', '#CP', dumps[0]) != re.sub(r'#\d+', '#CP', dumps[1]):
            report['bytecodeChangedClasses'].append(name)
    report['resourcesUnchangedExceptVersion'] = True

tests = [ET.parse(path).getroot().attrib for path in (repo / 'eco-core/core-plugin/build/test-results/test').glob('TEST-*.xml')]
report['tests'] = [{key: row[key] for key in ('name', 'tests', 'failures', 'errors', 'skipped')} for row in tests]
assert sum(int(row['tests']) for row in tests) == 20
assert all(int(row['failures']) == int(row['errors']) == int(row['skipped']) == 0 for row in tests)
server = task / 'server'
inputs = [server / 'server.jar', *(server / 'versions').rglob('*.jar'), *(server / 'plugins').glob('*.jar')]
probes = {'OptCompare.jar', 'PreviewProbe.jar'}
report['runtimeArtifacts'] = [{'file': str(path.relative_to(server)), 'bytes': path.stat().st_size, 'sha256': sha(path)} for path in inputs if path.name not in probes]
report['instrumentationPackageScope'] = 'Test tools were repackaged from the same sources after the final runs. Their current archive hashes are post-run package fingerprints.'
report['instrumentationPackages'] = [{'file': path.name, 'sha256': sha(path)} for path in inputs if path.name in probes]
assert sha(server / 'plugins/EcoEnchants.jar') == sha(candidate)
assert sha(server / 'server.jar') == '19bac68bdd71810a4417b69acdc4b82f41a3e4d80065075f3d42ae87179f2b85'
assert sha(server / 'versions/26.2/shiroha-26.2.jar') == '40a178a02a2d5810596bab1e75df2c585760e58306e6274cb28328e361655f2a'
(task / 'evidence/artifacts.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({key: report[key] for key in ('baselineSha256', 'candidateSha256', 'unchangedClasses', 'bytecodeChangedClasses', 'addedEntries')}, ensure_ascii=False, indent=2))
