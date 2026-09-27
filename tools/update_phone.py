"""Install KITTY's test APK; back up before a confirmed one-time signing migration."""
import argparse
from datetime import datetime
import io
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
PACKAGE='com.kitty.ai'

def sanitize_backup(source,target):
    """Copy only app files; old Android Keystore ciphertext cannot survive reinstall."""
    with tarfile.open(source,'r:') as old,tarfile.open(target,'w') as new:
        count=0
        for member in old:
            path=Path(member.name)
            if path.is_absolute() or '..' in path.parts or not path.parts or path.parts[0] not in {'files','shared_prefs','databases'} or not (member.isfile() or member.isdir()):
                raise ValueError('Unexpected entry in app backup')
            if member.isdir():new.addfile(member);continue
            content=old.extractfile(member)
            if member.name=='shared_prefs/kitty.xml':
                root=ET.fromstring(content.read())
                for entry in list(root):
                    if entry.get('name') in {'token','iv','pending_action_nonce','pending_action_command'}:root.remove(entry)
                data=ET.tostring(root,encoding='utf-8',xml_declaration=True);member.size=len(data);content=io.BytesIO(data)
            new.addfile(member,content);count+=1
        if count==0:raise ValueError('App backup was empty')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apk',type=Path,default=ROOT/'KITTY-AI-0.2.0.apk');p.add_argument('--serial');args=p.parse_args()
    adb=shutil.which('adb')
    if not adb:raise SystemExit('Install Android platform-tools and put adb on PATH first.')
    if not args.apk.is_file():raise SystemExit('Put KITTY-AI-0.2.0.apk next to INSTALL_UPDATE.bat, or use --apk PATH.')
    devices=subprocess.check_output([adb,'devices'],text=True)
    available=[line.split()[0] for line in devices.splitlines()[1:] if line.endswith('\tdevice')]
    serial=args.serial
    if serial is None:
        if len(available)!=1:raise SystemExit('Connect exactly one authorized phone, or pass --serial.')
        serial=available[0]
    if serial not in available:raise SystemExit('The selected phone is not connected and authorized.')
    def call(*parts,**kwargs):return subprocess.run([adb,'-s',serial,*parts],check=True,**kwargs)
    result=subprocess.run([adb,'-s',serial,'install','-r',str(args.apk.resolve())],capture_output=True,text=True)
    if result.returncode==0:
        print('KITTY updated. Existing app data retained.');return
    if 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' not in result.stdout+result.stderr:
        raise SystemExit(result.stdout+result.stderr)
    print('The old APK uses a different signing key. It is still installed. Making a phone backup before asking to reinstall.')
    call('shell','am','force-stop',PACKAGE)
    backup=ROOT/'data'/('phone-backup-'+datetime.now().strftime('%Y%m%d-%H%M%S'));backup.mkdir(parents=True)
    listing=call('exec-out','run-as',PACKAGE,'ls',capture_output=True,text=True).stdout.splitlines()
    folders=[name for name in ('files','shared_prefs','databases') if name in listing]
    if not folders:raise SystemExit('No app data could be read. Nothing was uninstalled.')
    raw=backup/'original-data.tar';restore=backup/'restore-data.tar'
    with raw.open('wb') as out:call('exec-out','run-as',PACKAGE,'tar','-cf','-',*folders,stdout=out)
    sanitize_backup(raw,restore)
    packages=call('shell','pm','path',PACKAGE,capture_output=True,text=True).stdout.splitlines()
    if len(packages)!=1 or not packages[0].startswith('package:/'):raise SystemExit('Unexpected APK layout. Backup saved; nothing uninstalled.')
    call('pull',packages[0][8:],str(backup/'previous.apk'))
    print('Verified backup:',backup)
    print('Reinstall restores the offline model, app settings and saved chats. You must paste the pairing token again and regrant Android/Accessibility permissions. Laptop data is unaffected.')
    if input('Type REINSTALL to continue, or press Enter to keep the old app: ').strip()!='REINSTALL':
        print('Old app kept.');return
    call('uninstall',PACKAGE)
    try:call('install',str(args.apk.resolve()))
    except subprocess.CalledProcessError:
        print('New APK could not install; restoring the previous APK.')
        call('install',str(backup/'previous.apk'))
        with restore.open('rb') as data:call('exec-in','run-as',PACKAGE,'tar','-xf','-',stdin=data)
        raise SystemExit('Previous APK and data restored. Pair the token again.')
    try:
        with restore.open('rb') as data:call('exec-in','run-as',PACKAGE,'tar','-xf','-',stdin=data)
    except subprocess.CalledProcessError:
        raise SystemExit('APK installed but restore failed. Keep the backup directory; do not delete it. This helper requires the supplied debug test APK.')
    print('Update restored. Open KITTY, paste the laptop token in Settings, and grant the permissions you use.')

if __name__=='__main__':main()
