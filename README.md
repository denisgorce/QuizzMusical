# Quizz Musical

Jeu d'ambiance Android : un joueur fredonne une chanson **uniquement avec une onomatopée tirée au sort** (« NA NA NA », « PAM PAM PAM »...) et les autres doivent la deviner avant la fin du chrono.

## Fonctionnalités

- **Deux types de jeu** : 🎤 **Fredonne** (un joueur fait deviner avec une onomatopée) et 🎧 **Blind test** (l'application joue l'original, titre masqué, tout le monde devine en même temps).
- **Blind test** : 10 à 50 chansons, 10 à 45 s d'écoute, extrait au cœur du morceau ou au début ; ✋ Trouvé → quoi (titre 1 / interprète 1 / les deux 3, +1 éclair) → qui ; réponse affichée avec la musique qui continue ; chanson suivante préchargée ; classement, trophées et liste réécoutable en fin de partie. Accessible depuis l'accueil ou le réglage « Type de jeu ».
- **Réglages** : mode individuel ou équipes (2 à 4), 2 à 12 joueurs, temps par joueur (30 à 120 s), 1 à 10 tours, pénalité de passe (0 / 3 / 5 / 10 s), difficulté (mix / incontournables / connaisseurs), défis surprises, bonus éclair.
- **Thème unique** choisi au début de la partie (après les réglages), conservé jusqu'à la fin.
- **Tirage au sort animé** du premier joueur, puis ordre de passage tournant.
- **Écran « Passe le téléphone à... »** entre chaque joueur.
- **🇫🇷 Jauge « chansons françaises »** (0 à 100 %, défaut 75 %) en haut du choix de playlist : s'applique à Random, aux styles et aux décennies (les titres étrangers viennent du catalogue principal et du module English).
- **Trait de séparation** : sous la jauge, les thèmes généraux ; sous le trait, les langues et thèmes spéciaux (International, English, Español, Italiano, Instrumental, Russie, Classique), non concernés par la jauge.
- **🎲 Random** : tuile vedette, la plus grosse, en tête du choix de playlist (tire dans tout le catalogue hors modules).
- **⭐ Tubes français intemporels** : 517 incontournables français de toutes les époques, de Piaf à Stromae.
- **16 playlists** : Pop, Rock, Disco / Dance, Slow, Variété, 60's à 2020's, Español, Italiano, Instrumental, Random. Une chanson peut appartenir à plusieurs playlists.
- **Español 🇪🇸, Italiano 🇮🇹, Instrumental 🎹** : ~100 titres chacun (Gipsy Kings, Despacito, Felicità, Volare, Oxygène, Popcorn, thèmes de films...), inclus dans Random et dans les décennies.
- **Module Russie** 🪆 : 100 tubes du top russe (1976-2023), titres en cyrillique avec traduction française. Playlist isolée : jamais tirée dans Random ni dans les autres playlists.
- **Module Musique de film** 🎬 : 223 chansons et thèmes de films (Disney, comédies françaises, James Bond, John Williams, Morricone...), avec le film affiché sous le titre, isolé.
- **Module International** 🇪🇺 : 500 chansons parmi les plus connues en Europe (anglais, allemand, italien, espagnol, eurodance, Eurovision...), isolé.
- **Module English** 🇬🇧 : 1 188 titres mondialement célèbres en anglais (1954-2025), riche en slows, pop rock et disco, isolé comme le module Russie.
- **Module Classique** 🎻 : 161 œuvres célèbres (Vivaldi, Mozart, Beethoven, Tchaïkovski, Ravel...), isolé, avec la période (Baroque, Classicisme, Romantisme, XXe siècle).
- **Design néon** inspiré de l'icône casque + micro : bleu nuit → violet, contours lumineux bleu → rose, boutons orange (casque) et bleus (oreillettes), notes et étoiles flottantes, égaliseur bleu → rose.
- **Style musical affiché** pour chaque chanson (carte de jeu et récapitulatifs).
- **Onomatopée + défi éventuel** (chuchoter, voix de robot, siffler, bouche fermée...) tirés dynamiquement à chaque chanson.
- **Passer** : perte de temps ; le jeu se met en pause et propose d'écouter la chanson passée (🎧), qui n'est alors plus reproposée. Désactivable dans les réglages (« Écouter après un passe »).
- **Sons doux** synthétisés : petit carillon les 5 dernières secondes (+ vibration légère, écran rouge), carillon descendant en fin de passage, arpège en fin de partie.
- **Liste de 10 chansons par passage** : les chansons passées reviennent en fin de liste s'il reste du temps ; 10/10 trouvées = **+10 pts** pour le chanteur.
- **Score** : titre = 1 pt, interprète = 1 pt, les deux = 3 pts, pour le chanteur et celui qui devine (mode équipes : pour l'équipe). Bonus éclair (< 5 s) : +1 pour chacun.
- **Trouvé** : choix de qui a trouvé et de quoi (titre / interprète / les deux), avec écoute possible de l'original (chrono en pause).
- **Rattrapage** (option) : en fin de passage, les autres joueurs écoutent l'original des chansons non trouvées (titre masqué jusqu'à la révélation) et peuvent marquer 1 / 1 / 3 pts ; le chanteur ne joue pas.
- **Récap après chaque passage, chaque tour et en fin de partie** : chaque chanson est cliquable pour l'**écouter dans l'application** (lecteur intégré : lecture/pause, ±10 s, autre version), flux audio direct sans interface ni publicité YouTube (NewPipeExtractor + ExoPlayer, comme WebRadio).
- **Pause / arrêt** : bouton ⏸ (Reprendre, Terminer et voir le classement, Quitter), pause automatique si l'app passe en arrière-plan.
- **Statistiques par joueur** en fin de partie : taux de réussite, temps moyen, passes, éclairs, chansons devinées, points forts et points à travailler (playlist forte / difficile, décennie favorite, défis...).
- **Classement final + trophées** : meilleur chanteur, meilleure oreille, plus rapide, roi du joker. Bouton revanche.
- **Anti-répétition** : les chansons déjà jouées sont mémorisées sur le téléphone et évitées aux parties suivantes (réinitialisable depuis l'accueil).
- **Hors ligne** : seule l'écoute des chansons nécessite Internet.

## Catalogue

`app/src/main/assets/songs.txt` : ~900 titres très populaires, dont ~780 chansons françaises (genre `F`). Random : 76 % de chansons françaises.

Format d'une ligne :

```
année|artiste|titre|genres|difficulté
1984|Jeanne Mas|Toute première fois|P,V|1
```

- `genres` : `P` pop, `R` rock, `D` disco / dance, `S` slow, `V` variété (plusieurs possibles, séparés par des virgules)
- `difficulté` : `1` incontournable, `2` connaisseur
- La décennie (playlists 60's à 2020's) est déduite de l'année.

`app/src/main/assets/songs_monde.txt` : Espagnol (`H`), Italien (`I`), Instrumental (`N`), même format. Un titre déjà présent dans `songs.txt` (même artiste + titre) est fusionné au chargement : ses genres se cumulent.

`app/src/main/assets/songs_film.txt` : module Musique de film, genre `M` obligatoire, 6e colonne = film d'origine.

`app/src/main/assets/songs_inter.txt` : module International, genre `X` obligatoire.

`app/src/main/assets/songs_en.txt` : module English, genre `E` obligatoire (isole la chanson dans ce module).

`app/src/main/assets/songs_classique.txt` : module Classique, genre `C` obligatoire (année = composition, artiste = compositeur).

`app/src/main/assets/songs_ru.txt` : module Russie, même format avec le genre `U` (obligatoire, isole la chanson dans ce module) et une 6e colonne optionnelle : traduction française du titre, affichée sous le titre cyrillique.

Listes compilées sans source officielle exhaustive : à relire et enrichir librement (une ligne par chanson, les lignes commençant par `#` sont ignorées).

## Build

L'APK est généré par **GitHub Actions** à chaque push sur `main` (ou manuellement via *Actions > Build APK > Run workflow*) :

- artefact `QuizzMusical-apk` dans l'exécution du workflow
- release GitHub `v1.0.N` avec l'APK en pièce jointe

Stack : Java, AGP 8.2.2, Gradle 8.4, minSdk 24, targetSdk 34. Dépendances : NewPipeExtractor (JitPack), OkHttp, Media3 ExoPlayer.

### Signature

Sans configuration, l'APK release est signé avec une clé de debug générée par le runner : il s'installe, mais une nouvelle version peut exiger de désinstaller la précédente.

Pour des mises à jour sans désinstallation, ajouter ces **secrets** dans *Settings > Secrets and variables > Actions* :

| Secret | Contenu |
|---|---|
| `KEYSTORE_BASE64` | keystore encodé en base64 |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | alias de la clé |
| `KEY_PASSWORD` | mot de passe de la clé |

Ne jamais committer le keystore ni ses mots de passe.
