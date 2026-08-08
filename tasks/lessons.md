# Leçons

[2026-08-08] | Le canal DEV avait été construit « standalone » (jpackage du payload) pour aller vite ;
conséquence : aucun chemin de mise à jour, réinstallation manuelle à chaque itération.
Règle : tout canal distribué DOIT passer par le socle bootstrap + payload, sinon il n'est pas
maintenable à distance. Un raccourci de packaging se paie en réinstallations chez les testeurs.

[2026-08-08] | Un test qui lit un fichier de configuration doit ignorer les COMMENTAIRES.
Le premier jet de DevUpdatePathTest tombait sur sa propre prose : l'en-tête du workflow cite
volontairement l'ancien montage pour le rendre reconnaissable. Règle : asserter sur le contenu
effectif (lignes non commentées), jamais sur le texte brut d'un fichier documenté.

[2026-08-08] | Une empreinte de `ls -la` inclut la ligne « .. », donc la date du dossier PARENT.
Créer un dossier voisin fait « bouger » l'empreinte d'un dossier pourtant intact — et fait
croire à une régression. Règle : pour prouver qu'un dossier n'a pas bougé, comparer ses
FICHIERS (et sa propre date de modification), pas une sortie qui embarque son parent.
