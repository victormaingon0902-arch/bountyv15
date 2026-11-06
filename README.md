# BountyCraft — Paper 26.3 (Java 25, Maven)

Économie (`/bank`, `/pay`…), PNJ **Hunter** (primes), évènement **/hunt**, PNJ **Forgeron**
(boutique pilotée par `forge.yml`), PNJ **Alchimiste** (8 élixirs), PNJ **Guide**,
resource pack (pioche, hache, potions). Les PNJ sont des entités **Mannequin** natives
(skin de joueur, aucune dépendance : ni Citizens, ni ProtocolLib).
6 PNJ : Hunter, Forgeron, Alchimiste, Guide, **Quêtier** (quête horaire) et **Marchand** (rachat de cultures/objets).

## Compiler
Prérequis : **JDK 25** + Maven.

```
mvn clean package
```
→ `target/BountyCraft-1.0.0.jar` à placer dans `plugins/` d'un serveur **Paper 26.3**.
La dépendance est `io.papermc.paper:paper-api:[26.3.build,26.4)` (build 26.3) ;
pour figer une build : remplacez par exemple `26.3.build.X-stable` dans `pom.xml`.

## Resource pack (obligatoire pour les textures)
1. Hébergez `BountyCraft-ResourcePack.zip` en **lien direct** (GitHub Release, votre site, etc.).
2. `config.yml` → `resource-pack.url: "https://…/BountyCraft-ResourcePack.zip"`
   (le `sha1` est déjà rempli pour le zip fourni ; si vous modifiez le zip, recalculez-le avec `sha1sum`).
3. Le pack est envoyé à la connexion. `/bountycraft pack` le renvoie à un joueur.
Sans pack, les objets custom s'affichent avec un modèle manquant (violet/noir).

## Mise en place des PNJ (admin)
```
/npc create hunter | blacksmith | alchemist | guide | quest | merchant     (apparaît là où vous êtes)
/npc skin                 -> VOTRE skin sur le PNJ regardé
/npc skin <joueur>        -> le skin d'un autre joueur
/npc skinall [joueur]     -> même chose pour TOUS les PNJ
/npc remove               -> supprime le PNJ regardé
/npc list
```
Clic droit sur un PNJ = ouvre son menu. Les PNJ sont invulnérables et immobiles.

**PNJ invisible ?** `/npc create` vérifie désormais que le PNJ existe vraiment dans le monde et
affiche la raison en cas d'échec. `/npc list` indique l'état de chaque PNJ : `[présent]`,
`[chunk non chargé]` ou `[ABSENT]` (enregistré mais introuvable). Causes fréquentes :
spawn annulé par un autre plugin (WorldGuard, anti-lag), PNJ supprimé par un plugin de nettoyage
d'entités (ClearLag…), ou entité non envoyée au client (plugin de visibilité, `entity-tracking-range`).
Le plugin empêche déjà l'annulation du spawn de ses propres PNJ ; la console détaille le reste.

## Serveur ouvert au crack (online-mode=false)
En crack, l'UUID d'un joueur est calculé à partir de son pseudo : il diffère de l'UUID premium. Le plugin gère ça :
au démarrage, les comptes en double (même pseudo en premium ET en crack) sont fusionnés dans `data.yml`, et au premier
passage d'un joueur sous un nouvel UUID il retrouve son compte (solde, kills, morts). Solde conservé = le plus élevé
des comptes déjà utilisés. `/eco` et `/pay` visent toujours le compte du joueur connecté.
Hors plugin (à régler côté serveur) : skins (SkinsRestorer), OP (`ops.json`), rangs LuckPerms, et surtout un plugin
de connexion (AuthMe) : en crack, n'importe qui peut se connecter avec le pseudo d'un OP.

## Commandes
| Commande | Rôle | Permission |
|---|---|---|
| `/bank [joueur]` (`/bal`, `/money`) | solde | tous (autres joueurs : `bountycraft.bank.others`) |
| `/pay <joueur> <montant>` | envoyer de l'argent | tous |
| `/baltop` | top 10 | tous |
| `/contract [cancel]` | voir/annuler son contrat Hunter | tous |
| `/eco give\|take\|set\|reset <joueur> [montant]` | admin éco | `bountycraft.admin.eco` |
| `/hunt <joueur> [prime]` · `/hunt stop` · `/hunt info` | chasse à l'homme | `bountycraft.admin.hunt` |
| `/npc …` | PNJ | `bountycraft.admin.npc` |
| `/bountycraft reload\|give <joueur> <id>\|pack\|ids` | admin | `bountycraft.admin` |
| `/bountycraft reset` puis `/bountycraft reset confirm` | reset général : inventaires + coffres de l'Ender (même hors ligne), argent de tous remis au solde de départ, limite des élixirs à 0 | `bountycraft.admin` |

## Mécaniques
**Hunter.** Le joueur choisit une cible dans le menu et doit la tuer (60 min max). La prime vaut
`max(min, base × decay^morts)` où *morts* = nombre de fois où la cible est morte **de la main d'un joueur**
(valeurs dans `config.yml › bounty`). Une cible tuée est protégée 10 min. Action bar : distance, flèche, temps.

**/hunt.** 30 min, boss bar pour tout le monde (temps, prime, coordonnées de la cible ; la cible voit sa propre barre).
Le joueur qui tue la cible empoche la prime (`hunt-event`, ou valeur donnée à `/hunt`).

**Quêtier (`/npc create quest`).** Une quête simple et bien payée **toutes les heures** : le joueur accepte une quête au
hasard (`quests.yml`), la termine, encaisse, puis attend `cooldown-minutes` (60). Deux types : *DELIVER* (rapporter des
objets « normaux », livraison cumulable en plusieurs fois) et *KILL* (tuer des créatures). On peut abandonner (5 min d'attente).
L'état est enregistré sur le joueur (il survit aux redémarrages). Ajoutez vos propres quêtes dans `quests.yml`.

**Marchand (`/npc create merchant`).** Rachète cultures, récoltes de monstres et minerais selon `sell.yml`
(prix par objet). Clic gauche = vendre 1, clic droit = 16, Shift + clic = tout ce qu'on a de cet objet, bouton **Tout vendre**.
Les objets renommés, enchantés ou personnalisés ne sont jamais rachetés.

**Forgeron.** Tout est dans `forge.yml` (voir plus bas). Accroupi = capacités désactivées.

**Élixirs** (une seule potion active à la fois, perdue à la mort) — compétence = touche **F**
(échange de main) ; l'End a aussi **Shift+F**. Les durées/cooldowns/prix sont dans `config.yml › elixirs`.

| Élixir | Passif | Compétence |
|---|---|---|
| Invisibilité | invisibilité **permanente**, les monstres ne vous attaquent jamais | aura 5 min : *Obscurité* aux joueurs à ≤ 10 blocs |
| Warden | +10 cœurs | rayon sonique (dégâts + recul) |
| Soin | Régénération II | 30 s : soigne l'être regardé |
| Émeraude | Héros du village + Célérité II | — |
| Vent | zéro dégât de chute | propulsion vers le haut puis dash vers l'avant (au sol, sans recharge) |
| End | — | F : téléportation (sans cooldown) · Shift+F : souffle du dragon |
| Feu | Résistance au feu | 3 min : blocs/mobs tués cuits, tout ce que vous frappez brûle |
| Eau | Grâce du dauphin + Puissance de l'aqueduc | pluie 15 min + trident *Impulsion* prêté 15 min |

## Ajouter un outil / une arme sans toucher au plugin
Éditez `plugins/BountyCraft/forge.yml` puis `/bountycraft reload` :

```yaml
items:
  ma_pelle:
    price: 18000
    material: NETHERITE_SHOVEL
    name: "<gradient:#ff9a1a:#ff3d00><bold>Pelle des Braises"
    lore: ["<gray>Mine en 5x5x5"]
    item-model: "bountycraft:ma_pelle"     # optionnel : texture ajoutée au resource pack
    unbreakable: true
    enchantments: { efficiency: 8, unbreaking: 5 }
    abilities:
      area-mine: { radius: 2 }             # area-mine | tree-feller | ignite | lifesteal
```
Pour une texture perso : ajoutez `assets/bountycraft/textures/item/ma_pelle.png`,
`models/item/ma_pelle.json` et `items/ma_pelle.json` (copiez ceux de la pioche).

## Fichiers générés
`config.yml`, `forge.yml`, `quests.yml`, `sell.yml`, `guide.yml` (Discord, site, règles, fonctionnement — **à personnaliser**),
`data.yml` (comptes, stats), `npcs.yml` (registre des PNJ).

## Notes
* Les contrats Hunter sont en mémoire (perdus au redémarrage) ; l'argent et les stats sont sauvegardés.
* L'API `Mannequin`/`ResolvableProfile` est marquée *expérimentale* par Paper : à surveiller lors des mises à jour.


## Modifications intégrées

- Les achats d'élixirs sont limités à cinq exemplaires par type et par serveur ; les compteurs persistants sont stockés dans `elixir-purchases.yml`. Les dons via `/bountycraft give` ne consomment pas cette limite.
- Les niveaux Héros du village, Célérité, Grâce du dauphin et Puissance de l'aqueduc sont réglables dans `config.yml`. Une nouvelle potion rend l'ancien élixir au joueur.
- La compétence Vent est un dash horizontal utilisable au sol sans recharge.
- `/hunt <ban|event> <temps> <joueur> [durée-du-ban|def]` lance une chasse avec bossbar rouge. Les durées acceptent `s`, `m`, `h` ou `d` (ex. `30m`, `2h`) ; sans durée de ban ou avec `def`, le ban est permanent.
- Le Marchand ne rachète que poissons, pastèque, patate, carotte, betterave et chair putréfiée, à bas prix.
- `/kit create <nom> <prix>` crée un kit depuis la shulker tenue en main principale. La clé est vendue chez le Forgeron ; posez la boîte au spawn et faites un clic droit avec la clé correspondante.
- **Kits réservés à un grade (LuckPerms)** : `/kit perm <kit> [permission|none] [libellé]` réserve un kit à une permission. `/kit perm vip group.vip VIP` suffit pour le réserver au groupe LuckPerms `vip` (le nœud `group.<nom>` est vrai pour tous les membres du groupe, y compris ceux qui en héritent). Sans permission indiquée, le nœud par défaut est `bountycraft.kit.<kit>` : donnez-le alors au grade avec `/lp group vip permission set bountycraft.kit.vip true`. `/kit perm <kit> none` rend le kit libre, `/kit list` affiche les kits et leur permission. Un joueur sans la permission ne peut ni acheter la clé (le Forgeron l'affiche avec « Vous n'avez pas accès à ce kit ») ni ouvrir la boîte, et sa clé n'est pas consommée. Les administrateurs (`bountycraft.admin`) passent toujours. À la main, dans `kits.yml` : `permission:` et `label:` sous le kit, puis redémarrer. Avant sa première écriture après chaque démarrage, le plugin copie `kits.yml` (et `kit-boxes.yml`) en `.bak` à côté de l'original.
- **Remplir / corriger un kit** : `/kit update <kit>` remplace le contenu d'un kit existant par la shulker tenue en main (prix, nom, grade et boîtes posées ne changent pas). Un kit vide ne peut plus être créé ni vendu, et sa clé n'est pas consommée si on l'utilise.
- **Texte au-dessus des boîtes** : chaque boîte affiche le nom du kit, le nom de sa clé, son prix et le grade requis. Ces textes ne sont pas sauvegardés dans le monde : ils sont recréés au démarrage, au chargement des chunks et après `/bountycraft reload`.
- **Texture des clés** : `/kit key <kit> <texture>` (copper, fire, gold, diamond, iron, amethyst, steel, wood ; `none` = valeur par défaut). Les valeurs par défaut sont dans `config.yml`, section `kit-keys`. Les textures viennent du resource pack (`assets/bountycraft/items/key_<texture>.json`) : régénérez et rehébergez le pack, puis mettez à jour son `sha1`. Les clés achetées avant le changement gardent l'ancien aspect. `/kit list` affiche pour chaque kit son prix, son contenu, sa texture et son grade.
