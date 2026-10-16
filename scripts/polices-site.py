#!/usr/bin/env python3
"""Polices du site Pokényle → TTF statiques pour JavaFX (refonte 2026-10, lot L2).

JavaFX ne lit ni le woff2 ni les axes variables : on instancie Nunito (variable 200-1000) aux graisses 700, 800 et 900,
on décompresse Lilita One. Chaque fichier reçoit un nom de famille UNIQUE (JavaFX enregistre chaque TTF sous sa propre
famille : « Nunito Black », « Bulle Pokenyle »…), comme les Montserrat existants.

    python3 scripts/polices-site.py            # lit ~/sites/pokenyle-v2/public/polices, écrit src/main/resources/fonts/site
"""
import pathlib
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

SITE = pathlib.Path.home() / 'sites/pokenyle-v2/public/polices'
SORTIE = pathlib.Path(__file__).resolve().parent.parent / 'src/main/resources/fonts/site'


def renommer(f: TTFont, famille: str, nom_fichier: str):
    """Une famille par fichier, sous-famille « Regular » ; on retire les noms typographiques (16/17) qui regrouperaient
    les graisses sous « Nunito » (JavaFX choisirait mal la graisse)."""
    nt = f['name']
    for rec in list(nt.names):
        if rec.nameID in (16, 17, 21, 22, 25):
            nt.removeNames(nameID=rec.nameID)
    complet = famille
    ps = famille.replace(' ', '')
    for pid, eid, lid in ((3, 1, 0x409), (1, 0, 0)):
        nt.setName(famille, 1, pid, eid, lid)
        nt.setName('Regular', 2, pid, eid, lid)
        nt.setName(f'{ps}-Regular;refonte-2026-10', 3, pid, eid, lid)
        nt.setName(complet, 4, pid, eid, lid)
        nt.setName(ps + '-Regular', 6, pid, eid, lid)
    f['OS/2'].fsSelection = (f['OS/2'].fsSelection & ~0b1100001) | 0b1000000   # REGULAR, ni gras ni italique
    f['head'].macStyle = 0
    f.flavor = None
    f.save(SORTIE / nom_fichier)
    print('écrit', nom_fichier, '·', famille, '· graisse', f['OS/2'].usWeightClass)


def main():
    SORTIE.mkdir(parents=True, exist_ok=True)
    for graisse, nom in ((700, 'Bold'), (800, 'ExtraBold'), (900, 'Black')):
        f = TTFont(SITE / 'nunito.woff2')
        f = instantiateVariableFont(f, {'wght': graisse}, updateFontNames=False)
        f['OS/2'].usWeightClass = graisse
        renommer(f, f'Nunito {nom}', f'Nunito-{nom}.ttf')
    renommer(TTFont(SITE / 'lilita-one.woff2'), 'Bulle Pokenyle', 'BullePokenyle-Regular.ttf')   # Lilita One renommée : « Lilita » est un nom réservé (OFL)
    # Titres : Northura Black SemExp (décision owner 16/10), copiée telle quelle depuis
    # ~/sites/pokenyle-v2/fournis/polices-titres/northura-black-semi-expanded.ttf → NorthuraBlackSemiExpanded.ttf
    # (police commerciale « All Rights Reserved » : aucune modification, pas de renommage).


if __name__ == '__main__':
    main()
