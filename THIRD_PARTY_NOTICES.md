# Data and dependency notices

## CoFID 2021

The bundled `app/src/main/assets/cofid.json` is derived from Public Health England's
**McCance and Widdowson's Composition of Foods Integrated Dataset, 2021**.
Contains public sector information licensed under the **Open Government Licence v3.0**.
Crown copyright. The app is not endorsed by Public Health England or the UK government.

- Source: https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid
- Licence: https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/
- Changes: selected nutrient fields, sodium-to-salt conversion (×2.5), search aliases,
  JSON conversion, and preservation of trace/unavailable values as unknown.
- 2,887 foods. Nutrients are per 100 g, except group Q alcoholic beverages, per 100 ml.
- Reproduce with `python scripts/import_cofid.py CoFID.xlsx` (requires `openpyxl`).

## Open Food Facts

Product information fetched at runtime comes from **Open Food Facts**.
Database: Open Database Licence (ODbL) 1.0. Individual database contents:
Database Contents Licence. Product pictures, if reused, have separate CC BY-SA terms;
this application does not bundle Open Food Facts product images.

- https://world.openfoodfacts.org/terms-of-use
- https://opendatacommons.org/licenses/odbl/1-0/

OFF records retain provider identity and a product source URL. The OFF database is not
bundled or republished in this repository. Redistributors of a derived OFF database
must meet its attribution and share-alike obligations. Personal diary exports are
not a public food-database publishing feature. Verify the applicable terms before
publishing bulk exports or combining third-party databases.

## Libraries

Kotlin, AndroidX/Jetpack, Room, Compose, WorkManager, DataStore, CameraX, Health Connect,
Hilt/Dagger, Guava, and OkHttp are used under their respective Apache-2.0 notices.
ML Kit's bundled barcode scanner is distributed under Google's applicable SDK terms.
See Gradle dependency coordinates for the exact versions and upstream licence texts.
