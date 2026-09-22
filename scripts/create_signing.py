"""Create an owner-local release identity, never in the repository. Does not print secrets."""
import json,os,secrets,subprocess
from pathlib import Path
folder=Path.home()/'.local/share/calorietracker/signing'
folder.mkdir(parents=True,exist_ok=True);folder.chmod(0o700)
credentials=folder/'credentials.json'
if credentials.exists():
 print('Existing release signing identity retained.')
else:
 password=secrets.token_urlsafe(36)
 values={'SIGNING_STORE_FILE':str(folder/'release.jks'),'SIGNING_STORE_PASSWORD':password,'SIGNING_KEY_PASSWORD':password}
 env=os.environ|{'CT_SIGNING_PASSWORD':password}
 subprocess.run(['/usr/lib/jvm/java-17-openjdk/bin/keytool','-genkeypair','-keystore',values['SIGNING_STORE_FILE'],'-storepass:env','CT_SIGNING_PASSWORD','-keypass:env','CT_SIGNING_PASSWORD','-alias','calorietracker','-keyalg','RSA','-keysize','4096','-validity','10000','-dname','CN=CalorieTracker Release, OU=Android, O=LainsMain, C=BE'],env=env,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 credentials.write_text(json.dumps(values));credentials.chmod(0o600);Path(values['SIGNING_STORE_FILE']).chmod(0o600)
 print('Release identity created in ~/.local/share/calorietracker/signing; back up this directory securely.')
