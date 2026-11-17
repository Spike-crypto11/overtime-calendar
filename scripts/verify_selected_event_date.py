#!/usr/bin/env python3
"""Verify v20 -> v21 scope, metadata, and selected-date bytecode."""
import json
from pathlib import Path
import sys
import zipfile
from loguru import logger
logger.remove()
from androguard.core.apk import APK
from androguard.core.dex import DEX

def methods(data):
    return {(c.get_name(), m.get_name(), m.get_descriptor()):
            [(i.get_name(), i.get_output()) for i in m.get_instructions()]
            for c in DEX(data).get_classes() for m in c.get_methods()}

def manifest(apk):
    root = apk.get_android_manifest_xml()
    for name in ('versionCode', 'versionName'):
        root.attrib.pop('{http://schemas.android.com/apk/res/android}' + name, None)
    def walk(node):
        return node.tag, sorted(node.attrib.items()), (node.text or '').strip(), [walk(n) for n in node]
    return walk(root)

old, new = map(Path, sys.argv[1:])
before, after = APK(str(old)), APK(str(new))
assert before.get_package() == after.get_package() == 'com.dohyun.overtimecalendar.preview'
assert before.get_androidversion_code() == '20' and after.get_androidversion_code() == '21'
assert after.get_app_name() == before.get_app_name()
assert after.get_androidversion_name() == '1.20-daylog-selected-date'
assert manifest(before) == manifest(after), 'Manifest changed beyond version metadata'
with zipfile.ZipFile(old) as a, zipfile.ZipFile(new) as b:
    dex = [n for n in a.namelist() if n.endswith('.dex')]
    assert sorted(dex) == sorted(n for n in b.namelist() if n.endswith('.dex'))
    for n in dex:
        if n != 'classes3.dex': assert a.read(n) == b.read(n), n
    ma, mb = methods(a.read('classes3.dex')), methods(b.read('classes3.dex'))
    changed = {k for k in ma if ma[k] != mb.get(k)}
    assert {(k[0], k[1]) for k in changed} == {
        ('Lcom/dohyun/overtimecalendar/MainActivity;', 'showInputDialog'),
        ('Lcom/dohyun/overtimecalendar/MainActivity;', 'showInputDialog$lambda$21'),
        ('Lcom/dohyun/overtimecalendar/InputDialogActivity;', 'onCreate$lambda$1'),
        ('Lcom/dohyun/overtimecalendar/EventActivity;', 'showEdit')}
    added = set(mb) - set(ma)
    assert len(added) == 1 and next(iter(added))[1] == 'daylogInitialDate'
    show = next(v for k, v in mb.items() if k[1] == 'showEdit' and k[0].endswith('/EventActivity;'))
    assert sum('daylogInitialDate' in text for _, text in show) == 1
    assert not any('DateUtils;->today' in text for _, text in show)
    # Existing event dates and default end=start remain in the same method.
    assert any('Event;->getStart' in text for _, text in show)
    assert any('Event;->getEnd' in text for _, text in show)
    launch = next(v for k, v in mb.items() if k[1] == 'onCreate$lambda$1' and k[0].endswith('/InputDialogActivity;'))
    assert any('Intent;->putExtra' in text for _, text in launch)
    assert any('InputDialogActivity;->date' in text for _, text in launch)
    print(json.dumps({'package':after.get_package(), 'version':after.get_androidversion_name(),
                      'preserved_dex':[n for n in dex if n!='classes3.dex'],
                      'changed_existing_methods':[k[1] for k in sorted(changed)],
                      'unchanged_classes3_methods':len(ma)-len(changed),
                      'manifest':'only version metadata changed'}, indent=2))
