"""Compute medians from paired old/new batches in two independent server JVMs."""
from pathlib import Path
import json
import statistics
import sys

repo = Path(__file__).resolve().parents[2]
evidence = repo.parent / 'audit/optimization-20261110/evidence'
tags = sys.argv[1:] or ['final-d', 'final-e']
assert len(tags) == 2
runs = [json.loads((evidence / f'{tag}-results.json').read_text(encoding='utf-8')) for tag in tags]
assert all(not run['observations'].get('failure') for run in runs)
assert runs[0]['observations']['displayEnchants'] == runs[1]['observations']['displayEnchants']
rows = []
for scenario in sorted({batch['scenario'] for run in runs for batch in run['batches']}):
    row = {'scenario': scenario}
    for version in ('old', 'new'):
        values = [batch['nsPerCall'] for run in runs for batch in run['batches']
                  if batch['scenario'] == scenario and batch['version'] == version]
        assert len(values) == 24
        row[version + 'NsPerCall'] = statistics.median(values)
        row[version + 'RunMedians'] = [statistics.median(batch['nsPerCall'] for batch in run['batches']
            if batch['scenario'] == scenario and batch['version'] == version) for run in runs]
    row['observedTimeReductionPct'] = 100 * (1 - row['newNsPerCall'] / row['oldNsPerCall'])
    rows.append(row)

allocation = {}
for metric in ('bytesPerCall', 'nsPerCall'):
    for version in ('old-equivalent', 'new-helper'):
        values = [batch[metric] for run in runs for batch in run['observations']['detachedSortingAllocation']['batches'] if batch['version'] == version]
        assert len(values) == 24
        allocation[version + '-' + metric] = statistics.median(values)
allocation['allocationReductionPct'] = 100 * (1 - allocation['new-helper-bytesPerCall'] / allocation['old-equivalent-bytesPerCall'])
allocation['scope'] = '220-candidate sorting substep on detached immutable registry keys, platform ThreadMXBean. Old equivalent comparator versus actual new helper.'
report = {'tags': tags, 'displayEnchants': runs[0]['observations']['displayEnchants'],
          'checks': sum(len(run['checks']) for run in runs),
          'displayComparisons': sum(run['observations']['displayComparisons'] for run in runs),
          'previewComparisons': sum(run['observations']['previewComparisons'] for run in runs),
          'measuredOwnerCallsBothVersions': sum(batch['calls'] for run in runs for batch in run['batches']),
          'wallTime': rows, 'sortingAllocation': allocation,
          'scope': 'Same-owner paired batches, 8 warmups plus 12 measured rounds per JVM. Includes cloning/properties for display and event creation/signature for prepare. No whole-server CPU, retained heap or MSPT conclusion. Owner allocation unavailable on virtual thread.'}
(evidence / 'summary.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
