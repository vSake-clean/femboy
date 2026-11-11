package pl.vsake.femboy;

import java.util.HashMap;
import java.util.Map;

/**
 * Język okna moda i komunikatów: po polsku, gdy gra jest ustawiona na polski, w każdym innym języku po angielsku.
 * Teksty w kodzie są polskie; {@link #tr} podmienia je na angielskie ze słownika poniżej (brak wpisu = tekst bez zmian).
 */
public final class Lang {
	private Lang() {
	}

	/** Ustawiane co tick z języka gry (FemboyMod). Domyślnie polski. */
	public static volatile boolean polish = true;

	private static final Map<String, String> EN = new HashMap<>();

	public static String tr(String pl) {
		if (polish || pl == null) return pl;
		String en = EN.get(pl);
		return en != null ? en : pl;
	}

	/** Czy słownik zna ten tekst (do testów). */
	public static boolean known(String pl) {
		return EN.containsKey(pl);
	}

	private static void e(String pl, String en) {
		EN.put(pl, en);
	}

	static {
		// ---- zakładki i dół okna ----
		e("Skin", "Skin");
		e("Dodatki", "Addons");
		e("Zestawy", "Kits");
		e("Kolory", "Colors");
		e("Odgłosy", "Sounds");
		e("Zabawa", "Fun");
		e("Animacje", "Animations");
		e("Zabawki", "Toys");
		e("Szafa", "Wardrobe");
		e("Klawisze", "Keys");
		e("Losuj od nowa", "Reroll");
		e("Wyczyść", "Reset");
		e("klik = zaznacz; zmiany dotyczą wszystkich zaznaczonych", "click = select; changes apply to everyone selected");
		e("Wszyscy", "Everyone");
		e("Ja (", "Me (");
		e("oryginał", "original");
		e("pogłaskany ", "patted ");
		e("wejdź bliżej, by zobaczyć podgląd", "come closer to see the preview");
		e(" (jeszcze nie widziany)", " (not seen yet)");
		e(" (generowany)", " (generated)");
		e("wł.", "on");
		e("wył.", "off");
		// ---- skin ----
		e("Pula: ", "Pool: ");
		e("losowy skin", "random skin");
		e("Losowy skin", "Random skin");
		e("Daj wszystkim ten", "Use for all");
		// ---- dodatki ----
		e("Uszy", "Ears");
		e("Ogon", "Tail");
		e("Szyja", "Neck");
		e("Włosy", "Hair");
		e("Twarz", "Face");
		e("Plecy", "Back");
		e("Ręce", "Arms");
		e("Łapki", "Paws");
		e("Spódnica", "Skirt");
		e("Efekt", "Effect");
		e("W ręce", "In hand");
		e("Bez dodatków", "No accessories");
		e("kocie uszy", "cat ears");
		e("lisie uszy", "fox ears");
		e("wilcze uszy", "wolf ears");
		e("psie uszy", "puppy ears");
		e("królicze uszy", "bunny ears");
		e("misie uszy", "bear ears");
		e("mysie uszy", "mouse ears");
		e("kocie uszy z dzwoneczkami", "cat ears with bells");
		e("koci ogon", "cat tail");
		e("lisi ogon", "fox tail");
		e("wilczy ogon", "wolf tail");
		e("psi ogonek", "puppy tail");
		e("króliczy puszek", "bunny puff");
		e("diabelski ogonek", "devil tail");
		e("obroża z dzwoneczkiem", "collar with a bell");
		e("obroża z kostką", "collar with a tag");
		e("choker z serduszkiem", "heart choker");
		e("choker z kokardką", "bow choker");
		e("choker z kłódką", "lock choker");
		e("choker z ćwiekami", "spiked choker");
		e("obroża z identyfikatorem", "collar with a name tag");
		e("obroża z trzema dzwoneczkami", "collar with three bells");
		e("szalik", "scarf");
		e("kokardka we włosach", "hair bow");
		e("spinka-serduszko", "heart hair clip");
		e("aureolka", "halo");
		e("diabelskie różki", "devil horns");
		e("tiara", "tiara");
		e("kwiatek we włosach", "hair flower");
		e("kokardka z dzwoneczkiem", "bow with a bell");
		e("okulary", "glasses");
		e("maseczka", "face mask");
		e("anielskie skrzydła", "angel wings");
		e("skrzydła nietoperza", "bat wings");
		e("skrzydła motyla", "butterfly wings");
		e("ocieplacze na ręce", "arm warmers");
		e("ocieplacze w paski", "striped arm warmers");
		e("łapki", "paws");
		e("plisowana spódniczka", "pleated skirt");
		e("serduszka nad głową", "hearts above the head");
		e("iskierki", "sparkles");
		e("myszka na wędce", "mouse on a rod");
		e("piórko na wędce", "feather on a rod");
		// ---- zestawy ----
		e("catboy", "catboy");
		e("puppyboy", "puppyboy");
		e("lisek", "fox");
		e("wilczek", "wolf");
		e("króliczek", "bunny");
		e("femboy", "femboy");
		e("miś", "bear");
		e("diabełek", "devil");
		e("aniołek", "angel");
		e("wróżka", "fairy");
		e("Losowy strój", "Random outfit");
		e("Według trybu (auto)", "By mode (auto)");
		// ---- kolory ----
		e("Futro: ", "Fur: ");
		e("Dodatki: ", "Accessories: ");
		e("Futro = kolor włosów", "Fur = hair color");
		e("Kolor dodatków losowy", "Random accessory color");
		e("jak włosy", "like hair");
		e("losowy", "random");
		e("różowy", "pink");
		e("mocny róż", "hot pink");
		e("czerwony", "red");
		e("pomarańczowy", "orange");
		e("żółty", "yellow");
		e("miętowy", "mint");
		e("zielony", "green");
		e("błękitny", "light blue");
		e("niebieski", "blue");
		e("lawendowy", "lavender");
		e("fioletowy", "purple");
		e("czarny", "black");
		e("biały", "white");
		e("szary", "gray");
		e("srebrny", "silver");
		e("brązowy", "brown");
		e("kremowy", "cream");
		e("rudy", "ginger");
		e("złoty", "golden");
		// ---- odgłosy ----
		e("Ogólnie: ", "Overall: ");
		e("wyciszone", "muted");
		e("Głośność: ", "Volume: ");
		e("wyciszony", "muted");
		e("Odcisz", "Unmute");
		e("Wycisz", "Mute");
		e("Odgłos: ", "Sound: ");
		e("> naciśnij klawisz <", "> press a key <");
		e("Klawisz: ", "Key: ");
		e("▶ Zagraj", "▶ Play");
		e("Włącz wszystkie (", "Enable all (");
		e("tylko", "only");
		e("Miau", "Meow");
		e("Hau", "Woof");
		e("Mruczenie", "Purr");
		e("Syczenie", "Hiss");
		e("Warczenie", "Growl");
		e("Skomlenie", "Whine");
		e("Dyszenie", "Pant");
		e("Lisek", "Fox");
		e("Pisk", "Squeak");
		// ---- zabawa ----
		e("Clicker", "Clicker");
		e("Kliknij", "Click");
		e("Tylko wybrane osoby", "Only chosen people");
		e("Reaguję na", "I react to");
		e("Smycz", "Leash");
		e("Daję się ciągnąć", "Let myself be pulled");
		e("Mogą mnie ciągnąć", "May pull me");
		e("Widzę smycz na sobie", "I see the leash on me");
		e("Puść smycz (", "Release leash (");
		e("Weź na smycz: ", "Leash: ");
		e("Smycz: wybierz gracza z listy", "Leash: pick a player from the list");
		e("We dwoje", "Together");
		e("Boop w nosek", "Nose boop");
		e("Za rękę", "Hold hands");
		e("Na ręce", "Carry");
		e("Na barana", "Piggyback");
		e("Wybierz gracza z listy", "Pick a player from the list");
		e("Puść: ", "Let go: ");
		e("Zgódź się na noszenie: ", "Agree to be carried: ");
		e("Mogą mnie nosić bez pytania", "May carry me without asking");
		e("Emotki nad głową", "Emotes above the head");
		e(" – nick pod nickiem:", ", one nick per line:");
		e("wpisz nick albo kliknij gracza z listy", "type a nick or click a player in the list");
		e("Serduszko", "Heart");
		e("Pytajnik", "Question mark");
		e("Wykrzyknik", "Exclamation mark");
		e("Złość", "Anger");
		e("Nutka", "Music note");
		e("Zzz", "Zzz");
		e("Gwiazdka", "Star");
		e("Łezka", "Tear");
		// ---- animacje ----
		e("Siad", "Sit");
		e("Ugniatanie łapkami", "Kneading");
		e("Ugniatanie", "Knead");
		e("Bochenek", "Loaf");
		e("Brzuszek do głaskania", "Belly up");
		e("Brzuszek", "Belly");
		e("Drzemka w kłębku", "Curled-up nap");
		e("Drzemka", "Nap");
		e("Prosi (łapki w górze)", "Beg (paws up)");
		e("Prosi", "Beg");
		e("Taniec", "Dance");
		e("Przeciąganie", "Stretch");
		e("Machanie", "Wave");
		e("Daj łapę", "Give paw");
		e("Mycie łapki", "Grooming");
		e("Czajenie się przed skokiem", "Pounce wiggle");
		e("Czajenie się", "Wiggle");
		e("Otrzepanie się", "Shake off");
		e("Otrzepanie", "Shake");
		e("Ocieranie główką", "Nuzzle");
		e("Ocieranie", "Nuzzle");
		e("■ Stop", "■ Stop");
		e("Same z siebie", "Automatic");
		e("Bochenek i przeciąganie", "Loaf and stretching");
		e("Mruganie", "Blinking");
		e(" (ruch albo ten sam klawisz kończy)", " (move or press the key again to stop)");
		e("Kółko animacji", "Animation wheel");
		e("Przerwij", "Stop");
		e("trwa do ruchu", "lasts until you move");
		e("krótka", "short");
		e("kliknij pozę", "click a pose");
		e("puść na pozie", "release on a pose");
		// ---- zabawki ----
		e("Wskaźnik laserowy (trzymaj klawisz)", "Laser pointer (hold the key)");
		e("Idę za kropką", "I follow the dot");
		e("Osoby, za których zabawkami chodzę", "People whose toys I follow");
		e("Kłębek włóczki", "Yarn ball");
		e("Rzuć kłębek", "Throw yarn ball");
		e("Gonię cudze kłębki", "I chase others' yarn");
		e("Widzę cudze zabawki", "I see others' toys");
		e("Efekty", "Effects");
		e("Ślady łapek (śnieg, piasek)", "Paw prints (snow, sand)");
		e("Dzwoneczki przy chodzeniu", "Bells while walking");
		e("Mruczenie głaskanego kota", "Purring when patted");
		e("Serduszka przy głaskaniu", "Hearts when patted");
		e("Reakcje uszu i ogona", "Ear and tail reactions");
		e("Gonisz kłębek (ruch albo kucnięcie = koniec)", "Chasing the yarn (move or sneak to stop)");
		e("Idziesz za kropką (ruch = przerwa, kucnięcie = stoisz)", "Following the dot (move to pause, sneak to stand)");
		// ---- szafa ----
		e("Nazwa stroju", "Outfit name");
		e("Zapisz strój: ", "Save outfit: ");
		e("Razem ze skinem: ", "Include skin: ");
		e("tak", "yes");
		e("nie", "no");
		e("Stroje ", "Outfits ");
		e(" – klawisz ", ", key ");
		e("Szafa jest pusta", "The wardrobe is empty");
		e("Strój ", "Outfit ");
		e("Strój: ", "Outfit: ");
		e("Szafa jest pusta – zapisz strój w oknie moda (zakładka Szafa)", "The wardrobe is empty. Save an outfit in the mod window (Wardrobe tab)");
		// ---- klawisze ----
		e("Ogólne", "General");
		e("Emotki", "Emotes");
		e("Następny strój z szafy", "Next wardrobe outfit");
		e("Okno moda", "Mod window");
		e("Pat pat (trzymaj)", "Pat pat (hold)");
		e("Smycz (celuj w gracza)", "Leash (aim at a player)");
		e("Przytulas (trzymaj przy kimś)", "Hug (hold near someone)");
		e("Za ręce (celuj w gracza)", "Hold hands (aim at a player)");
		e("Noszenie na rękach / zgoda", "Carry in arms / agree");
		e("Na barana / zgoda", "Piggyback / agree");
		e("Kółko animacji (przytrzymaj)", "Animation wheel (hold)");
		e("Wskaźnik laserowy (trzymaj)", "Laser pointer (hold)");
		e("Rzuć kłębek włóczki", "Throw yarn ball");
		e("Boop w nosek (celuj w gracza)", "Nose boop (aim at a player)");
		e("♥ Serduszko", "♥ Heart");
		e("? Pytajnik", "? Question mark");
		e("! Wykrzyknik", "! Exclamation mark");
		e("# Złość", "# Anger");
		e("♪ Nutka", "♪ Music note");
		e("z Zzz", "z Zzz");
		e("✦ Gwiazdka", "✦ Star");
		e("● Łezka", "● Tear");
		// ---- komunikaty nad paskiem ----
		e("Wyceluj w gracza", "Aim at a player");
		e("Za daleko – puszczasz", "Too far, you let go");
		e("Schodzisz", "You get down");
		e("Puszczasz rękę", "You let go of the hand");
		e("Stawiasz na ziemi", "You put them down");
		e("Puszczasz", "You let go");
		e("Przytulasz: ", "Hugging: ");
		e("Trzymasz za rękę: ", "Holding hands with: ");
		e("Prosisz o zgodę na noszenie na rękach: ", "Asking to carry in arms: ");
		e("Prosisz o zgodę na noszenie na barana: ", "Asking to carry piggyback: ");
		e("Dajesz się nieść – nie sterujesz, dopóki cię nie puści (", "You are being carried. No control until released (");
		e(" = zejdź)", " = get down)");
		e(" cię przytula", " hugs you");
		e(" bierze cię za rękę", " takes your hand");
		e(" chce cię ponieść ", " wants to carry you ");
		e("na rękach", "in arms");
		e("na barana", "piggyback");
		e(" – naciśnij ", ", press ");
		e(", żeby się zgodzić", " to agree");
		e("Puszczasz smycz", "You release the leash");
		e("Smycz się zerwała", "The leash snapped");
		e("100%", "100%");
		e("Wyceluj w gracza, żeby wziąć go na smycz", "Aim at a player to leash them");
		e("Prowadzisz na smyczy: ", "Leading on a leash: ");
		e(" bierze cię na smycz", " puts you on a leash");
		e(" (nie ciągnie cię – tak masz ustawione)", " (it does not pull you, per your settings)");
		e("Clicker jest wyłączony – włącz go w oknie moda (zakładka Zabawa)", "The clicker is off. Turn it on in the mod window (Fun tab)");
		e("Wyceluj w gracza, żeby zrobić boop", "Aim at a player to boop");
		e(" robi ci boop w nosek", " boops your nose");
	}
}
