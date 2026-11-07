param([string]$Version="1.21.4")
$ErrorActionPreference="Stop"
$root=Split-Path $PSScriptRoot -Parent
$server=Join-Path $PSScriptRoot "paper-$Version"
Copy-Item (Join-Path $root "build/libs/DamageTracker-2.2.0.jar") (Join-Path $server "plugins")
$psi=[Diagnostics.ProcessStartInfo]::new()
$psi.FileName='C:\Program Files\Java\jdk-25.0.3\bin\java.exe'
$psi.Arguments='-Dterminal.jline=false -Dterminal.ansi=false -Xms512M -Xmx1536M -jar paper.jar --nogui'
$psi.WorkingDirectory=$server
$psi.UseShellExecute=$false
$psi.CreateNoWindow=$true
$psi.RedirectStandardInput=$true
$started=Get-Date
$p=[Diagnostics.Process]::Start($psi)
try {
    $deadline=[DateTime]::UtcNow.AddMinutes(3)
    do {
        Start-Sleep -Seconds 2
        $log=Join-Path $server "logs/latest.log"
        $ready=(Test-Path $log) -and ((Get-Item $log).LastWriteTime -ge $started) -and ((Get-Content $log -Raw) -match 'Done \(')
    } until ($ready -or $p.HasExited -or [DateTime]::UtcNow -gt $deadline)
    if (!$ready -or $p.HasExited) { throw "Server did not start" }
    foreach ($command in @('version','plugins','damagetracker check SkeletonKing','damagetracker reload','mm mobs spawn SkeletonKing 1 world,0,5,0')) {
        $p.StandardInput.WriteLine($command)
        Start-Sleep -Seconds 2
    }
    $p.StandardInput.WriteLine('minecraft:kill @e[type=minecraft:wither_skeleton]')
    Start-Sleep -Seconds 3
} finally {
    if (!$p.HasExited) {
        $p.StandardInput.WriteLine('stop')
        if (!$p.WaitForExit(30000)) { $p.Kill() }
    }
}
Copy-Item $log (Join-Path $PSScriptRoot "paper-$Version-result.log")
