#!/usr/bin/env python3
"""Correct only leave foreground/background methods in verified Daylog v19."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

def patch(project):
    path = project / 'smali_classes6/com/dohyun/overtimecalendar/PayrollCategoryStyle.smali'
    text = path.read_text()
    def method(signature, body):
        nonlocal text
        start = text.index(signature + '\n')
        end = text.index('.end method', start) + len('.end method')
        text = text[:start] + signature + '\n' + body + '\n.end method' + text[end:]
    method('.method public static final chipColor(I)I', '''    .locals 0
    return p0''')
    start = text.index('.method public static final labelColor(')
    end = text.index('.end method', start)
    part = text[start:end]
    old = '    sget p1, Lcom/dohyun/overtimecalendar/PayrollCategoryStyle;->LEAVE_RED:I'
    assert part.count(old) == 1
    text = text[:start] + part.replace(old, '    const/4 p1, -0x1') + text[end:]
    start = text.index('.method public static final styleText(')
    end = text.index('.end method', start)
    part = text[start:end]
    old = '    sget v0, Lcom/dohyun/overtimecalendar/PayrollCategoryStyle;->LEAVE_RED:I'
    assert part.count(old) == 1
    part = part.replace(old, '    const/4 v0, -0x1')
    part = part.replace('->LEAVE_BACKGROUND:I', '->LEAVE_RED:I')
    text = text[:start] + part + text[end:]
    method('.method public static final setWidgetLabel(Landroid/widget/RemoteViews;ILjava/lang/CharSequence;)V', '''    .locals 1
    invoke-virtual {p0, p1, p2}, Landroid/widget/RemoteViews;->setTextViewText(ILjava/lang/CharSequence;)V
    sget-object v0, Lcom/dohyun/overtimecalendar/PayrollCategoryStyle;->INSTANCE:Lcom/dohyun/overtimecalendar/PayrollCategoryStyle;
    invoke-direct {v0, p2}, Lcom/dohyun/overtimecalendar/PayrollCategoryStyle;->isLeave(Ljava/lang/CharSequence;)Z
    move-result v0
    if-eqz v0, :daylog_label_done
    const/4 v0, -0x1
    invoke-virtual {p0, p1, v0}, Landroid/widget/RemoteViews;->setTextColor(II)V
    :daylog_label_done
    return-void''')
    path.write_text(text)
    path = project / 'apktool.yml'
    text, count = re.subn(r'versionCode: [\'\"]?19[\'\"]?', 'versionCode: 20', path.read_text())
    assert count == 1
    path.write_text(text.replace('1.18-daylog-leave-background', '1.19-daylog-leave-contrast'))

def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('installed', 'bundle', 'recovery', 'output'): p.add_argument('--'+name, required=True, type=Path)
    a = p.parse_args()
    old,bundle,recovery,output=[getattr(a,n).resolve() for n in ('installed','bundle','recovery','output')]
    assert hashlib.sha256(old.read_bytes()).hexdigest()=='36c7c00a755370ced6dd678a161c4206fc4d43d02796862bd15bddb415c546a0'
    def run(*args): subprocess.run([str(x) for x in args],check=True)
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='daylog-contrast-',dir=output.parent) as temp:
        t=Path(temp);project=t/'decoded'
        run('java','-jar',bundle/'apktool.jar','d','-f',old,'-o',project)
        patch(project)
        with zipfile.ZipFile(old) as z:
            for name in z.namelist():
                if re.fullmatch(r'classes\d*\.dex',name) and name!='classes6.dex':
                    suffix=name[len('classes'):-4]
                    shutil.rmtree(project/('smali' if not suffix else 'smali_classes'+suffix))
                    (project/name).write_bytes(z.read(name))
        run('java','-jar',bundle/'apktool.jar','b',project,'-o',t/'unsigned.apk')
        bt=bundle/'build-tools'
        run(bt/'zipalign','-p','-f','4',t/'unsigned.apk',t/'aligned.apk')
        run(bt/'apksigner','sign','--ks',recovery/'signing/weekend-preview.jks','--ks-key-alias','weekendpreview',
            '--ks-pass','pass:android','--key-pass','pass:android','--out',output,t/'aligned.apk')
        run(bt/'apksigner','verify','--print-certs',output)
        run(bt/'zipalign','-c','-p','4',output)
    print(output)

if __name__=='__main__': main()
