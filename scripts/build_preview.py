#!/usr/bin/env python3
"""Build the supported preview upgrade, retaining all recovered UI/resources and the original key.
Requires: compiled Kotlin classes (gradle testDebugUnitTest assembleDebug lintDebug), Apktool 2.12.1,
Android build-tools 34.0.0, and the user's existing WeekendPlanner recovery directory.
"""
import argparse,subprocess,tempfile,zipfile,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--recovery',required=True);p.add_argument('--baseline',required=True)
p.add_argument('--sdk',required=True);p.add_argument('--apktool',required=True);p.add_argument('--output',required=True)
a=p.parse_args();repo=Path(__file__).resolve().parents[1];sdk=Path(a.sdk).resolve();bt=sdk/'build-tools/34.0.0'
recovery=Path(a.recovery).resolve();baseline=Path(a.baseline).resolve();output=Path(a.output).resolve();output.parent.mkdir(parents=True,exist_ok=True)
classes=repo/'app/build/tmp/kotlin-classes/debug';files=list(classes.rglob('Payroll*.class'));assert len(files)>10,'Compile the source first'
def run(*args):subprocess.run([str(v) for v in args],check=True)
with tempfile.TemporaryDirectory(prefix='payroll-build-',dir=output.parent) as temp:
 t=Path(temp);project=t/'preview'
 run('python3',repo/'scripts/patch_preview.py',recovery,baseline,project)
 run('java','-jar',Path(a.apktool).resolve(),'b',project,'-o',t/'reassembled.apk')
 with zipfile.ZipFile(t/'addon.jar','w') as z:
  for f in files:z.write(f,f.relative_to(classes))
 (t/'dex').mkdir()
 run(bt/'d8','--release','--min-api','24','--lib',sdk/'platforms/android-34/android.jar','--output',t/'dex',t/'addon.jar')
 shutil.copyfile(t/'reassembled.apk',t/'with-addon.apk')
 with zipfile.ZipFile(t/'with-addon.apk','a') as z:
  assert 'classes6.dex' not in z.namelist();z.write(t/'dex/classes.dex','classes6.dex')
 run(bt/'zipalign','-p','-f','4',t/'with-addon.apk',t/'aligned.apk')
 # This is the documented, non-production preview development key only.
 run(bt/'apksigner','sign','--ks',recovery/'signing/weekend-preview.jks','--ks-key-alias','weekendpreview',
     '--ks-pass','pass:android','--key-pass','pass:android','--out',output,t/'aligned.apk')
 run('python3',repo/'scripts/check_apk_update.py',baseline,output,'--build-tools',bt)
 run(bt/'zipalign','-c','-p','4',output)
print(output)
