"""Create a version manifest from the exact signed APK that will be published."""
import hashlib,json,sys
from pathlib import Path
apk=Path(sys.argv[1]);version=sys.argv[2];code=int(sys.argv[3])
assert code>0 and apk.exists()
manifest={'versionCode':code,'versionName':version,'apkUrl':f'https://github.com/LainsMain/calorietracker-android/releases/download/v{version}/CalorieTracker-{version}.apk','sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'bytes':apk.stat().st_size,'notes':Path('docs/RELEASE_NOTES.md').read_text(),'minSdk':29}
apk.with_name('update.json').write_text(json.dumps(manifest,indent=2)+'\n')
apk.with_name('SHA256SUMS').write_text(f"{manifest['sha256']}  {apk.name}\n")
