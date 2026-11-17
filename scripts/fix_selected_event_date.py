#!/usr/bin/env python3
"""Make an update-compatible Daylog v21 from the verified v20 APK.

Only classes3 is rebuilt. All other DEX files are preserved byte for byte.
Requires a locally held signing key; no signing material belongs in this repo.
"""
import argparse
import hashlib
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

PACKAGE = 'Lcom/dohyun/overtimecalendar/'

def patch(project):
    folder = project / 'smali_classes3/com/dohyun/overtimecalendar'
    path = folder / 'InputDialogActivity.smali'
    text = path.read_text()
    start = text.index('.method private static final onCreate$lambda$1(')
    end = text.index('.end method', start)
    part = text[start:end]
    needle = '    invoke-direct {v0, v1, v2}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V'
    assert part.count(needle) == 1
    extra = '''
    const-string v1, "date"
    iget-object v2, p0, Lcom/dohyun/overtimecalendar/InputDialogActivity;->date:Ljava/lang/String;
    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;
'''
    text = text[:start] + part.replace(needle, needle + '\n' + extra) + text[end:]
    path.write_text(text)

    path = folder / 'MainActivity.smali'
    text = path.read_text()
    anchor = '# instance fields\n'
    assert text.count(anchor) == 1
    text = text.replace(anchor, anchor + '.field private daylogSelectedDate:Ljava/lang/String;\n\n')
    start = text.index('.method private final showInputDialog(Ljava/lang/String;)V')
    end = text.index('.end method', start)
    part = text[start:end]
    part, count = re.subn(r'(    \.locals \d+\n)', r'\1\n    move-object/from16 v0, p0\n    move-object/from16 v1, p1\n    iput-object v1, v0, Lcom/dohyun/overtimecalendar/MainActivity;->daylogSelectedDate:Ljava/lang/String;\n', part, count=1)
    assert count == 1
    text = text[:start] + part + text[end:]
    start = text.index('.method private static final showInputDialog$lambda$21(')
    end = text.index('.end method', start)
    part = text[start:end]
    needle = '    invoke-direct {v0, v1, v2}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V'
    assert part.count(needle) == 1
    extra = '''
    const-string v1, "date"
    iget-object v2, p1, Lcom/dohyun/overtimecalendar/MainActivity;->daylogSelectedDate:Ljava/lang/String;
    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;
'''
    text = text[:start] + part.replace(needle, needle + '\n' + extra) + text[end:]
    path.write_text(text)

    path = folder / 'EventActivity.smali'
    text = path.read_text()
    start = text.index('.method private final showEdit(')
    end = text.index('.end method', start)
    part = text[start:end]
    needle = '''    sget-object v1, Lcom/dohyun/overtimecalendar/DateUtils;->INSTANCE:Lcom/dohyun/overtimecalendar/DateUtils;

    invoke-virtual {v1}, Lcom/dohyun/overtimecalendar/DateUtils;->today()Ljava/lang/String;'''
    assert part.count(needle) == 1
    part = part.replace(needle, '    invoke-direct/range {p0 .. p0}, Lcom/dohyun/overtimecalendar/EventActivity;->daylogInitialDate()Ljava/lang/String;')
    text = text[:start] + part + text[end:]
    assert 'daylogInitialDate()Ljava/lang/String;\n    .locals' not in text
    text += '''

.method private final daylogInitialDate()Ljava/lang/String;
    .locals 5

    :try_start_daylog
    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
    move-result-object v0
    const-string v1, "date"
    invoke-virtual {v0, v1}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v1
    if-eqz v1, :daylog_today

    new-instance v0, Ljava/text/SimpleDateFormat;
    const-string v2, "yyyy-MM-dd"
    sget-object v3, Ljava/util/Locale;->ROOT:Ljava/util/Locale;
    invoke-direct {v0, v2, v3}, Ljava/text/SimpleDateFormat;-><init>(Ljava/lang/String;Ljava/util/Locale;)V
    const/4 v2, 0x0
    invoke-virtual {v0, v2}, Ljava/text/DateFormat;->setLenient(Z)V
    invoke-virtual {v0, v1}, Ljava/text/DateFormat;->parse(Ljava/lang/String;)Ljava/util/Date;
    move-result-object v3
    if-eqz v3, :daylog_today
    invoke-virtual {v0, v3}, Ljava/text/DateFormat;->format(Ljava/util/Date;)Ljava/lang/String;
    move-result-object v4
    invoke-virtual {v1, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v2
    if-eqz v2, :daylog_today
    :try_end_daylog
    return-object v1
    .catch Ljava/lang/Exception; {:try_start_daylog .. :try_end_daylog} :daylog_invalid

    :daylog_invalid
    move-exception v0
    :daylog_today
    sget-object v0, Lcom/dohyun/overtimecalendar/DateUtils;->INSTANCE:Lcom/dohyun/overtimecalendar/DateUtils;
    invoke-virtual {v0}, Lcom/dohyun/overtimecalendar/DateUtils;->today()Ljava/lang/String;
    move-result-object v0
    return-object v0
.end method
'''
    path.write_text(text)
    path = project / 'apktool.yml'
    text, count = re.subn(r'versionCode: [\'\"]?20[\'\"]?', 'versionCode: 21', path.read_text())
    assert count == 1
    assert '1.19-daylog-leave-contrast' in text
    path.write_text(text.replace('1.19-daylog-leave-contrast', '1.20-daylog-selected-date'))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('installed', 'bundle', 'recovery', 'output'):
        parser.add_argument('--' + name, required=True, type=Path)
    args = parser.parse_args()
    old, bundle, recovery, output = [getattr(args, n).resolve() for n in ('installed','bundle','recovery','output')]
    assert hashlib.sha256(old.read_bytes()).hexdigest() == '588672a40f18fd062226d5880fd955a202b6b67ffd59c2c86aaac049d570bc95'
    def run(*cmd): subprocess.run([str(x) for x in cmd], check=True)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='daylog-date-', dir=output.parent) as temp:
        temp = Path(temp)
        project = temp / 'decoded'
        run('java','-jar',bundle/'apktool.jar','d','-f',old,'-o',project)
        patch(project)
        with zipfile.ZipFile(old) as archive:
            for name in archive.namelist():
                if re.fullmatch(r'classes\d*\.dex', name) and name != 'classes3.dex':
                    suffix = name[len('classes'):-4]
                    shutil.rmtree(project/('smali' if not suffix else 'smali_classes'+suffix))
                    (project/name).write_bytes(archive.read(name))
        run('java','-jar',bundle/'apktool.jar','b',project,'-o',temp/'unsigned.apk')
        bt = bundle/'build-tools'
        run(bt/'zipalign','-p','-f','4',temp/'unsigned.apk',temp/'aligned.apk')
        run(bt/'apksigner','sign','--ks',recovery/'signing/weekend-preview.jks','--ks-key-alias','weekendpreview',
            '--ks-pass','pass:android','--key-pass','pass:android','--out',output,temp/'aligned.apk')
        run(bt/'apksigner','verify','--print-certs',output)
        run(bt/'zipalign','-c','-p','4',output)
    print(output)

if __name__ == '__main__': main()
