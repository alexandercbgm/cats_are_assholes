#!/usr/bin/env python3
"""Copies ../index.html into app/src/main/assets with the Google Fonts bundled, so the app works offline.
Run it after every change to the game:  python3 tools/prepare_assets.py"""
import pathlib, re, shutil, urllib.request

HERE = pathlib.Path(__file__).resolve().parent.parent
SRC = HERE.parent / 'index.html'
OUT = HERE / 'app' / 'src' / 'main' / 'assets'
UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36'


def get(url, binary=False):
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': UA}), timeout=60) as r:
        data = r.read()
    return data if binary else data.decode()


s = SRC.read_text(encoding='utf-8')
link = re.search(r'<link rel="stylesheet" href="(https://fonts.googleapis.com[^"]+)">', s)
assert link, 'Google Fonts <link> not found in index.html'
css = get(link.group(1).replace('&amp;', '&'))

shutil.rmtree(OUT, ignore_errors=True)
(OUT / 'fonts').mkdir(parents=True)
faces, files = [], {}
for subset, face in re.findall(r'/\* ([\w-]+) \*/\s*(@font-face\s*\{[^}]*\})', css):
    if subset not in ('cyrillic', 'cyrillic-ext', 'latin', 'latin-ext'):
        continue
    url = re.search(r'url\((https://[^)]+)\)', face).group(1)
    if url not in files:  # variable fonts: one file serves every weight
        files[url] = f'f{len(files)}.woff2'
        (OUT / 'fonts' / files[url]).write_bytes(get(url, binary=True))
    faces.append(face.replace(url, files[url]))
assert faces, 'no fonts downloaded'
(OUT / 'fonts' / 'fonts.css').write_text('\n'.join(faces) + '\n', encoding='utf-8')

s = re.sub(r'<link rel="preconnect"[^>]*>\n?', '', s).replace(link.group(0), '<link rel="stylesheet" href="fonts/fonts.css">')
s = s.replace('viewport-fit=cover">', 'viewport-fit=cover,user-scalable=no">', 1)
(OUT / 'index.html').write_text(s, encoding='utf-8')
print(f'assets: index.html + {len(files)} font files -> {OUT}')
