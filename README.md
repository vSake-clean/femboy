# Femboy Skins

Mod do Minecrafta (Fabric 1.21.11, tylko klient), który zamienia skiny wszystkich graczy
na skiny femboyów i e-girls. Zmiana jest widoczna tylko u ciebie.

## Skąd są skiny

- **Domyślnie: prawdziwe skiny z laby.net** – ok. 150 z tagu [Femboy](https://laby.net/skins/tag/Femboy)
  i 150 z tagu [egirl](https://laby.net/skins/tag/egirl). Są wbudowane w jar: komu dasz sam plik
  `.jar`, ten ma je od razu, bez kopiowania żadnych folderów.
- **Generator** – mod sam rysuje wygląd (włosy, oczy, bluza / sweter / mundurek, spódniczka,
  podkolanówki, buty, dodatki): `/femboy source generated`. `/femboy source mix` = pół na pół.

Każdy gracz dostaje skin wylosowany według swojego UUID. Dopóki w puli są wolne skiny, dwie osoby
nie mają tego samego. Ten sam gracz zwykle ma ten sam skin, dopóki nie zrobisz rerolla
(jeśli jego skin nosi już ktoś inny, dostaje następny wolny).

## Pat pat (głaskanie)

Trzymaj **R**, celując w kogoś (do 6 bloków): nad jego głową pojawia się ręka z GIF-a petpet
i klepie go, dopóki nie puścisz. Głowa ściska się w rytm klepania jak obrazek w GIF-ie,
a przy każdym klepnięciu piszczy gumowa zabawka. Działa też na moby (zwierzaki!) – im ściska się całe ciało.
Klawisz zmienisz w Ustawienia → Sterowanie → Przypisane klawisze → Femboy Skins (np. gdy masz Irisa z R na przeładowanie shaderów). Widać to tylko u ciebie (mod działa po stronie klienta).

## Dodatki (uszy, ogony, obroże…)

Każdy gracz może mieć dodatki 3D – siedzą na postaci i ruszają się razem z nią:

- **uszy** (`ears`): kocie `cat`, lisie `fox`, wilcze `wolf`, oklapnięte psie `puppy`, królicze `bunny`
- **ogon** (`tail`): koci `cat`, puszysty lisi `fox` i wilczy `wolf`, psi `puppy`, króliczy puszek `bunny`
- **szyja** (`neck`): obroża z dzwoneczkiem `bell`, obroża z kostką `tag`, choker z serduszkiem `heart`,
  choker z kokardką `bow`
- **włosy** (`head`): kokardka `bow`, spinka-serduszko `clip`
- **nogi** (`legs`): zakolanówki `socks`, zakolanówki w paski `stripes`
- **łapki** (`paws on|off`) i **plisowana spódniczka** (`skirt on|off`)

Gotowe zestawy: `catboy`, `puppy`, `fox`, `wolf`, `bunny`, `femboy`. Futro (`fur`) ma domyślnie kolor
włosów ze skina (`auto`), a reszta (`color`) – wybrany kolor: `pink`, `black`, `lightblue`, `ff88cc`…
Uszy co kilka sekund strzygą, przy głaskaniu kładą się po sobie, koci ogon się kołysze, psi merda
(najmocniej przy bieganiu i głaskaniu), a spódniczka rusza się z nogami.

Tryby (`/femboy acc mode`):
- `random` (domyślny) – każdy dostaje coś losowego z włączonych zestawów
- `same` – wszyscy noszą ten sam strój (`/femboy acc all …`), futro każdy ma w kolorze swoich włosów
- `me` – dodatki ma tylko twoja postać (i gracze, którym ustawisz strój)

Przykłady:
- `/femboy acc me catboy` – ty jako catboy
- `/femboy acc me tail fox` – zmień sobie tylko ogon, `/femboy acc me neck none` – zdejmij obrożę
- `/femboy acc me fur white`, `/femboy acc me color black` – kolory
- `/femboy acc all puppy` i `/femboy acc mode same` – wszyscy jako puppyboye
- `/femboy acc player Nick bunny` – jedna osoba jako króliczek
- `/femboy acc kits wolf off` – bez wilków w losowaniu
- `/femboy acc items` – lista wszystkich dodatków i kolorów

Jak wszystko w tym modzie, dodatki widzisz tylko ty. Kolega z modem widzi to, co sam ustawi u siebie
(np. `/femboy acc player <twój nick> catboy`).

## Komendy

- `/femboy on | off | toggle` – włącz / wyłącz
- `/femboy mode femboy | egirl | mix` – kto jaki skin dostaje (mix = losowo pół na pół)
- `/femboy source skins | generated | mix` – pobrane skiny / generator / jedno i drugie
- `/femboy reroll [gracz]` – nowe losowanie dla wszystkich albo jednej osoby
- `/femboy set <gracz> femboy | egirl | auto | off` – wybór dla jednej osoby (`off` = oryginalny skin)
- `/femboy reset [gracz]` – usuwa ustawienia graczy
- `/femboy self on|off` – czy zmieniać też twój skin
- `/femboy tab on|off` – główki na liście graczy (TAB)
- `/femboy layers on|off` – pokazuj warstwy skina (włosy 3D, spódniczka), nawet gdy gracz je wyłączył
- `/femboy pat on|off` – głaskanie pod klawiszem, `/femboy patsound on|off` – piszczenie
- `/femboy acc on|off` – dodatki 3D (uszy, ogony, obroże…); `/femboy acc` pokazuje wszystkie komendy dodatków
- `/femboy acc mode random | same | me` – losowo każdemu / każdemu to samo / tylko ja
- `/femboy acc me | all | player <gracz> …` – strój: `<zestaw>`, `random`, `none`, `auto`,
  `<miejsce> <dodatek>`, `fur <kolor>`, `color <kolor>`
- `/femboy acc kits [<zestaw> on|off]`, `/femboy acc reroll [gracz]`, `/femboy acc items`
- `/femboy list`, `/femboy status` – kto co ma (z którego pliku) / ustawienia i liczba skinów
- `/femboy export [gracz]` – zapisuje skin do PNG (`config/femboy/export/`), możesz go ustawić jako swój
- `/femboy folder`, `/femboy reload` – folder ze skinami / wczytaj pliki od nowa

## Własne skiny

- **Dla wszystkich** (w jarze): wrzuć PNG (64x64 albo 64x32) do `skins/femboy/` albo `skins/egirl/`
  w projekcie i zbuduj mod – wszystko z tych folderów trafia do jara. Usunięty stąd skin znika z jara.
- **Tylko u siebie**: wrzuć PNG do `config/femboy/skins/femboy/` albo `config/femboy/skins/egirl/`
  w folderze gry i wpisz `/femboy reload`. Dołączą do puli razem z wbudowanymi.

Model chudy / szeroki: słowo `slim` / `wide` w nazwie pliku, a bez niego mod rozpoznaje go
z pikseli (chudy, jeśli miejsca, których chudy model nie używa, są w większości puste).

Ustawienia: `config/femboy.json`.

## Skiny z laby.net

- Lista z linkami do stron i plików: `skins/laby-femboy.json` i `skins/laby-egirl.json`
  (w jarze w `assets/femboy/skins/`).
- Nazwa pliku to `<hash>_slim.png` albo `<hash>_wide.png`. Model jest z laby.net, z jedną poprawką:
  32 skiny oznaczone tam jako wide są narysowane pod chudy model, więc dostały `_slim`
  (w JSON `laby_slim` to oryginalna wartość z laby.net).
- To skiny innych ludzi z laby.net – lista wyżej mówi, skąd który jest.

## Ręka i dźwięk pat pat

- Ręka: klatki z generatora petpet (mem „Pet the X”), wzięte z paczki
  [pet-pet-gif](https://pypi.org/project/pet-pet-gif/) (MIT, camprevail) – 5 klatek po 112 px
  w `assets/femboy/textures/petpet/hand.png`. Animacja jak w generatorze: 10 klatek, ściśnięcie
  najmocniejsze w 5., tylko trochę wolniej (30 ms zamiast 20 ms na klatkę).
- Dźwięk: 4 piśnięcia wycięte z „Squeeze Toy” autorstwa survivalzombie
  ([freesound.org/s/240015](https://freesound.org/people/survivalzombie/sounds/240015/), CC0),
  mono, w `assets/femboy/sounds/petpet/`.

## Budowanie

`./gradlew build --offline` – gotowy jar (razem ze skinami z `skins/`) sam ląduje w modach instancji
Fabulously Optimized (ścieżka `fo_mods_dir` w `gradle.properties`). Ten sam plik możesz dać innym:
`build/libs/femboy-<wersja>.jar` (potrzebują jeszcze tylko Fabric API).
