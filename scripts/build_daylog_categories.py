#!/usr/bin/env python3
"""Build v18 from the verified Daylog v17, preserving the owner's records and private key."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile
from build_daylog_widgets import replace_once

def patch(project, half_id):
    manifest = project / 'AndroidManifest.xml'
    text = manifest.read_text()
    assert 'daylog.confirmedHalfDayId' not in text
    if half_id:
        assert re.fullmatch(r'[a-zA-Z0-9_]+', half_id)
        text = replace_once(text, '</application>', '<meta-data android:name="daylog.confirmedHalfDayId" android:value="' + half_id + '"/></application>')
    manifest.write_text(text)
    yaml = project / 'apktool.yml'
    text, count = re.subn(r'versionCode: [\'\"]?17[\'\"]?', 'versionCode: 18', yaml.read_text())
    assert count == 1
    yaml.write_text(replace_once(text, '1.16-daylog-widgets', '1.17-daylog-record-styles'))
    root = project / 'smali_classes3/com/dohyun/overtimecalendar'
    prefs = root / 'Prefs.smali'
    text = replace_once(prefs.read_text(), '.method public final getCategories(Landroid/content/Context;)Ljava/util/List;',
        '.method private final daylogOriginalCategories(Landroid/content/Context;)Ljava/util/List;')
    text += '''
.method public final getCategories(Landroid/content/Context;)Ljava/util/List;
    .locals 1
    invoke-direct {p0, p1}, Lcom/dohyun/overtimecalendar/Prefs;->daylogOriginalCategories(Landroid/content/Context;)Ljava/util/List;
    move-result-object v0
    invoke-static {p1, v0}, Lcom/dohyun/overtimecalendar/PayrollCategoryStyle;->ensure(Landroid/content/Context;Ljava/util/List;)Ljava/util/List;
    move-result-object v0
    return-object v0
.end method
'''
    prefs.write_text(text)
    adapter = root / 'CalendarAdapter.smali'
    text = adapter.read_text()
    start = text.index('.method private final bindLine(')
    end = text.index('.end method', start)
    method = text[start:end]
    at = method.rindex('    return-void')
    method = method[:at] + '    invoke-static {p1}, Lcom/dohyun/overtimecalendar/PayrollCategoryStyle;->styleText(Landroid/widget/TextView;)V\n' + method[at:]
    adapter.write_text(text[:start] + method + text[end:])
    widget = project / 'smali_classes4/com/dohyun/overtimecalendar/widget'
    provider = widget / 'CalendarWidgetProvider$Companion.smali'
    text, count = re.subn(r'invoke-virtual (\{[^\n]+\}), Landroid/widget/RemoteViews;->setTextViewText\(ILjava/lang/CharSequence;\)V',
        r'invoke-static \1, Lcom/dohyun/overtimecalendar/PayrollCategoryStyle;->setWidgetLabel(Landroid/widget/RemoteViews;ILjava/lang/CharSequence;)V', provider.read_text())
    assert count == 6
    provider.write_text(text)
    artwork = widget / 'ArtworkWidget.smali'
    text = artwork.read_text()
    text = replace_once(text, '    .local v6, "base":I\n', '    .local v6, "base":I\n    invoke-static {v6}, Lcom/dohyun/overtimecalendar/PayrollCategoryStyle;->chipColor(I)I\n    move-result v6\n')
    start = text.index('.method private static final bitmap$text(')
    end = text.index('.end method', start)
    method = text[start:end]
    method = replace_once(method, '    invoke-virtual {p0, p6}, Landroid/graphics/Paint;->setColor(I)V',
        '    invoke-static {p2, p6}, Lcom/dohyun/overtimecalendar/PayrollCategoryStyle;->labelColor(Ljava/lang/String;I)I\n    move-result p6\n    invoke-virtual {p0, p6}, Landroid/graphics/Paint;->setColor(I)V')
    artwork.write_text(text[:start] + method + text[end:])
    # Reduce the approved plain-skin proportions and text sizes together.
    sizing = widget / 'WidgetSizing.smali'
    sizing.write_text(replace_once(sizing.read_text(), '0x3fcccccd    # 1.6f', '0x3fb33333    # 1.4f'))
    legibility = widget / 'WeekendLegibility.smali'
    text = replace_once(legibility.read_text(), 'const/16 p3, 0x46', 'const/16 p3, 0x3c')
    text = replace_once(text, '0x42980000    # 76.0f', '0x42a00000    # 80.0f')
    legibility.write_text(text)
    for skin in ('weekend', 'midnight'):
        p = project / ('res/layout/widget_' + skin + '.xml')
        text = p.read_text().replace('android:autoSizeMaxTextSize="20.0sp"', 'android:autoSizeMaxTextSize="18.0sp"')
        text = text.replace('android:textSize="20.0sp"', 'android:textSize="18.0sp"')
        text = text.replace('android:layout_height="44.0dip"', 'android:layout_height="40.0dip"')
        text = text.replace('android:layout_height="34.0dip"', 'android:layout_height="30.0dip"')
        p.write_text(text)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('installed', 'bundle', 'recovery', 'output'):
        parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--confirmed-half-id')
    a = parser.parse_args()
    old, bundle, recovery, output = [getattr(a, x).resolve() for x in ('installed', 'bundle', 'recovery', 'output')]
    assert hashlib.sha256(old.read_bytes()).hexdigest() == '6c82bacd43a9d4160b1d717a3f8efcc3a08d81a2ed4d6d4726df642599515ffa'
    def run(*args): subprocess.run([str(x) for x in args], check=True)
    output.parent.mkdir(parents=True, exist_ok=True)
    bt = bundle / 'build-tools'
    for tool in ('apksigner', 'zipalign'): (bt / tool).chmod(0o755)
    with tempfile.TemporaryDirectory(prefix='daylog-categories-', dir=output.parent) as tmp:
        t = Path(tmp); project = t / 'decoded'
        run('java', '-jar', bundle / 'apktool.jar', 'd', '-f', old, '-o', project)
        patch(project, a.confirmed_half_id)
        with zipfile.ZipFile(old) as archive:
            for name in ('classes.dex', 'classes2.dex', 'classes5.dex'):
                suffix = name[len('classes'):-4]
                shutil.rmtree(project / ('smali' if not suffix else 'smali_classes' + suffix))
                (project / name).write_bytes(archive.read(name))
        shutil.rmtree(project / 'smali_classes6')
        (project / 'classes6.dex').write_bytes((bundle / 'dex/classes.dex').read_bytes())
        run('java', '-jar', bundle / 'apktool.jar', 'b', project, '-o', t / 'unsigned.apk')
        run(bt / 'zipalign', '-p', '-f', '4', t / 'unsigned.apk', t / 'aligned.apk')
        run(bt / 'apksigner', 'sign', '--ks', recovery / 'signing/weekend-preview.jks', '--ks-key-alias', 'weekendpreview',
            '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', output, t / 'aligned.apk')
        run(bt / 'apksigner', 'verify', '--print-certs', output)
        run(bt / 'zipalign', '-c', '-p', '4', output)
    print(output)

if __name__ == '__main__': main()
