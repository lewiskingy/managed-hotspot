#!/usr/bin/env python3
"""Pinned JVM-only JSON dependency; never included in the Android app."""
import hashlib, pathlib, urllib.request, subprocess
p=pathlib.Path('build/test-deps/json.jar');p.parent.mkdir(parents=True,exist_ok=True)
data=p.read_bytes() if p.exists() else urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',timeout=60).read()
assert hashlib.sha256(data).hexdigest()=='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed', 'JSON checksum mismatch'
p.write_bytes(data)
root='app/src/main/java/uk/me/theflat/hotspot/'
subprocess.run(['javac','-cp',str(p),'-d','build/backup',*[root+x+'.java' for x in ['SettingsBackupCodec','SessionRules','DeviceIdentity']],'tests/SettingsBackupTest.java'],check=True)
subprocess.run(['java','-cp','build/backup:'+str(p),'SettingsBackupTest'],check=True)
