"""Rebuild offline catalogue. pip install openpyxl; python scripts/import_cofid.py /path/to/CoFID.xlsx"""
import json,sys,openpyxl
from pathlib import Path
w=openpyxl.load_workbook(sys.argv[1],read_only=True,data_only=True)
def number(v):
 try: return float(v)
 except (ValueError,TypeError): return None # trace and unavailable remain unknown
minerals={r[0]:r for r in list(w['1.4 Inorganics'].values)[3:] if r[0]}
aliases={'chicken':'kip poulet','beef':'rund boeuf','pork':'varken porc','bread':'brood pain','potato':'aardappel pomme de terre','rice':'rijst riz','milk':'melk lait','egg':'ei oeuf','apple':'appel pomme','banana':'banaan banane','cheese':'kaas fromage','yogurt':'yoghurt yaourt','salmon':'zalm saumon','butter':'boter beurre','oat':'haver avoine','carrot':'wortel carotte','tomato':'tomaat tomate','onion':'ui oignon','water':'water eau','beer':'bier bière','strawberr':'aardbei fraise','lentil':'linzen lentilles','bean':'bonen haricots','spinach':'spinazie épinard','pear':'peer poire','mushroom':'champignon paddenstoel','cream':'room crème','flour':'bloem farine','sugar':'suiker sucre','oil':'olie huile','orange':'sinaasappel orange','tuna':'tonijn thon','pasta':'pasta pâtes','nut':'noten noix'}
out=[]
for r in list(w['1.3 Proximates'].values)[3:]:
 if not r[0]:continue
 m=minerals.get(r[0]); sodium=number(m[7]) if m else None
 nutrients={k:number(r[i]) for k,i in [('kcal',12),('protein',9),('carbs',11),('fat',10),('sugars',16),('fibre',25),('saturated',27)]}
 nutrients['salt']=sodium*2.5/1000 if sodium is not None else None
 nutrients['extra']={k:number(m[i]) for k,i in [('Potassium (mg)',8),('Calcium (mg)',9),('Iron (mg)',12)] if m and number(m[i]) is not None}
 out.append(dict(id='cofid:'+str(r[0]),name=r[1],basis='ml' if str(r[3]).startswith('Q') else 'g',nutrients=nutrients,source='CoFID 2021',sourceUrl='https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid',aliases=' '.join(v for k,v in aliases.items() if k in r[1].lower())))
Path('app/src/main/assets/cofid.json').write_text(json.dumps(out,ensure_ascii=False,separators=(',',':')))
print(f'Imported {len(out)} foods. CoFID OGL v3. Trace values are unknown, not zero.')
