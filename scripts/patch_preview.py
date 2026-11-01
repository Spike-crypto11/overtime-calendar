#!/usr/bin/env python3
"""Only the supplied 1.13 preview recovery is supported. Never publishes signing material."""
import argparse,hashlib,re,shutil,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('recovery');p.add_argument('baseline_apk');p.add_argument('output');a=p.parse_args()
assert hashlib.sha256(Path(a.baseline_apk).read_bytes()).hexdigest()=='8e76c240c5d824a9f316a29514be457cc4011197e32685c738b11f49fbb7cfa4','Unsupported baseline APK'
source=Path(a.recovery)/'preview-project';out=Path(a.output)
assert not out.exists(),'Use a fresh output directory'
shutil.copytree(source,out)
root=out/'smali_classes3/com/dohyun/overtimecalendar'

def hook(file, signature, code):
    path=root/file;text=path.read_text()
    start=text.index(signature+'\n');end=text.index('.end method',start)
    method=text[start:end];assert method.count('    return-void')==1,signature
    method=method.replace('    return-void',code+'\n    return-void')
    path.write_text(text[:start]+method+text[end:])
hook('Prefs.smali','.method public final setRecords(Landroid/content/Context;Ljava/lang/String;Ljava/util/List;)V',
    '    invoke-static {p1, p2}, Lcom/dohyun/overtimecalendar/PayrollBridge;->recordChanged(Landroid/content/Context;Ljava/lang/String;)V')
hook('Prefs.smali','.method public final removePending(Landroid/content/Context;Ljava/lang/String;)V',
    '    invoke-static {p1, p2}, Lcom/dohyun/overtimecalendar/PayrollBridge;->recordsUploaded(Landroid/content/Context;Ljava/lang/String;)V')
hook('Prefs.smali','.method public final replaceMonth(Landroid/content/Context;Ljava/lang/String;Ljava/util/Map;)V',
    '    invoke-static {p1, p2}, Lcom/dohyun/overtimecalendar/PayrollBridge;->monthLoaded(Landroid/content/Context;Ljava/lang/String;)V')
hook('SettingsActivity.smali','.method protected onCreate(Landroid/os/Bundle;)V',
    '    invoke-static {p0}, Lcom/dohyun/overtimecalendar/PayrollBridge;->attachSettings(Landroid/app/Activity;)V')
hook('MainActivity.smali','.method protected onResume()V',
    '    invoke-static {p0}, Lcom/dohyun/overtimecalendar/PayrollBridge;->resume(Landroid/content/Context;)V')
# Preserve original save handler, but do not acknowledge an older snapshot after a concurrent edit.
path=root/'SyncManager.smali';text=path.read_text()
old='    invoke-virtual {v1, p3, p1}, Lcom/dohyun/overtimecalendar/Prefs;->removePending(Landroid/content/Context;Ljava/lang/String;)V'
assert text.count(old)==1
new='    invoke-static {p3, p1, p2}, Lcom/dohyun/overtimecalendar/PayrollBridge;->acknowledgeRecords(Landroid/content/Context;Ljava/lang/String;Ljava/util/List;)V'
path.write_text(text.replace(old,new))
# All read/modify/write operations share the Prefs instance monitor with the bridge acknowledgement.
path=root/'Prefs.smali';text=path.read_text()
for name,signature,params,result in [
    ('getAllRecords','(Landroid/content/Context;)Ljava/util/Map;','p0, p1','object'),
    ('setRecords','(Landroid/content/Context;Ljava/lang/String;Ljava/util/List;)V','p0, p1, p2, p3','void'),
    ('replaceMonth','(Landroid/content/Context;Ljava/lang/String;Ljava/util/Map;)V','p0, p1, p2, p3','void'),
    ('getPending','(Landroid/content/Context;)Ljava/util/Set;','p0, p1','object'),
    ('addPending','(Landroid/content/Context;Ljava/lang/String;)V','p0, p1, p2','void'),
    ('removePending','(Landroid/content/Context;Ljava/lang/String;)V','p0, p1, p2','void'),
    ('deleteCategory','(Landroid/content/Context;Ljava/lang/String;)V','p0, p1, p2','void')]:
    original='.method public final '+name+signature
    assert text.count(original)==1,name
    text=text.replace(original,'.method private final payrollOriginal_'+name+signature)
    resultCode='    move-result-object v0\n' if result=='object' else ''
    returnCode='    return-object v0' if result=='object' else '    return-void'
    text+='\n'+original+'''\n    .locals 1
    monitor-enter p0
    :payroll_try_start
    invoke-direct {'''+params+'''}, Lcom/dohyun/overtimecalendar/Prefs;->payrollOriginal_'''+name+signature+'\n'+resultCode+'''    :payroll_try_end
    monitor-exit p0
'''+returnCode+'''
    .catchall {:payroll_try_start .. :payroll_try_end} :payroll_catch
    :payroll_catch
    move-exception v0
    monitor-exit p0
    throw v0
.end method
'''
path.write_text(text)
manifest=out/'AndroidManifest.xml';text=manifest.read_text()
assert 'package="com.dohyun.overtimecalendar.preview"' in text
assert 'PayrollActivity' not in text
text=text.replace('</application>','<activity android:name="com.dohyun.overtimecalendar.PayrollActivity" android:exported="false"/></application>')
manifest.write_text(text)
yaml=out/'apktool.yml';text=yaml.read_text();assert 'versionCode: 14' in text
text=text.replace('versionCode: 14','versionCode: 16').replace('versionName: 1.13-weekend-legibility','versionName: 1.15-payroll-categories')
yaml.write_text(text)
print(out)
