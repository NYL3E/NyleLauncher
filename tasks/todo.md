# TODO — Mise à jour du launcher DEV sans réinstallation

## Contexte
Le canal DEV `jpackage`ait le PAYLOAD directement (`--main-class fr.nylerp.launcher.Main`),
sans socle, et `SelfUpdater.check()` renvoie « aucune mise à jour » dès que `Channel.isDev()`.
Un launcher DEV n'allait donc jamais chercher quoi que ce soit → réinstallation manuelle
à chaque itération.

## Plan retenu — famille A (aligner DEV sur l'architecture de production)
Décision de l'owner : « prends la solution la plus propre ».

- [x] Bootstrap rendu conscient du canal — manifeste et cache séparés, production littéralement
      inchangée (repli en `prod` au moindre doute)
- [x] Purge du cache DEV (3 charges gardées) — le cache prod n'est jamais purgé
- [x] Publication d'une charge DEV sur le tag stable `dev-payload` par la CI
- [x] `dev-launcher.yml` empaquette le SOCLE DEV (job `native`, à la demande) et publie la
      charge (job `payload`, le cas courant)
- [x] `Constants.runningPayloadVersion()` — version réellement chargée, affichée en DEV,
      littéral inchangé en prod
- [x] `SelfUpdater` : garde DEV conservée, commentaire réécrit (aucune ligne de code modifiée)
- [x] Test de garde `bootstrap/DevUpdatePathTest` — 11 assertions, branché sur le job `garde`
      du workflow DEV
- [x] Témoin ROUGE → VERT sur 4 régressions → `design/dev-update/garde-temoin-rouge-vert.txt`
- [x] Preuve réelle de mise à jour → `design/dev-update/` (journal + 4 captures)
- [x] Preuve production intacte → `design/dev-update/preuve-production-intacte.txt`

## Reste à faire (owner)
- [ ] Publier : lancer `Build DEV launcher` avec `installers = true` (une fois), puis annoncer
      aux testeurs la DERNIÈRE réinstallation
- [ ] Ensuite : lancer le même workflow avec `installers = false` à chaque nouvelle charge

## Piste ouverte, hors périmètre
Le cache de PRODUCTION de cette machine contient 51 charges pour ~4,3 Go. La purge existe
déjà (`pruneDevCache`) mais est volontairement gardée au canal DEV pour ne rien changer en
production. À décider séparément.
