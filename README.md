# Femboy Skins

Mod do Minecrafta (Fabric, tylko klient), który zamienia skiny wszystkich graczy
na skiny femboyów, e-girls i catboyów, dokłada dodatki 3D (uszy, ogony, skrzydła, aureolki, różki,
obroże…), głaskanie
oraz **okno wyboru wyglądu z żywym podglądem**. Wszystko widoczne tylko u ciebie.

Zbudowany i sprawdzony na **Minecraft 1.21.11** (Fabric). Inne wersje wymagają przeniesienia
kodu — patrz [Budowanie na różne wersje](#budowanie-na-różne-wersje).

<p align="center">
  <img src="docs/img/gallery-big.png" alt="Przykładowe skiny" width="860">
</p>

## Okno wyboru wyglądu (GUI)

<p align="center">
  <img src="docs/img/game-gui-1.png" alt="Okno wyboru wyglądu – zakładka Dodatki" width="860">
</p>
<p align="center">
  <img src="docs/img/game-gui-0.png" alt="Zakładka Skin" width="420">
  <img src="docs/img/game-gui-2.png" alt="Zakładka Zestawy" width="420">
</p>

Wciśnij **G** (albo wpisz `/femboy gui`) i ustaw wszystko myszką, bez komend:

- **Po lewej – kogo zmieniasz.** Klikasz, żeby zaznaczyć dowolną liczbę osób: jedną,
  kilka, „Ja” albo **Wszyscy**. Każda zmiana leci do wszystkich zaznaczonych naraz –
  więc możesz dać np. całemu serwerowi ten sam skin jednym kliknięciem.
- **W środku – żywy podgląd 3D** zaznaczonej postaci: od razu widać skin i dodatki,
  obraca się za kursorem, a każda zmiana jest widoczna natychmiast.
- **Po prawej – cztery zakładki z mnóstwem opcji** (za długą listę przewijasz kółkiem):
  - **Skin** – styl (`auto` / `femboy` / `egirl` / `catboy` / `off`), przeglądanie całej puli
    skinów (wybierasz konkretny skin strzałkami – też skin, który nosi ktoś inny), „losowy”
    albo „daj wszystkim ten sam”.
  - **Dodatki** – 10 miejsc: uszy, ogon, szyja, włosy, twarz, plecy, ręce, nogi, łapki,
    spódniczka (każde strzałkami) i „bez dodatków”.
  - **Zestawy** – 10 gotowych strojów jednym kliknięciem, „losowy strój”, „według trybu”.
  - **Kolory** – futro i kolor dodatków z całej palety, „futro = kolor włosów”, „losowy”.
- Na dole: **Losuj od nowa** i **Wyczyść** – dla wszystkich zaznaczonych.

To samo, co komendy `/femboy …`, tylko wygodniej i z podglądem. Wszystkie zdjęcia z gry w tym
README zrobił sam mod (`./gradlew runShots`, patrz [Zrzuty z gry](#zrzuty-z-gry)).

## Skiny

Do wyboru masz ok. **450 wbudowanych skinów** (po ~150: femboy, e-girl i **catboy** – najpopularniejsze
z tagu [catboy na laby.net](https://laby.net/skins/tag/catboy), bez powtórek z pozostałych pul; od razu w jarze – komu dasz sam
plik `.jar`, ten ma je bez kopiowania żadnych folderów), **generator**, który sam rysuje wygląd
(włosy, oczy, bluza / sweter / mundurek, spódniczka, podkolanówki, buty, dodatki), albo jedno
i drugie naraz.

- `/femboy source skins` – tylko gotowe skiny
- `/femboy source generated` – tylko generator
- `/femboy source mix` – pół na pół

`/femboy mode femboy | egirl | catboy | mix` wybiera, kto jaki skin dostaje (`mix` = po równo ze
wszystkich trzech). Każdy gracz dostaje skin wylosowany według swojego UUID. Dopóki w puli są wolne skiny, dwie osoby
nie mają tego samego. Ten sam gracz zwykle ma ten sam skin, dopóki nie zrobisz rerolla
(jeśli jego skin nosi już ktoś inny, dostaje następny wolny).

**femboy**

<p align="center">
  <img src="docs/img/femboy-row2.png" alt="Skiny femboy" width="900">
</p>

**e-girl**

<p align="center">
  <img src="docs/img/egirl-row2.png" alt="Skiny e-girl" width="900">
</p>

**catboy**

<p align="center">
  <img src="docs/img/catboy-row.png" alt="Skiny catboy" width="900">
</p>

## Pat pat (głaskanie)

<p align="center">
  <img src="docs/img/petpet-hand.png" alt="Klatki ręki pat pat" width="640">
</p>

Trzymaj **R**, celując w kogoś (do 6 bloków): nad jego głową pojawia się ręka z GIF-a petpet
i klepie go, dopóki nie puścisz. Głowa ściska się w rytm klepania jak obrazek w GIF-ie,
a przy każdym klepnięciu piszczy gumowa zabawka. Działa też na moby (zwierzaki!) – im ściska się całe ciało.
Klawisz zmienisz w Ustawienia → Sterowanie → Przypisane klawisze → Femboy Skins (np. gdy masz Irisa z R na przeładowanie shaderów). Widać to tylko u ciebie (mod działa po stronie klienta).

## Dodatki (uszy, ogony, skrzydła, aureolki…)

Wszystkie 10 gotowych zestawów w grze:

<p align="center">
  <img src="docs/img/game-kits.png" alt="10 zestawów dodatków w grze" width="900">
</p>

Z tyłu – skrzydła, ogony, różki, aureolka, tiara:

<p align="center">
  <img src="docs/img/game-back.png" alt="Dodatki z tyłu" width="900">
</p>

Każdy gracz może mieć dodatki 3D – siedzą na postaci i ruszają się razem z nią:

- **uszy** (`ears`): kocie `cat`, lisie `fox`, wilcze `wolf`, oklapnięte psie `puppy`, królicze `bunny`,
  misie `bear`, mysie `mouse`
- **ogon** (`tail`): koci `cat`, puszysty lisi `fox` i wilczy `wolf`, psi `puppy`, króliczy puszek `bunny`,
  diabelski `devil`
- **szyja** (`neck`): obroża z dzwoneczkiem `bell`, obroża z kostką `tag`, choker z serduszkiem `heart`,
  choker z kokardką `bow`, z kłódką `lock`, z ćwiekami `spikes`
- **włosy** (`head`): kokardka `bow`, spinka-serduszko `clip`, aureolka `halo`, diabelskie różki `horns`,
  tiara `tiara`, kwiatek `flower`
- **twarz** (`face`): okulary `glasses`, maseczka `mask`
- **plecy** (`back`): anielskie skrzydła `angel`, skrzydła nietoperza `bat`, skrzydła motyla `butterfly` –
  trzepoczą, przy bieganiu i głaskaniu mocniej, przy skradaniu się składają
- **ręce** (`arms`): ocieplacze `warmers`, ocieplacze w paski `stripes`
- **nogi** (`legs`): zakolanówki `socks`, zakolanówki w paski `stripes`
- **łapki** (`paws on|off`) i **plisowana spódniczka** (`skirt on|off`)

Do tego nowe uszy: misie `bear` i mysie `mouse`; ogon diabełka `devil`; chokery z kłódką `lock`
i z ćwiekami `spikes`.

Gotowe zestawy: `catboy`, `puppy`, `fox`, `wolf`, `bunny`, `femboy`, `bear` (miś), `devil` (diabełek),
`angel` (aniołek), `fairy` (wróżka). Futro (`fur`) ma domyślnie kolor
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

- `/femboy gui` – okno wyboru wyglądu z podglądem (albo klawisz **G**)
- `/femboy on | off | toggle` – włącz / wyłącz
- `/femboy mode femboy | egirl | catboy | mix` – kto jaki skin dostaje (mix = po równo ze wszystkich)
- `/femboy source skins | generated | mix` – pobrane skiny / generator / jedno i drugie
- `/femboy reroll [gracz]` – nowe losowanie dla wszystkich albo jednej osoby
- `/femboy set <gracz> femboy | egirl | catboy | auto | off` – wybór dla jednej osoby (`off` = oryginalny skin)
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

- **Dla wszystkich** (w jarze): wrzuć PNG (64x64 albo 64x32) do `skins/femboy/`, `skins/egirl/` albo `skins/catboy/`
  w projekcie i zbuduj mod – wszystko z tych folderów trafia do jara. Usunięty stąd skin znika z jara.
- **Tylko u siebie**: wrzuć PNG do `config/femboy/skins/<femboy|egirl|catboy>/`
  w folderze gry i wpisz `/femboy reload`. Dołączą do puli razem z wbudowanymi.

Model chudy / szeroki: słowo `slim` / `wide` w nazwie pliku, a bez niego mod rozpoznaje go
z pikseli (chudy, jeśli miejsca, których chudy model nie używa, są w większości puste).

Ustawienia: `config/femboy.json`.

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

### Budowanie na różne wersje

**Stan na teraz: kod kompiluje się na 1.21.11.** Build jest przygotowany pod inne wersje
(numery podaje się z linii poleceń), ale sam kod jeszcze się na nich nie zbuduje bez zmian —
patrz niżej.

```
./gradlew build -Pmc=1.21.8 -Pyarn=1.21.8+build.1 -Pfabricapi=0.136.1+1.21.8
```

Jary różnych wersji mają inną nazwę (`femboy-mc1.21.8-<wersja>.jar`), więc się nie nadpisują,
a każdy dostaje w `fabric.mod.json` zakres zgodności dla swojej linii. Skrypt
`tools/build-all.sh` dobiera numery z `meta.fabricmc.net` + Modrintha i buduje serię do `dist/`
(nie przerywa się na błędzie – wypisuje, czego nie udało się skompilować).

**Dlaczego to nie jest „za darmo”:** mod wczepia się mixinami w wewnętrzne klasy renderowania,
a Mojang przebudował je nawet w obrębie 1.21.x. Próba buildu na **1.21.8** kończy się błędami
kompilacji – tam nie ma pakietów `net.minecraft.client.render.state` / `…render.command`,
`GameProfile` ma inne metody (`getId()`/`getName()` zamiast `id()`/`name()`), `PlayerEntityRenderer`
nie jest generyczny, a API wejścia GUI (`Click`, `KeyInput`) i `RenderPipelines` wygląda inaczej.
Każda starsza wersja wymaga więc osobnych wariantów tych klas (np. przez *source sety* albo
refleksję). Infrastruktura do tego jest gotowa; samo przeniesienie kodu to oddzielna robota.

### Testy

`./gradlew test` – testy logiki bez uruchamiania gry: czy geometria wszystkich dodatków mieści się
w teksturze 128×128 i nic na siebie nie nachodzi, czy każdy dodatek ma model i widoczne piksele,
zapis/odczyt strojów, losowanie (deterministyczne, zawsze ≥ 2 dodatki, każdy nowy dodatek wypada),
kolory, animacje (skończone wartości) oraz czy wszystkie skiny w `skins/` to poprawne PNG zgodne
z listami z laby.net i bez powtórek między pulami.

### Zrzuty z gry

`./gradlew runShots` uruchamia grę w świecie `run/saves/Femboy Shots` (wystarczy skopiować tam
dowolny świat 1.21.11), sama ubiera postać w kolejne zestawy, robi zdjęcia z przodu, z tyłu i okna
GUI do `docs/img/game/` i się zamyka – niczego nie trzeba klikać. W zwykłej grze ten tryb jest
wyłączony (włącza go tylko flaga `-Dfemboy.shots`).

Potem `python3 -I tools/make_game_gallery.py` składa z nich galerie `docs/img/game-*.png`.
Rzędy skinów rysuje bez uruchamiania gry `python3 -I tools/render_skins.py` (wprost z tekstur).
