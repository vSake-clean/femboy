#!/usr/bin/env bash
# Buduje Femboy Skins na kilka wersji Minecrafta.
#
# Dla każdej wersji dobiera pasujące numery z internetu:
#   - yarn mappings  → meta.fabricmc.net
#   - fabric-loader  → meta.fabricmc.net (najnowszy stabilny; działa w całej linii 1.21.x)
#   - fabric-api     → api.modrinth.com
# i uruchamia `./gradlew build -Pmc=… -Pyarn=… -Ploader=… -Pfabricapi=…`.
# Gotowe jary zbiera do dist/.
#
# Użycie:
#   tools/build-all.sh                 # domyślna lista wersji (patrz niżej)
#   tools/build-all.sh 1.21.8 1.21.11  # wybrane wersje
#
# Wymaga dostępu do sieci (Mojang + Fabric + Modrinth). UWAGA: kod kompiluje się na razie tylko na 1.21.11. Mixiny celują w
# wewnętrzne klasy renderowania, które Mojang przebudował nawet w obrębie 1.21.x
# więc wcześniejsze wersje mogą się nie skompilować bez poprawek — skrypt nie
# przerywa serii, tylko wypisuje, co się nie udało.
set -u
cd "$(dirname "$0")/.."

VERSIONS=("$@")
if [ ${#VERSIONS[@]} -eq 0 ]; then
	VERSIONS=(1.21.11 1.21.10 1.21.9 1.21.8 1.21.7 1.21.6)
fi

mkdir -p dist
jq_get() { # $1 = url, $2 = jq filter
	curl -fsS -m 30 "$1" 2>/dev/null | jq -r "$2" 2>/dev/null
}

LOADER="$(jq_get 'https://meta.fabricmc.net/v2/versions/loader' '[.[] | select(.stable==true)][0].version')"
[ -z "$LOADER" ] && LOADER="$(jq_get 'https://meta.fabricmc.net/v2/versions/loader' '.[0].version')"
echo "fabric-loader: $LOADER"

ok=(); fail=()
for mc in "${VERSIONS[@]}"; do
	echo
	echo "==================== Minecraft $mc ===================="
	yarn="$(jq_get "https://meta.fabricmc.net/v2/versions/yarn/$mc" '.[0].version')"
	api="$(jq_get "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22$mc%22%5D&loaders=%5B%22fabric%22%5D" '.[0].version_number')"
	if [ -z "$yarn" ] || [ -z "$api" ]; then
		echo "!! brak wersji (yarn='$yarn' api='$api') – pomijam $mc"
		fail+=("$mc (brak zależności)")
		continue
	fi
	echo "yarn=$yarn  fabric-api=$api"
	if ./gradlew --no-daemon clean build \
		-Pmc="$mc" -Pyarn="$yarn" -Ploader="$LOADER" -Pfabricapi="$api"; then
		cp -v build/libs/*.jar dist/ 2>/dev/null
		ok+=("$mc")
	else
		echo "!! build $mc nie przeszedł"
		fail+=("$mc (błąd kompilacji)")
	fi
done

echo
echo "==================== PODSUMOWANIE ===================="
echo "OK:   ${ok[*]:-(brak)}"
echo "BŁĄD: ${fail[*]:-(brak)}"
echo "Jary w: dist/"
ls -1 dist/*.jar 2>/dev/null
