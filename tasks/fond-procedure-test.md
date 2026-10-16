# Launcher en fond (lot L1) — procédure de test Windows et Linux

Testé sur macOS le 16/10/2026 (voir le rapport du lot). Windows et Linux n'ont pas pu être testés sur la
machine de développement : cette procédure est pour un testeur, avec l'installeur du canal DEV.

Pour chaque étape, noter OK / KO et joindre une capture d'écran en cas de KO, plus le journal :
- Windows : `%LOCALAPPDATA%\NyleLauncher\Logs\launcher.log` ;
- Linux : `~/.local/state/nylelauncher/launcher.log`.

Chercher les lignes « Fond : ».

## Windows 10 / 11

1. **Premier lancement** (double-clic sur le raccourci) : la fenêtre s'ouvre comme avant. Une icône du
   launcher apparaît près de l'horloge (éventuellement dans la flèche « Afficher les icônes cachées »).
2. **Entrée de démarrage** : Gestionnaire des tâches → onglet « Applications de démarrage » : une ligne
   « NyleLauncher » (ou « NyleLauncher DEV ») est présente et activée.
   Vérification technique : `reg query HKCU\Software\Microsoft\Windows\CurrentVersion\Run /v NyleLauncher`
   → `"C:\…\NyleLauncher.exe" --fond`.
3. **Fermer la fenêtre** (croix) : la fenêtre disparaît, l'icône reste près de l'horloge, et une carte
   « Le launcher reste à portée de main » apparaît en bas à droite (une seule fois, au premier masquage).
   Aucun bouton du launcher ne reste dans la barre des tâches.
4. **Clic gauche sur l'icône** : la fenêtre revient au premier plan. **Clic droit** : menu « Ouvrir
   NyleLauncher / Quitter ».
5. **Double lancement** : fenêtre fermée (icône seule), double-cliquer à nouveau le raccourci du bureau :
   la fenêtre existante revient, aucun second launcher ne s'ouvre (une seule icône près de l'horloge,
   un seul `NyleLauncher.exe` dans le Gestionnaire des tâches).
6. **Ouverture de session** : redémarrer le PC. Après la connexion, l'icône apparaît près de l'horloge,
   AUCUNE fenêtre ne s'ouvre, aucun son ne joue. Clic sur l'icône : la fenêtre s'ouvre normalement.
7. **Quitter** (clic droit → Quitter) : l'icône disparaît, plus aucun `NyleLauncher.exe` ni `javaw.exe`
   du launcher dans le Gestionnaire des tâches.
8. **Désactivation par le joueur** : Gestionnaire des tâches → Démarrage → « Désactiver » sur NyleLauncher,
   puis relancer le launcher : la ligne doit RESTER désactivée (le launcher ne réactive pas de force).
9. **Désinstallation** : désinstaller le launcher (Paramètres → Applications), redémarrer le PC : aucune
   boîte d'erreur à l'ouverture de session (la valeur `Run` orpheline est ignorée par Windows). Noter si
   la ligne reste visible dans le Gestionnaire des tâches (attendu : oui, grisée ou « introuvable »,
   sans effet).
10. **Mise à l'échelle** : à 125 % et 150 % (Paramètres → Affichage), l'icône près de l'horloge est nette
    et la carte d'annonce n'est pas coupée.

## Linux (Ubuntu GNOME, Fedora KDE, Mint Cinnamon si possible)

1. **Premier lancement** : fenêtre normale. KDE / Cinnamon / XFCE : icône dans la zone d'état.
   GNOME sans extension « AppIndicator » : pas d'icône (attendu) ; le journal indique « pas de zone de
   notification sur ce bureau ».
2. **Entrée de démarrage** : `cat ~/.config/autostart/nylelauncher.desktop` → `Exec="/opt/…/NyleLauncher" --fond`.
   GNOME : Ajustements → Applications au démarrage : NyleLauncher listé.
3. **Avec icône** (KDE…) : fermer la fenêtre → elle disparaît, l'icône reste, carte d'astuce en haut à
   droite ; clic gauche sur l'icône → la fenêtre revient ; clic droit → Ouvrir / Quitter.
4. **Sans icône** (GNOME nu) : fermer la fenêtre → le launcher QUITTE complètement (`pgrep -f NyleLauncher`
   ne renvoie rien). Il ne doit jamais rester un launcher invisible impossible à quitter.
5. **Double lancement** : relancer depuis le menu des applications pendant qu'il tourne → la fenêtre
   existante revient, pas de second processus.
6. **Ouverture de session** : se déconnecter / reconnecter → launcher lancé en fond (icône si le bureau en
   a), aucune fenêtre.
7. **Désinstallation** (`sudo apt remove nylelauncher`) puis reconnexion : aucune erreur visible.

## Réglages (toutes plateformes, dès que le lot L6 les expose)

- « Lancer au démarrage » décoché → l'entrée disparaît (Windows : `reg query` ne la trouve plus ;
  Linux : le fichier `.desktop` est supprimé).
- « Garder dans la barre système » décoché → fermer la fenêtre quitte le launcher.
- « Notifications » décoché → aucune carte d'annonce.
- « Tout supprimer » → l'entrée de démarrage est retirée aussi.
