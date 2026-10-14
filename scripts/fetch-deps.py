#!/usr/bin/env python3
"""Fetch pinned upstream Shizuku artifacts for the dependency-free SDK build."""
from pathlib import Path
import hashlib, io, urllib.request, zipfile
HASHES={'shizuku-api.aar': '4def9bde498ef8626614c2fc5db9af4749c86f16f6c33e3f5658d35e70bab59b', 'shizuku-aidl.aar': '33fe7191cdd69fcb66d649264f3b0c47acb2f3d6343afc05b98dbbff6f221963', 'shizuku-provider.aar': 'b0f18cd9812464ec171c53cac93a819fe411718a3965c311f01eb4de265381b3', 'shizuku-shared.aar': '4659642c9339be0a26e9c65bb8648f7ad6d8f4a465f557993ccbc78802381635'}
out=Path('app/libs');out.mkdir(parents=True,exist_ok=True)
for filename,digest in HASHES.items():
    target=out/filename.replace('.aar','.jar')
    # Verify both the upstream archive and extracted jar even on repeated builds.
    part=filename.removeprefix('shizuku-').removesuffix('.aar')
    cache=Path('build/deps')/filename;cache.parent.mkdir(parents=True,exist_ok=True)
    data=cache.read_bytes() if cache.exists() else urllib.request.urlopen(f'https://repo.maven.apache.org/maven2/dev/rikka/shizuku/{part}/13.1.5/{part}-13.1.5.aar',timeout=60).read()
    if hashlib.sha256(data).hexdigest()!=digest: raise RuntimeError('Dependency checksum mismatch: '+filename)
    cache.write_bytes(data)
    with zipfile.ZipFile(io.BytesIO(data)) as z: target.write_bytes(z.read('classes.jar'))
