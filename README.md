# MillCar

Autós launcher az S24-re. Az autó (FM transzmitter) Bluetooth-jára csatlakozva megnyílik, a Waze-t osztott képernyőn maga mellé teszi, a panelen zenevezérlés van, a Haiku pedig hanggal irányítja a telefont.

## APK készítése
1. Töltsd fel ezt a mappát egy GitHub repóba (pl. `tpatrikjanos-create/millcar`), a `main` ágra.
2. Az Actions magától megépíti, és a **Releases** alatt megjelenik a `MillCar.apk`.
3. Töltsd le a telefonra, és telepítsd.

Minden build ugyanazzal a kulccsal van aláírva (`app/millcar.keystore`), így az új APK frissíti a régit.

## Első beállítás (⚙ a panelen)
1. **Alkalmazásinfó → ⋮ → Korlátozott beállítások engedélyezése.** Sideloadolt appnál enélkül nem kapcsolható be a 3. és a 4. lépés.
2. **Alap engedélyek:** Bluetooth, mikrofon, hely, névjegyek, hívás.
3. **Megjelenítés más appok felett:** e nélkül nem tud magától megnyílni BT-csatlakozáskor.
4. **Értesítés-hozzáférés:** a zene vezérléséhez és a „most szól” kijelzéshez.
5. **Kisegítő szolgáltatás → MillCar telefonvezérlés:** ezzel tud a Haiku gépelni és gombokat nyomni más appokban.
6. **Eszköz kiválasztása:** az FM transzmitter.
7. **Kulcsok:** Claude API kulcs (console.anthropic.com) és Todoist token (Todoist → Beállítások → Integrációk → Fejlesztő).
8. **Gombteszt:** megmutatja, milyen jelet küld a transzmitter egyes gombja.
9. Ha a transzmitter hívásgombjának hosszú nyomása megjelenik a gombtesztben („HANGGOMB”), akkor ez lesz az indítás: a **5. Alapértelmezett appok → Digitális asszisztens / hangbevitel** alatt, vagy az első felugró választónál válaszd a MillCart → **Mindig**.

## Panel
- ⏮ ⏯ ⏭: bármelyik lejátszót vezérli (később a MillMusicot is).
- 🎤 Haiku: hangparancs.
- ✎: diktált gondolat egyenesen a Todoist Inboxba.
- ⧉: a Waze visszahelyezése osztott képernyőre.

## Hangparancs példák
- „Következő szám” / „Tegyél valami nyugisat”
- „Írd fel, hogy holnap 9-kor hívjam a zöldségest”
- „Vigyél haza” / „Navigálj a Menzához”
- „Hívd fel Pistit” (előtte rákérdez)
- „Írd meg Messengeren Annának, hogy 10 perc és ott vagyok” (küldés előtt rákérdez)

## Feltevések
- S24, Android 14 vagy újabb; Waze telepítve (`com.waze`).
- Modell: `claude-haiku-5-5` (a beállításokban átírható).
- A transzmitter léptető gombjai a mindenkori lejátszónak mennek. Ezeket a MillCar csak akkor tudja elkapni (pl. dupla szünet), ha ő maga a lejátszó; ez a MillMusic-integrációval jön.
