<#
.SYNOPSIS
Publishes the commit at the tip of main from this PC, the way CI does
(.github/workflows/android-build.yml, "Publish release"), when CI could not publish it.

.DESCRIPTION
Run it after pushing a release commit to main. It does nothing while the commit's CI run is still
running or after CI has published the commit, so it is safe to run after every release push.

Dry run by default: checks the repo, verifies the signing key against the published certificate,
builds (unit tests, lint, signed release), verifies the APK, and stages lumora-<name>-<code>.apk,
update.json and notes.md under build-out\local-release\<tag>. Add -Publish to create the GitHub
release, keep the five newest releases, and verify the live update feed.

The version code is 1100 + the number of the commit's failed CI run: the code CI would have used,
so later CI runs stay higher. -RunNumber overrides it, for example for a commit pushed with
[skip ci]. Do not re-run that CI run afterwards: it would build the same version code and fail on
the existing tag.

Keystore password sources, in order: $env:LUMORA_KEYSTORE_PASSWORD, the DPAPI file -PasswordFile
(only your Windows account on this PC can decrypt it), or a masked prompt. Create the file once with:
  Read-Host 'Lumora keystore password' -AsSecureString | ConvertFrom-SecureString | Set-Content "$HOME\lumora-ks.sec"

Needs PowerShell 7, JDK 21, the Android SDK build-tools, Python 3 and a signed-in gh CLI.

.EXAMPLE
pwsh -NoProfile -File tools\publish-release.ps1
pwsh -NoProfile -File tools\publish-release.ps1 -SkipChecks -Publish
#>
#Requires -Version 7.2
[CmdletBinding()]
param(
    [int]$RunNumber,
    [switch]$Publish,
    [switch]$SkipChecks,
    [string]$Repo = 'engziada/Lumora',
    [string]$Keystore = "$HOME\lumora-release.jks",
    [string]$PasswordFile = "$HOME\lumora-ks.sec",
    [string]$ExpectedCert = '5d050e8a953f403169982fc6edddae63548f0a98f8c336c597751361aa033e99',
    # Empty: use a JDK 21 found under Program Files. Gradle 8.13 cannot run on JDK 25.
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest

function Step([string]$Text) { Write-Host "== $Text" -ForegroundColor Cyan }
function Require([bool]$Ok, [string]$Message) { if (-not $Ok) { throw $Message } }

# Same note selection and update.json shape as the CI step, so the app parses it identically.
$releaseFilesPy = @'
import hashlib, json, os, subprocess, sys

changelog, apk, code, name, url, commit, cert, out = sys.argv[1:9]

# awk '/^## / { if (seen++) exit; next } seen { print }' CHANGELOG.md, then $(...) trailing-newline strip.
with open(changelog, encoding="utf-8") as f:
    lines = f.read().split("\n")
seen = 0
picked = []
for line in lines:
    if line.startswith("## "):
        seen += 1
        if seen > 1:
            break
        continue
    if seen:
        picked.append(line)
notes = "".join(line + "\n" for line in picked).rstrip("\n")
if not notes:
    notes = subprocess.run(["git", "log", "-1", "--pretty=%s"], cwd=os.path.dirname(changelog),
                           capture_output=True, text=True, check=True).stdout.rstrip("\n")

digest = hashlib.sha256()
with open(apk, "rb") as f:
    for chunk in iter(lambda: f.read(1 << 20), b""):
        digest.update(chunk)
sha = digest.hexdigest()
size = os.path.getsize(apk)

with open(os.path.join(out, "update.json"), "w", encoding="utf-8", newline="\n") as f:
    print(json.dumps({"versionCode": int(code), "versionName": name,
                      "apk": url, "sha256": sha, "size": size,
                      "commit": commit[:7], "notes": notes}, indent=2), file=f)
with open(os.path.join(out, "notes.md"), "w", encoding="utf-8", newline="\n") as f:
    f.write("%s\n\nCommit: %s\nSigning certificate SHA-256: %s\n" % (notes, commit, cert))
print(sha, size, len(notes))
'@

$secretVars = @('LUMORA_KEYSTORE_PASSWORD', 'LUMORA_KEY_PASSWORD')
$touchedVars = @('JAVA_HOME', 'Path', 'GITHUB_RUN_NUMBER', 'LUMORA_KEYSTORE_FILE', 'LUMORA_KEY_ALIAS') + $secretVars
$saved = @{}
foreach ($n in $touchedVars) { $saved[$n] = [Environment]::GetEnvironmentVariable($n) }
$failed = $false
$RepoDir = Split-Path $PSScriptRoot -Parent

Push-Location $RepoDir
try {
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $bt = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory | Where-Object Name -Match '^\d+(\.\d+)+$' |
        Sort-Object { [version]$_.Name } | Select-Object -Last 1
    Require ($null -ne $bt) "No Android build-tools found under $sdk"
    $apksignerJar = Join-Path $bt.FullName 'lib\apksigner.jar'
    $aapt = Join-Path $bt.FullName 'aapt.exe'
    if (-not $JavaHome) {
        $JavaHome = Get-ChildItem "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Java" `
            -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1 -ExpandProperty FullName
    }
    Require ([bool]$JavaHome -and (Test-Path (Join-Path $JavaHome 'bin\keytool.exe'))) 'JDK 21 not found. Pass -JavaHome <path-to-jdk-21>.'
    $java = Join-Path $JavaHome 'bin\java.exe'
    $keytool = Join-Path $JavaHome 'bin\keytool.exe'
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;$env:Path"

    Step 'Checking the repository'
    git fetch --quiet origin
    Require ($LASTEXITCODE -eq 0) 'git fetch failed.'
    $commit = (git rev-parse HEAD).Trim()
    Require ($commit -eq (git rev-parse origin/main).Trim()) 'HEAD is not origin/main. Check out main and pull first.'
    Require (-not (git status --porcelain)) 'The working tree has changes or untracked files. Commit or stash them first.'

    if (-not $RunNumber) {
        # Reusing the number of the commit's CI run keeps local and CI version codes in one sequence.
        $runs = @(gh run list --repo $Repo --workflow android-build.yml --branch main --event push --commit $commit `
                --json number,status,conclusion --jq '.[] | "\(.number) \(.status) \(.conclusion)"')
        Require ($LASTEXITCODE -eq 0) 'gh run list failed.'
        Require ($runs.Count -gt 0) "No CI run found for $commit. If you just pushed, retry in a minute; for a [skip ci] commit pass -RunNumber."
        $num, $status, $conclusion = "$($runs[0])" -split ' '
        Require ($status -eq 'completed') "CI run #$num for this commit is $status. Wait for it to finish, then run this again."
        if ($conclusion -eq 'success') {
            Write-Host "CI run #$num succeeded and published this commit. Nothing to do." -ForegroundColor Green
            return
        }
        $RunNumber = [int]$num
        Write-Host "   CI run #$num ended with '$conclusion'; publishing in its place"
    }
    $code = 1100 + $RunNumber
    $tag = "build-$code"
    $latestTag = gh release view --repo $Repo --json tagName --jq .tagName
    Require ($LASTEXITCODE -eq 0 -and "$latestTag" -match '^build-(\d+)$') 'Could not read the latest release.'
    Require ($code -gt [int]$Matches[1]) "versionCode $code must be above the latest release $latestTag. Raise -RunNumber."
    gh release view $tag --repo $Repo --json tagName 2>$null | Out-Null
    Require ($LASTEXITCODE -ne 0) "Release $tag already exists."
    Write-Host "   $commit, next tag $tag (latest is $latestTag)"

    Step 'Checking the signing key'
    Require (Test-Path $Keystore) "Keystore not found: $Keystore"
    if (-not $env:LUMORA_KEYSTORE_PASSWORD) {
        $secure = if (Test-Path $PasswordFile) {
            (Get-Content $PasswordFile -Raw).Trim() | ConvertTo-SecureString
        } elseif (-not [Console]::IsInputRedirected) {
            Read-Host 'Lumora keystore password' -AsSecureString
        } else {
            throw "No keystore password. Create $PasswordFile (see the script help) or set LUMORA_KEYSTORE_PASSWORD."
        }
        $env:LUMORA_KEYSTORE_PASSWORD = [Net.NetworkCredential]::new('', $secure).Password
    }
    # -storepass:env keeps the password off the command line.
    # Quoted: PowerShell splits an unquoted -J-Duser.language=en at the dot.
    $listing = & $keytool '-J-Duser.language=en' -list -v -keystore $Keystore -storepass:env LUMORA_KEYSTORE_PASSWORD 2>&1 |
        ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) {
        $why = @($listing | Where-Object { $_ -match 'keytool error' }) + @($listing) | Select-Object -First 1
        throw "keytool could not open the keystore: $why"
    }
    $entries = [System.Collections.Generic.List[object]]::new()
    $current = $null
    foreach ($line in $listing) {
        if ($line -match '^Alias name:\s*(.+)$') {
            $current = [pscustomobject]@{ Alias = $Matches[1].Trim(); Type = ''; Sha256 = '' }
            $entries.Add($current)
        } elseif ($current -and $line -match '^Entry type:\s*(.+)$') {
            $current.Type = $Matches[1].Trim()
        } elseif ($current -and -not $current.Sha256 -and $line -match '^\s*SHA256:\s*([0-9A-Fa-f:]+)\s*$') {
            $current.Sha256 = ($Matches[1] -replace ':', '').ToLowerInvariant()
        }
    }
    $keys = @($entries | Where-Object {
            $_.Type -eq 'PrivateKeyEntry' -and (-not $env:LUMORA_KEY_ALIAS -or $_.Alias -eq $env:LUMORA_KEY_ALIAS) })
    Require ($keys.Count -eq 1) "Expected one signing key in the keystore, found $($keys.Count). Set LUMORA_KEY_ALIAS to pick one."
    Require ($keys[0].Sha256 -eq $ExpectedCert) "The keystore certificate ($($keys[0].Sha256)) is not the published one ($ExpectedCert). Installed apps would refuse this update."
    $env:LUMORA_KEY_ALIAS = $keys[0].Alias
    # PKCS12 keystores made by keytool use the store password for the key too.
    if (-not $env:LUMORA_KEY_PASSWORD) { $env:LUMORA_KEY_PASSWORD = $env:LUMORA_KEYSTORE_PASSWORD }
    $env:LUMORA_KEYSTORE_FILE = (Resolve-Path $Keystore).Path
    $env:GITHUB_RUN_NUMBER = "$RunNumber"
    Write-Host "   key '$($keys[0].Alias)' matches the published certificate"

    # @() keeps a one-task list an array; a bare string would splat character by character.
    $tasks = @(if ($SkipChecks) { 'assembleRelease' } else { 'testDebugUnitTest', 'lintDebug', 'assembleRelease' })
    Step "Building versionCode $code ($($tasks -join ', '))"
    $apkIn = Join-Path $RepoDir 'build-out\app\outputs\apk\release\app-release.apk'
    Remove-Item $apkIn -ErrorAction SilentlyContinue
    # No daemon, so the signing secrets do not outlive this build.
    & .\gradlew.bat @tasks --no-daemon --console=plain -q
    Require ($LASTEXITCODE -eq 0) 'The Gradle build failed.'
    Require (Test-Path $apkIn) "Signed release APK not found: $apkIn"

    Step 'Verifying the APK'
    $signer = & $java -jar $apksignerJar verify --print-certs $apkIn 2>&1 | ForEach-Object { "$_" }
    Require ($LASTEXITCODE -eq 0) "apksigner rejected the APK: $($signer -join ' ')"
    $certLine = $signer | Where-Object { $_ -match 'certificate SHA-256 digest' } | Select-Object -First 1
    $cert = if ($certLine) { ($certLine -split '\s+')[-1] } else { '' }
    Require ($cert -eq $ExpectedCert) "The APK is signed with '$cert', not the published certificate."
    $pkgLine = & $aapt dump badging $apkIn 2>$null | Where-Object { $_ -like 'package:*' } | Select-Object -First 1
    Require ("$pkgLine" -match "name='([^']+)' versionCode='(\d+)' versionName='([^']+)'") 'Could not read the APK version.'
    $pkg, $apkCode, $apkName = $Matches[1], [int]$Matches[2], $Matches[3]
    $name = (Select-String -Path 'app\build.gradle' -Pattern "^ *versionName '([^']+)'" | Select-Object -First 1).Matches[0].Groups[1].Value
    Require ($pkg -eq 'com.firestick.aura.launcher') "Unexpected package $pkg"
    Require ($apkCode -eq $code) "APK versionCode is $apkCode, expected $code"
    Require ($apkName -eq $name) "APK versionName is $apkName, build.gradle says $name"

    Step 'Staging release files'
    $stage = Join-Path $RepoDir "build-out\local-release\$tag"
    if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
    New-Item -ItemType Directory -Path $stage | Out-Null
    $apkFile = Join-Path $stage "lumora-$name-$code.apk"
    Copy-Item $apkIn $apkFile
    $url = "https://github.com/$Repo/releases/download/$tag/lumora-$name-$code.apk"
    $py = Join-Path $stage 'release_files.py'
    Set-Content -Path $py -Value $releaseFilesPy -Encoding utf8
    $summary = & python $py (Join-Path $RepoDir 'CHANGELOG.md') $apkFile $code $name $url $commit $cert $stage
    Require ($LASTEXITCODE -eq 0) 'Writing update.json failed.'
    Remove-Item $py
    $apkSha, $apkSize, $notesLen = "$summary".Trim() -split ' '
    Write-Host "   tag      $tag"
    Write-Host "   title    Lumora $name (build $code)"
    Write-Host "   commit   $commit"
    Write-Host "   apk      $apkFile ($apkSize bytes)"
    Write-Host "   sha256   $apkSha"
    Write-Host "   signer   $cert"
    Write-Host "   notes    $notesLen chars"

    if (-not $Publish) {
        Write-Host 'Dry run only. Rerun with -Publish (add -SkipChecks to skip tests and lint) to create the release.' -ForegroundColor Yellow
        return
    }

    Step "Publishing $tag"
    # Beta/rc builds become pre-releases; the app's update channel follows only the latest release.
    $channel = if ($name -match '-beta|-rc') { '--prerelease' } else { '--latest' }
    gh release create $tag $apkFile (Join-Path $stage 'update.json') --repo $Repo --title "Lumora $name (build $code)" `
        --notes-file (Join-Path $stage 'notes.md') --target $commit $channel
    Require ($LASTEXITCODE -eq 0) 'gh release create failed.'

    Step 'Keeping the five newest releases'
    $old = @(gh release list --repo $Repo --limit 100 --json tagName,createdAt `
            --jq 'sort_by(.createdAt) | reverse | .[5:] | .[].tagName' | Where-Object { $_ })
    foreach ($t in $old) {
        gh release delete $t --repo $Repo --yes --cleanup-tag
        if ($LASTEXITCODE -eq 0) { Write-Host "   deleted $t" } else { Write-Warning "Could not delete $t" }
    }

    Step 'Verifying the live update feed'
    $live = Join-Path $stage 'live'
    New-Item -ItemType Directory -Path $live | Out-Null
    $feed = $null
    foreach ($attempt in 1..5) {
        Invoke-WebRequest "https://github.com/$Repo/releases/latest/download/update.json" -OutFile (Join-Path $live 'update.json')
        $feed = Get-Content (Join-Path $live 'update.json') -Raw | ConvertFrom-Json
        if ($feed.versionCode -eq $code) { break }
        Start-Sleep -Seconds 5
    }
    Require ($feed.versionCode -eq $code -and $feed.sha256 -eq $apkSha) "releases/latest still serves build $($feed.versionCode)."
    Invoke-WebRequest $feed.apk -OutFile (Join-Path $live 'app.apk')
    $liveSha = (Get-FileHash (Join-Path $live 'app.apk') -Algorithm SHA256).Hash.ToLowerInvariant()
    Require ($liveSha -eq $apkSha) 'The downloaded APK does not match update.json.'
    Remove-Item $live -Recurse -Force
    Write-Host "Published https://github.com/$Repo/releases/tag/$tag" -ForegroundColor Green
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    $failed = $true
} finally {
    foreach ($n in $touchedVars) { [Environment]::SetEnvironmentVariable($n, $saved[$n]) }
    Pop-Location
}
if ($failed) { exit 1 }
