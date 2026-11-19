# FAUX DISCORD pour Windows : essaie le client de tube nommé de l'activité Discord du NyleLauncher
# sans jamais toucher au vrai Discord (le tube a un autre nom que discord-ipc-N).
#
# Fenêtre 1 (le faux Discord) :
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\faux-discord.ps1
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
    [int]$Connexions = 2
)

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
        [System.IO.Pipes.PipeTransmissionMode]::Byte)
    Write-Host "Faux Discord : j'attends sur \\.\pipe\$Nom (connexion $k sur $Connexions)"
    $serveur.WaitForConnection()
    try {
        while ($true) {
            $entete = Lire-Exact $serveur 8
            if ($null -eq $entete) { Write-Host 'client parti'; break }
            $op = [BitConverter]::ToInt32($entete, 0)
            $n = [BitConverter]::ToInt32($entete, 4)
            $corps = Lire-Exact $serveur $n
            if ($null -eq $corps) { Write-Host 'trame tronquée'; break }
            $json = [Text.Encoding]::UTF8.GetString($corps)
            Write-Host ('{0:HH:mm:ss} reçu op={1} {2}' -f (Get-Date), $op, $json)
            if ($op -eq 0) {
                Envoyer $serveur 1 '{"cmd":"DISPATCH","evt":"READY","nonce":null,"data":{"v":1,"config":{},"user":{"id":"1","username":"essai"}}}'
                Envoyer $serveur 3 '{"essai":"ping"}'
            } elseif ($op -eq 1) {
                $o = $json | ConvertFrom-Json
                Envoyer $serveur 1 ('{"cmd":"SET_ACTIVITY","evt":null,"nonce":"' + $o.nonce + '","data":{}}')
            } elseif ($op -eq 4) {
                Write-Host 'PONG reçu'
            } elseif ($op -eq 2) {
                Write-Host 'CLOSE reçu'
                break
            }
        }
    } catch {
        Write-Host "fin de connexion : $_"
    } finally {
        $serveur.Dispose()
    }
}
Write-Host 'Faux Discord : terminé'
