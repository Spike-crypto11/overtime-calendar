#!/usr/bin/env python3
"""Update only the payroll DEX and version of the previously verified preview v15 or Daylog v18 APK.

Tools and the new payroll DEX are from the CI preview-build-inputs artifact.
Signing material stays in the owner's private recovery directory.
"""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--installed', required=True, type=Path)
p.add_argument('--bundle', required=True, type=Path)
p.add_argument('--recovery', required=True, type=Path)
p.add_argument('--output', required=True, type=Path)
a = p.parse_args()
old, bundle, recovery, output = [x.resolve() for x in (a.installed, a.bundle, a.recovery, a.output)]
profiles = {
    '110239985147c68c0915d36b193aa017a1b17664a9248ea2246aa2ae9d28ca16': (15, 16, '1.14-payroll-preview', '1.15-payroll-categories'),
    'b725a3d5864b21172139c1b9350ddc8803a8484152cf230f4b03d39125fd0111': (18, 19, '1.17-daylog-record-styles', '1.18-daylog-leave-background'),
}
profile = profiles.get(hashlib.sha256(old.read_bytes()).hexdigest())
assert profile, 'Unsupported preview base'
old_version, new_version, old_name, new_name = profile
bt = bundle / 'build-tools'
for name in ('zipalign', 'apksigner'):
    (bt / name).chmod(0o755)
def run(*args):
    subprocess.run([str(x) for x in args], check=True)
output.parent.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix='preview-update-', dir=output.parent) as temp:
    t = Path(temp)
    project = t / 'decoded'
    run('java', '-jar', bundle / 'apktool.jar', 'd', '-s', '-f', old, '-o', project)
    yaml = project / 'apktool.yml'
    text = yaml.read_text()
    text, count = re.subn(r'versionCode: [\'\"]?' + str(old_version) + r'[\'\"]?', 'versionCode: ' + str(new_version), text)
    assert count == 1
    text = text.replace(old_name, new_name)
    yaml.write_text(text)
    # -s keeps every original DEX byte-for-byte. Replace only the independent payroll module.
    (project / 'classes6.dex').write_bytes((bundle / 'dex/classes.dex').read_bytes())
    run('java', '-jar', bundle / 'apktool.jar', 'b', project, '-o', t / 'unsigned.apk')
    run(bt / 'zipalign', '-p', '-f', '4', t / 'unsigned.apk', t / 'aligned.apk')
    run(bt / 'apksigner', 'sign', '--ks', recovery / 'signing/weekend-preview.jks',
        '--ks-key-alias', 'weekendpreview', '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
        '--out', output, t / 'aligned.apk')
    run(bt / 'apksigner', 'verify', '--print-certs', output)
    run(bt / 'zipalign', '-c', '-p', '4', output)
with zipfile.ZipFile(old) as before, zipfile.ZipFile(output) as after:
    dex = [x for x in before.namelist() if re.fullmatch(r'classes\d*\.dex', x) and x != 'classes6.dex']
    assert len(dex) == 5
    assert all(before.read(x) == after.read(x) for x in dex), 'Original application DEX changed'
print('Original five DEX files preserved; payroll module updated. Device execution not tested.')
print(output)
