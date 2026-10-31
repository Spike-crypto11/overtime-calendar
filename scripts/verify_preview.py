from loguru import logger
logger.remove()
from androguard.core.apk import APK
from androguard.core.dex import DEX
from pathlib import Path
import argparse,hashlib,json
parser=argparse.ArgumentParser();parser.add_argument('old');parser.add_argument('new');parser.add_argument('--report',required=True);args=parser.parse_args()
old=APK(args.old);new=APK(args.new)
def parse(apk):
 classes={}
 for raw in apk.get_all_dex():
  for c in DEX(raw).get_classes(): classes[c.get_name()]=c
 return classes
before,after=parse(old),parse(new)
allowed={'Lcom/dohyun/overtimecalendar/'+x+';' for x in ['Prefs','SyncManager','SettingsActivity','MainActivity']}
changes=[];missing=[];unchanged=0
for name,c in before.items():
 if not name.startswith('Lcom/dohyun/'):continue
 n=after.get(name)
 if n is None:missing.append(name);continue
 methods={(m.get_name(),m.get_descriptor()):m for m in n.get_methods()}
 for m in c.get_methods():
  key=(m.get_name(),m.get_descriptor());match=methods.get(key)
  def instructions(x):return [(i.get_name(),i.get_output()) for i in x.get_instructions()]
  if match is None or instructions(m)!=instructions(match):changes.append([name,*key])
  else:unchanged+=1
assert not missing,missing
unexpected=[m for m in changes if m[0] not in allowed]
assert not unexpected,unexpected
# Resolve newly compiled module references to pre-existing non-platform classes/methods.
addon=DEX(new.get_file('classes6.dex'))
def resolves(cls,name,descriptor,seen=None):
 if cls.startswith(('Ljava/','Ljavax/','Landroid/','Lorg/json/','Ldalvik/')):return True
 if cls.startswith('['):return True
 seen=set() if seen is None else seen
 if cls in seen:return False
 seen.add(cls);c=after.get(cls)
 if not c:return False
 if any(m.get_name()==name and m.get_descriptor()==descriptor for m in c.get_methods()):return True
 if name=='<init>':return False
 return any(resolves(parent,name,descriptor,seen) for parent in [c.get_superclassname(),*c.get_interfaces()] if parent)
unresolved=[]
for m in addon.get_methods():
 if not resolves(m.get_class_name(),m.get_name(),m.get_descriptor()):
  unresolved.append([m.get_class_name(),m.get_name(),m.get_descriptor()])
assert not unresolved,unresolved
old_manifest=old.get_android_manifest_xml();new_manifest=new.get_android_manifest_xml()
ns='{http://schemas.android.com/apk/res/android}'
def attrs(e):return {k:v for k,v in e.attrib.items() if k not in {ns+'versionCode',ns+'versionName'}}
assert attrs(old_manifest)==attrs(new_manifest)
old_app=old_manifest.find('application');new_app=new_manifest.find('application');assert attrs(old_app)==attrs(new_app)
old_components={(x.tag,x.get(ns+'name')):dict(x.attrib) for x in old_app}
new_components={(x.tag,x.get(ns+'name')):dict(x.attrib) for x in new_app}
assert all(new_components.get(k)==v for k,v in old_components.items())
added=set(new_components)-set(old_components)
assert added=={('activity','com.dohyun.overtimecalendar.PayrollActivity')}
assert new_app.find("activity[@"+ns+"name='com.dohyun.overtimecalendar.PayrollActivity']").get(ns+'exported')=='false'
result={'oldAppClasses':sum(x.startswith('Lcom/dohyun/') for x in before),
 'retainedAppClasses':sum(x.startswith('Lcom/dohyun/') and x in after for x in before),
 'unchangedExistingAppMethods':unchanged,'changedExistingMethods':changes,
 'unexpectedChangedClasses':unexpected,'unresolvedAddonMethodReferences':unresolved,
 'existingManifestComponentsPreserved':True,'oldApplicationAttributesPreserved':True,
 'addedActivityExported':False,'deviceExecutionTested':False,
 'apkSHA256':hashlib.sha256(Path(args.new).read_bytes()).hexdigest()}
Path(args.report).write_text(json.dumps(result,ensure_ascii=False,indent=2))
print(json.dumps({k:v for k,v in result.items() if k!='changedExistingMethods'},ensure_ascii=False,indent=2))
