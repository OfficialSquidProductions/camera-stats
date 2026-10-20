param([Parameter(Mandatory=$true)][string]$MSI,[Parameter(Mandatory=$true)][string]$PackagedLauncher,[Parameter(Mandatory=$true)][string]$Output)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
New-Item -ItemType Directory -Path $Output | Out-Null
function RunApp([string]$launcher,[string[]]$arguments,[string]$pass) {
    $p=Start-Process -FilePath $launcher -ArgumentList $arguments -Wait -PassThru
    if ($p.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $pass)) {throw "Runtime smoke failed ($($p.ExitCode)): $launcher"}
    Get-Content -LiteralPath $pass
}
$qa=Join-Path $Output 'packaged'
RunApp $PackagedLauncher @('--self-test',('"'+$qa+'"')) (Join-Path $qa 'PASS.txt')
$oldDir=Join-Path $Output 'previous'
gh release download v0.1.1-alpha.3 --repo $env:GITHUB_REPOSITORY --pattern Shot-Reporter-Windows-0.1.1-alpha.3.msi --dir $oldDir
if ($LASTEXITCODE -ne 0) {throw 'Previous public MSI download failed'}
Remove-Item Env:GH_TOKEN -ErrorAction SilentlyContinue
$old=Join-Path $oldDir 'Shot-Reporter-Windows-0.1.1-alpha.3.msi'
if ((Get-FileHash -LiteralPath $old -Algorithm SHA256).Hash.ToLowerInvariant() -ne '319454e60bf5b651131c0edf1fe77d88a1ed3aba9f9934a6d7770d5e99ccc659') {throw 'Previous MSI hash mismatch'}
# All report data is confined to a fresh synthetic profile, never the runner user's existing data.
$env:LOCALAPPDATA=Join-Path $Output 'isolated-profile'
New-Item -ItemType Directory -Path $env:LOCALAPPDATA | Out-Null
$p=Start-Process "$env:SystemRoot/System32/msiexec.exe" -ArgumentList @('/i',('"'+$old+'"'),'/qn','/norestart','/L*v',('"'+(Join-Path $Output 'previous-install.log')+'"')) -Wait -PassThru
if ($p.ExitCode -notin 0,3010) {throw "Previous MSI installation failed: $($p.ExitCode)"}
$installed=Join-Path $env:ProgramFiles 'Shot Reporter/Shot Reporter.exe'
$oldQA=Join-Path $Output 'old-runtime'
RunApp $installed @('--self-test',('"'+$oldQA+'"')) (Join-Path $oldQA 'PASS.txt')
$data=Join-Path $env:LOCALAPPDATA 'Squid Productions/Camera Stats';New-Item -ItemType Directory -Path $data | Out-Null
Copy-Item -LiteralPath (Join-Path $oldQA 'qa.sqlite3') -Destination (Join-Path $data 'reports.sqlite3')
Set-Content -LiteralPath (Join-Path $data 'preferences.properties') -Value 'theme=Dark'
# Seed a separate synthetic prior desktop journal schema with the current QA command; old public builds have no desktop media engine.
$p=Start-Process $PackagedLauncher -ArgumentList '--seed-desktop-upgrade-journal' -Wait -PassThru
if ($p.ExitCode -ne 0) {throw 'Synthetic prior journal fixture failed'}
if ((Get-AuthenticodeSignature -LiteralPath $MSI).Status -ne 'NotSigned') {throw 'This release expects an unsigned manual installer; review publisher policy before changing this gate'}
$p=Start-Process "$env:SystemRoot/System32/msiexec.exe" -ArgumentList @('/i',('"'+$MSI+'"'),'/qn','/norestart','/L*v',('"'+(Join-Path $Output 'upgrade-install.log')+'"')) -Wait -PassThru
if ($p.ExitCode -notin 0,3010) {throw "New MSI installation failed: $($p.ExitCode)"}
$installedQA=Join-Path $Output 'installed-runtime'
RunApp $installed @('--self-test',('"'+$installedQA+'"')) (Join-Path $installedQA 'PASS.txt')
$upgrade=Join-Path $Output 'upgrade'
RunApp $installed @('--verify-desktop-upgrade',('"'+(Join-Path $oldQA 'package-test.camreport')+'"'),('"'+$upgrade+'"')) (Join-Path $upgrade 'PASS.txt')
$media=Join-Path $Output 'installed-media'
RunApp $installed @('--desktop-release-smoke',('"'+$media+'"'),('"'+(Join-Path $repo 'fixtures/media/numbered-24.mp4')+'"')) (Join-Path $media 'PASS.txt')
$products=@(Get-ItemProperty 'HKLM:/Software/Microsoft/Windows/CurrentVersion/Uninstall/*' | Where-Object {$_.DisplayName -eq 'Shot Reporter'})
if ($products.Count -ne 1 -or $products[0].DisplayVersion -ne '0.2.0') {throw 'Installed product version/upgrade identity is not unique0.2.0'}
Set-Content -LiteralPath (Join-Path $Output 'PASS.txt') -Value 'PASS: actual previous0.1.1 MSI ->0.2.0 MSI install, isolated report/history/preferences and prior media journal preservation, packaged and installed bundled decoder/exact frame/copy/undo smoke. Native RAW adapters and successful signed auto-update remain unvalidated.'
