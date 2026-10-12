#!/usr/bin/env bash
# Builds dist/ugomoni-kota.apk from ../index.html without Gradle or the Android SDK installer:
#   aapt2 (npm package aaptjs3), android.jar API 16 + dalvik-dx + apksig (Maven Central).
# Signing key: KEYSTORE / KEYSTORE_PASS / KEY_ALIAS, otherwise android/release.p12 is created.
# Keep that keystore: updates to an installed app must be signed with the same key.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
CACHE="$HERE/.tools"
BUILD="$HERE/build"
KEYSTORE="${KEYSTORE:-$HERE/release.p12}"
KEYSTORE_PASS="${KEYSTORE_PASS:-ugomoni}"
KEY_ALIAS="${KEY_ALIAS:-ugomoni}"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0}"
MAVEN=https://repo1.maven.org/maven2

mkdir -p "$CACHE"
CURL=(curl -fsSL --retry 5 --retry-delay 3 --retry-all-errors)
fetch() { [ -s "$CACHE/$1" ] || "${CURL[@]}" -o "$CACHE/$1" "$2"; }
fetch android-16.jar "$MAVEN/com/google/android/android/4.1.1.4/android-4.1.1.4.jar"
fetch dx.jar "$MAVEN/com/jakewharton/android/repackaged/dalvik-dx/16.0.1/dalvik-dx-16.0.1.jar"
fetch apksig.jar "$MAVEN/com/android/tools/build/apksig/2.3.0/apksig-2.3.0.jar"
if [ ! -x "$CACHE/aapt2" ]; then
  fetch aaptjs3.tgz "$("${CURL[@]}" https://registry.npmjs.org/aaptjs3/latest | python3 -c 'import sys,json;print(json.load(sys.stdin)["dist"]["tarball"])')"
  tar xzf "$CACHE/aaptjs3.tgz" -C "$CACHE" package/bin/x64/linux/aapt2
  mv "$CACHE/package/bin/x64/linux/aapt2" "$CACHE/aapt2" && chmod +x "$CACHE/aapt2"
fi
AAPT2="$CACHE/aapt2"; AJ="$CACHE/android-16.jar"

rm -rf "${BUILD:?}"; mkdir -p "$BUILD/assets/fonts" "$BUILD/classes" "$BUILD/signer"

# game page with bundled fonts (works offline)
python3 - "$ROOT/index.html" "$BUILD/assets" <<'PY'
import sys,re,subprocess
src,out=sys.argv[1:3]
s=open(src).read()
m=re.search(r'<link rel="stylesheet" href="(https://fonts.googleapis.com[^"]+)">',s)
C=['curl','-fsSL','--retry','5','--retry-delay','3','--retry-all-errors']
css=subprocess.run(C+['-A','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36',m.group(1).replace('&amp;','&')],capture_output=True,text=True,check=True).stdout
faces=[]
for i,(subset,b) in enumerate(re.findall(r'/\* ([\w-]+) \*/\s*(@font-face\s*\{[^}]*\})',css)):
    if subset not in ('cyrillic','cyrillic-ext','latin','latin-ext'):continue
    url=re.search(r'url\((https://[^)]+)\)',b).group(1);fn=f'f{i}.woff2'
    subprocess.run(C+['-o',f'{out}/fonts/{fn}',url],check=True);faces.append(b.replace(url,fn))
assert faces, 'no fonts downloaded'
open(f'{out}/fonts/fonts.css','w').write('\n'.join(faces))
s=re.sub(r'<link rel="preconnect"[^>]*>\n','',s).replace(m.group(0),'<link rel="stylesheet" href="fonts/fonts.css">')
s=s.replace('viewport-fit=cover">','viewport-fit=cover,user-scalable=no">',1)
open(f'{out}/index.html','w').write(s)
PY

"$AAPT2" compile --dir "$HERE/res" -o "$BUILD/res.zip"
"$AAPT2" link -I "$AJ" --manifest "$HERE/AndroidManifest.xml" -A "$BUILD/assets" -R "$BUILD/res.zip" --auto-add-overlay \
  --min-sdk-version 24 --target-sdk-version 34 --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" -o "$BUILD/base.apk"
javac -nowarn --release 8 -cp "$AJ" -d "$BUILD/classes" $(find "$HERE/src" -name '*.java')
java -cp "$CACHE/dx.jar" com.android.dx.command.Main --dex --min-sdk-version=24 --output="$BUILD/classes.dex" "$BUILD/classes"

# add dex; keep resources.arsc and media stored and 4-byte aligned (what zipalign does)
python3 - "$BUILD/base.apk" "$BUILD/classes.dex" "$BUILD/aligned.apk" <<'PY'
import sys,zipfile,struct
src,dex,out=sys.argv[1:4]
zin=zipfile.ZipFile(src)
items=[(i.filename,zin.read(i.filename)) for i in zin.infolist()]+[('classes.dex',open(dex,'rb').read())]
with zipfile.ZipFile(out,'w') as z:
    for name,data in items:
        zi=zipfile.ZipInfo(name,date_time=(2026,1,1,0,0,0))
        stored=name=='resources.arsc' or name.endswith(('.png','.woff2'))
        zi.compress_type=zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
        if stored:
            n=(-(z.fp.tell()+30+len(name.encode())+6))%4
            zi.extra=struct.pack('<HHH',0xd935,2+n,4)+b'\0'*n
        z.writestr(zi,data)
PY

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" \
    -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Ugomoni Kota"
fi
javac -nowarn -cp "$CACHE/apksig.jar" -d "$BUILD/signer" "$HERE/tools/Sign.java"
mkdir -p "$ROOT/dist"
java --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.pkcs=ALL-UNNAMED \
  --add-exports java.base/sun.security.util=ALL-UNNAMED -cp "$CACHE/apksig.jar:$BUILD/signer" \
  Sign "$BUILD/aligned.apk" "$ROOT/dist/ugomoni-kota.apk" "$KEYSTORE" "$KEYSTORE_PASS" "$KEY_ALIAS"
"$AAPT2" dump badging "$ROOT/dist/ugomoni-kota.apk" | head -3
