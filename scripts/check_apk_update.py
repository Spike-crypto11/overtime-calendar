#!/usr/bin/env python3
"""Read-only compatibility check. Never installs, uninstalls or clears application data."""
import argparse,json,re,subprocess,sys
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('old');p.add_argument('new');p.add_argument('--build-tools',required=True);a=p.parse_args()
b=Path(a.build_tools)
def metadata(path):
    text=subprocess.check_output([str(b/'aapt'),'dump','badging',path],text=True)
    m=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'",text)
    if not m: raise RuntimeError('Cannot read package metadata')
    signed=subprocess.check_output([str(b/'apksigner'),'verify','--print-certs',path],text=True)
    certs=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)',signed)
    if not certs: raise RuntimeError('APK has no verified signing certificate')
    return {'package':m[1],'versionCode':int(m[2]),'versionName':m[3],'signers':sorted(certs)}
old,new=metadata(a.old),metadata(a.new)
checks={'samePackage':old['package']==new['package'],'sameSigners':old['signers']==new['signers'],
        'higherVersionCode':new['versionCode']>old['versionCode']}
print(json.dumps({'old':old,'new':new,'checks':checks,'metadataCompatible':all(checks.values()),
    'deviceInstallTested':False},ensure_ascii=False,indent=2))
sys.exit(0 if all(checks.values()) else 1)
