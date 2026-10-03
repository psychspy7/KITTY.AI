"""Audit the built APK, including permissions merged in by dependencies."""
import argparse
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ALLOWED = {'android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE','com.kitty.ai.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
ANDROID='{http://schemas.android.com/apk/res/android}'


def check_manifest(path):
    root=ET.parse(path).getroot()
    for node in root:
        if node.get('{http://schemas.android.com/tools}node') == 'remove':continue
        if node.tag.startswith('uses-permission') and node.get(ANDROID+'name') not in ALLOWED:
            raise ValueError('Unexpected permission: '+node.get(ANDROID+'name',''))
    app=root.find('application')
    if app.get(ANDROID+'usesCleartextTraffic') != 'false':raise ValueError('Cleartext is not disabled')
    for node in app:
        name=node.get(ANDROID+'name','')
        if any(v in name for v in ['VoiceService','Accessibility','Shizuku','FileProvider']):raise ValueError('Legacy privileged component remains: '+name)
    for node in root.findall('permission'):
        if node.get(ANDROID+'protectionLevel') != 'signature':raise ValueError('Unexpected app-owned permission')


def main():
    p=argparse.ArgumentParser();p.add_argument('--apk',type=Path,required=True);p.add_argument('--aapt2',type=Path,required=True);p.add_argument('--merged-manifest',type=Path,required=True);args=p.parse_args()
    check_manifest(args.merged_manifest)
    output=subprocess.check_output([str(args.aapt2),'dump','permissions',str(args.apk)],text=True)
    permissions=set(re.findall(r"uses-permission[^\n]*name='([^']+)'",output))
    if not permissions or permissions-ALLOWED:raise SystemExit('APK permission audit failed: '+repr(permissions))
    if not {'android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE'} <= permissions:raise SystemExit('Network permissions missing')
    print('PASS: built APK has only network access and an optional app-owned signature permission.\n'+output)


if __name__=='__main__':main()
