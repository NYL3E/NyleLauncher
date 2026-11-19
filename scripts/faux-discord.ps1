# FAUX DISCORD pour Windows : essaie le client de tube nommé de l'activité Discord du NyleLauncher
# sans jamais toucher au vrai Discord (le tube a un autre nom que discord-ipc-N).
#
# Fenêtre 1 (le faux Discord) :
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\faux-discord.ps1
#   (options : -Nom <tube de test>, -Connexions <n>, -Journal <fichier> pour garder les lignes horodatées,
#    -Tampon 0 pour un faux Discord dont les écritures bloquent tant que le client ne lit pas,
#    -Muet pour un Discord figé qui ne lit plus rien après READY)
# Fenêtre 2 (le client, avec le jar du payload) :
#   $env:NYLE_DISCORD_IPC = '\\.\pipe\nyle-essai-discord-0'
#   java -cp nylelauncher-1.0.0.jar fr.nylerp.launcher.discord.EssaiDiscord --discord-test 123456789012345678
#
# Attendu dans la fenêtre 1 : op=0 (poignée de main, client_id), op=1 SET_ACTIVITY avec « En jeu sur Pokényle »,
# PONG reçu (réponse au PING envoyé après READY), puis 20 s plus tard op=1 SET_ACTIVITY SANS activity (effacée)
# et CLOSE reçu. Fenêtre 2 : « CONNECTE » puis « activité effacée, connexion fermée. » sans pendre.
# Sans la variable NYLE_DISCORD_IPC, le client viserait le VRAI Discord : ne pas l'oublier.
# Fichier en UTF-8 avec BOM : Windows PowerShell 5.1 lit alors correctement les accents.
param(
    [string]$Nom = 'nyle-essai-discord-0',
    [int]$Connexions = 2,
    [string]$Journal = '',
    # Tampons du tube : 65536 comme Discord (libuv) ; 0 reproduit le blocage d'écriture vu le 19/11 au portable.
    [int]$Tampon = 65536,
    # Discord figé : après READY, plus rien n'est lu (les écritures du client restent en attente).
    [switch]$Muet
)

# Chaque ligne va à l'écran et, si -Journal est donné, dans ce fichier (horodatée).
function Noter([string]$t) {
    $l = (Get-Date).ToString('HH:mm:ss.fff') + ' ' + $t
    Write-Host $l
    if ($Journal) { Add-Content -Path $Journal -Value $l -Encoding UTF8 }
}

function Lire-Exact([System.IO.Stream]$flux, [int]$n) {
    $buf = New-Object byte[] $n
    $lu = 0
    while ($lu -lt $n) {
        $r = $flux.Read($buf, $lu, $n - $lu)
        if ($r -le 0) { return $null }
        $lu += $r
    }
    return ,$buf
}

function Envoyer([System.IO.Stream]$flux, [int]$op, [string]$json) {
    $corps = [Text.Encoding]::UTF8.GetBytes($json)
    # Code d'opération puis longueur, 4 octets chacun, petit-boutistes (BitConverter l'est sous Windows).
    [byte[]]$entete = [BitConverter]::GetBytes([int32]$op) + [BitConverter]::GetBytes([int32]$corps.Length)
    $flux.Write($entete, 0, 8)
    $flux.Write($corps, 0, $corps.Length)
    $flux.Flush()
}

for ($k = 1; $k -le $Connexions; $k++) {
    $serveur = New-Object System.IO.Pipes.NamedPipeServerStream($Nom, [System.IO.Pipes.PipeDirection]::InOut, 1,
        [System.IO.Pipes.PipeTransmissionMode]::Byte, [System.IO.Pipes.PipeOptions]::None, $Tampon, $Tampon)
    Noter "Faux Discord : j'attends sur \\.\pipe\$Nom (connexion $k sur $Connexions)"
    $serveur.WaitForConnection()
    try {
        while ($true) {
            $entete = Lire-Exact $serveur 8
            if ($null -eq $entete) { Noter 'client parti'; break }
            $op = [BitConverter]::ToInt32($entete, 0)
            $n = [BitConverter]::ToInt32($entete, 4)
            $corps = Lire-Exact $serveur $n
            if ($null -eq $corps) { Noter 'trame tronquée'; break }
            $json = [Text.Encoding]::UTF8.GetString($corps)
            Noter ('reçu op={0} {1}' -f $op, $json)
            if ($op -eq 0) {
                Envoyer $serveur 1 '{"cmd":"DISPATCH","evt":"READY","nonce":null,"data":{"v":1,"config":{},"user":{"id":"1","username":"essai"}}}'
                if ($Muet) {
                    Noter 'muet : je ne lis plus rien'
                    while ($serveur.IsConnected) { Start-Sleep -Milliseconds 200 }
                    break
                }
                Envoyer $serveur 3 '{"essai":"ping"}'
            } elseif ($op -eq 1) {
                $o = $json | ConvertFrom-Json
                # Comme Discord : une réponse impossible (client déjà parti) n'empêche pas de lire ce qu'il a envoyé (CLOSE).
                try { Envoyer $serveur 1 ('{"cmd":"SET_ACTIVITY","evt":null,"nonce":"' + $o.nonce + '","data":{}}') }
                catch { Noter 'réponse impossible, je continue de lire' }
            } elseif ($op -eq 4) {
                Noter 'PONG reçu'
            } elseif ($op -eq 2) {
                Noter 'CLOSE reçu'
                break
            }
        }
    } catch {
        Noter "fin de connexion : $_"
    } finally {
        $serveur.Dispose()
    }
}
Noter 'Faux Discord : terminé'
