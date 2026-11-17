"""Build a private paste-ready backend, retaining the owner's original KASI key."""
import argparse
from pathlib import Path
import re

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--original', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
output = args.output.resolve()
if output.is_relative_to(root):
    parser.error('Private output must be outside the public repository')
original = args.original.read_text(encoding='utf-8-sig')
key = re.search(r"var\s+KASI_KEY\s*=\s*(['\"])(.*?)\1\s*;", original)
if not key or not key.group(2):
    parser.error('Expected one nonempty original KASI_KEY assignment')
code = (root / 'server/Code.gs').read_text()
lookup = "var KASI_KEY = PropertiesService.getScriptProperties().getProperty('KASI_KEY') || '';"
if code.count(lookup) != 1:
    parser.error('Unexpected public key configuration')
code = code.replace(lookup, key.group(0))
code += '\n// ===== 월별 급여 집계 모듈 (이 파일에 포함됨) =====\n'
code += (root / 'server/Payroll.gs').read_text()
names = re.findall(r'^function\s+(\w+)\s*\(', code, re.M)
if len(names) != len(set(names)):
    parser.error('Duplicate function definitions')
missing = set(re.findall(r'^function\s+(\w+)\s*\(', original, re.M)) - set(names)
if missing:
    parser.error('Original functions missing: ' + ', '.join(sorted(missing)))
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(code, encoding='utf-8')
output.chmod(0o600)
print('Private integrated backend created; all original functions retained.')
