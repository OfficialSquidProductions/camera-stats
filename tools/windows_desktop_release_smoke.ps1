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
gh release download v0.2.0-alpha.1 --repo $env:GITHUB_REPOSITORY --pattern Shot-Reporter-Windows-0.2.0-alpha.1.msi --dir $oldDir
if ($LASTEXITCODE -ne 0) {throw 'Previous public MSI download failed'}
Remove-Item Env:GH_TOKEN -ErrorAction SilentlyContinue
$old=Join-Path $oldDir 'Shot-Reporter-Windows-0.2.0-alpha.1.msi'
if ((Get-FileHash -LiteralPath $old -Algorithm SHA256).Hash.ToLowerInvariant() -ne 'f0280917aea08623e801f05e2a1820074efeb5d312320ea5eeefe442564fea5d') {throw 'Previous MSI hash mismatch'}
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
$oldMedia=Join-Path $Output 'old-installed-media'
RunApp $installed @('--desktop-release-smoke',('"'+$oldMedia+'"'),('"'+(Join-Path $repo 'fixtures/media/numbered-24.mp4')+'"')) (Join-Path $oldMedia 'PASS.txt')
$priorMedia=Join-Path $data 'desktop-media/alpha1-fixture'
New-Item -ItemType Directory -Path (Split-Path -Parent $priorMedia) -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $oldMedia 'media') -Destination $priorMedia -Recurse
$priorMediaHash=(Get-FileHash -LiteralPath (Join-Path $priorMedia 'media.sqlite3') -Algorithm SHA256).Hash
# Seed a separate synthetic prior desktop journal schema with the current QA command; retain the prior-schema migration check in addition to alpha1’s actual v2 journal.
$p=Start-Process $PackagedLauncher -ArgumentList '--seed-desktop-upgrade-journal' -Wait -PassThru
if ($p.ExitCode -ne 0) {throw 'Synthetic prior journal fixture failed'}
if ((Get-AuthenticodeSignature -LiteralPath $MSI).Status -ne 'NotSigned') {throw 'This release expects an unsigned manual installer; review publisher policy before changing this gate'}
$p=Start-Process "$env:SystemRoot/System32/msiexec.exe" -ArgumentList @('/i',('"'+$MSI+'"'),'/qn','/norestart','/L*v',('"'+(Join-Path $Output 'upgrade-install.log')+'"')) -Wait -PassThru
if ($p.ExitCode -notin 0,3010) {throw "New MSI installation failed: $($p.ExitCode)"}
if ((Get-FileHash -LiteralPath (Join-Path $priorMedia 'media.sqlite3') -Algorithm SHA256).Hash -ne $priorMediaHash) {throw 'MSI upgrade changed the existing alpha1 media journal bytes'}
$installedQA=Join-Path $Output 'installed-runtime'
RunApp $installed @('--self-test',('"'+$installedQA+'"')) (Join-Path $installedQA 'PASS.txt')
$upgrade=Join-Path $Output 'upgrade'
RunApp $installed @('--verify-desktop-upgrade',('"'+(Join-Path $oldQA 'package-test.camreport')+'"'),('"'+$upgrade+'"')) (Join-Path $upgrade 'PASS.txt')
$media=Join-Path $Output 'installed-media'
RunApp $installed @('--desktop-release-smoke',('"'+$media+'"'),('"'+(Join-Path $repo 'fixtures/media/numbered-24.mp4')+'"')) (Join-Path $media 'PASS.txt')
$products=@(Get-ItemProperty 'HKLM:/Software/Microsoft/Windows/CurrentVersion/Uninstall/*' | Where-Object {$_.DisplayName -eq 'Shot Reporter'})
if ($products.Count -ne 1 -or $products[0].DisplayVersion -ne '0.2.1') {throw 'Installed product version/upgrade identity is not unique0.2.1'}
Set-Content -LiteralPath (Join-Path $Output 'PASS.txt') -Value 'PASS: actual previous0.2.0-alpha.1/MSI0.2.0 ->0.2.0-alpha.2/MSI0.2.1 install, isolated report/history/preferences and prior v1 and actual alpha1 v2 media journal preservation, packaged and installed bundled decoder/exact frame/copy/undo smoke. Native RAW adapters and successful signed auto-update remain unvalidated.'
