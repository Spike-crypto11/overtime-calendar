#!/usr/bin/env python3
"""Rename and fit the four recovered preview skins; retain payroll v16 and the original key."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

def replace_once(text, old, new):
    assert text.count(old) == 1, 'Unexpected source structure: ' + old[:70]
    return text.replace(old, new)

def patch(project):
    manifest = project / 'AndroidManifest.xml'
    text = manifest.read_text()
    assert 'package="com.dohyun.overtimecalendar.preview"' in text
    manifest.write_text(replace_once(text, 'android:label="위켄드 크기 확인"', 'android:label="@string/app_name"'))
    strings = project / 'res/values/strings.xml'
    text = strings.read_text()
    text = re.sub(r'(<string name="app_name">).*?(</string>)', r'\g<1>데이로그\2', text)
    text = re.sub(r'(<string name="widget_label">).*?(</string>)', r'\g<1>데이로그 달력\2', text)
    strings.write_text(text)
    yaml = project / 'apktool.yml'
    text, count = re.subn(r'versionCode: [\'\"]?16[\'\"]?', 'versionCode: 17', yaml.read_text())
    assert count == 1
    yaml.write_text(replace_once(text, '1.15-payroll-categories', '1.16-daylog-widgets'))
    root = project / 'smali_classes4/com/dohyun/overtimecalendar/widget'
    # The 44dp header, its 8dp padding, 34dp weekday row and outer padding need up to 102dp.
    # Reserve 104dp instead of 96; never force a 24dp minimum that exceeds available height.
    sizing = root / 'WidgetSizing.smali'
    text = sizing.read_text()
    text = replace_once(text, 'add-int/lit8 v2, p1, -0x60', 'add-int/lit8 v2, p1, -0x68')
    text = replace_once(text, 'const/16 v3, 0x18', 'const/16 v3, 0x1')
    sizing.write_text(text)
    # Run the existing final text pass for both plain skins, after all record labels.
    legibility = root / 'WeekendLegibility.smali'
    text = legibility.read_text()
    old = '''    move-result p3

    if-eqz p3, :cond_6

    const/16 p3, 0x46'''
    new = '''    move-result v0

    if-nez v0, :daylog_plain_skin

    const-string v0, "midnight"
    invoke-virtual {v0, p3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :cond_6

    :daylog_plain_skin
    const/16 p3, 0x46'''
    legibility.write_text(replace_once(text, old, new))
    # Same shape and colors; bring the dark header/readability limits in line with the light skin.
    midnight = project / 'res/layout/widget_midnight.xml'
    text = midnight.read_text()
    text = text.replace('android:autoSizeMaxTextSize="17.0sp"', 'android:autoSizeMaxTextSize="20.0sp"')
    text = text.replace('android:textSize="13.0sp"', 'android:textSize="15.0sp"')
    midnight.write_text(text)
    # Fit the whole artwork and its touch map to BOTH dimensions, including settings previews.
    # The immutable bitmap keeps its 750:918 aspect; no independent horizontal/vertical stretching.
    artwork = root / 'ArtworkWidget.smali'
    text = artwork.read_text()
    start = text.index('.method public final views(')
    before, method = text[:start], text[start:]
    old = '''    if-nez p2, :cond_3

    sget v4, Landroid/os/Build$VERSION;->SDK_INT:I

    if-lt v4, v1, :cond_3

    .line 150'''
    method = replace_once(method, old, '    .line 150')
    method = replace_once(method, '    const/16 v3, 0x78', '    const/16 v3, 0x1')
    method = replace_once(method, '    if-nez p2, :cond_4\n\n', '')
    method = replace_once(method, '    const/high16 v3, 0x41c00000    # 24.0f', '    const/4 v3, 0x0    # No artificial blank space below artwork')
    artwork.write_text(before + method)

def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('installed', 'bundle', 'recovery', 'output'):
        p.add_argument('--' + name, required=True, type=Path)
    a = p.parse_args()
    old, bundle, recovery, output = [getattr(a, x).resolve() for x in ('installed', 'bundle', 'recovery', 'output')]
    assert hashlib.sha256(old.read_bytes()).hexdigest() == '490059467dde964e73890a42f79bb54440f4ec24c5610aab68d22ed11e2f6164', 'Unsupported v16 base'
    def run(*args):
        subprocess.run([str(x) for x in args], check=True)
    bt = bundle / 'build-tools'
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='daylog-widgets-', dir=output.parent) as t:
        project = Path(t) / 'decoded'
        run('java', '-jar', bundle / 'apktool.jar', 'd', '-f', old, '-o', project)
        patch(project)
        # Reassemble only the widget DEX. All record storage, sync and payroll DEX remain exact bytes.
        with zipfile.ZipFile(old) as apk:
            for name in apk.namelist():
                if re.fullmatch(r'classes\d*\.dex', name) and name != 'classes4.dex':
                    suffix = name[len('classes'):-4]
                    smali = project / ('smali' if not suffix else 'smali_classes' + suffix)
                    shutil.rmtree(smali)
                    (project / name).write_bytes(apk.read(name))
        run('java', '-jar', bundle / 'apktool.jar', 'b', project, '-o', Path(t) / 'unsigned.apk')
        run(bt / 'zipalign', '-p', '-f', '4', Path(t) / 'unsigned.apk', Path(t) / 'aligned.apk')
        run(bt / 'apksigner', 'sign', '--ks', recovery / 'signing/weekend-preview.jks',
            '--ks-key-alias', 'weekendpreview', '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
            '--out', output, Path(t) / 'aligned.apk')
        run(bt / 'apksigner', 'verify', '--print-certs', output)
        run(bt / 'zipalign', '-c', '-p', '4', output)
    with zipfile.ZipFile(old) as before, zipfile.ZipFile(output) as after:
        for name in before.namelist():
            if re.fullmatch(r'classes\d*\.dex', name) and name != 'classes4.dex':
                assert before.read(name) == after.read(name), name
    print('Non-widget DEX files unchanged, including record storage, server sync and payroll.')
    print(output)

if __name__ == '__main__':
    main()
