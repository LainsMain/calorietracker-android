"""Build locally with the owner-held key. Usage: python scripts/build_release.py 1.1.0 3"""
import json,os,subprocess,sys
from pathlib import Path
credentials=Path.home()/'.local/share/calorietracker/signing/credentials.json'
values=json.loads(credentials.read_text())
env=os.environ|values|{'JAVA_HOME':'/usr/lib/jvm/java-17-openjdk','VERSION_NAME':sys.argv[1],'VERSION_CODE':sys.argv[2]}
subprocess.run(['./gradlew',':app:assembleRelease'],env=env,check=True)
