<#
  apk.ps1 - Genera el APK de la app con Ctrl+Shift+B en VS Code.
  Lo instala la skill "apk-ctrl-shift-b". La configuracion esta en apk.config.json (raiz del repo).

  Uso (VS Code lo llama solo con Ctrl+Shift+B y un menu):
    powershell -ExecutionPolicy Bypass -File tools\apk\apk.ps1 -Modo GitHub -Tipo debug
    powershell -ExecutionPolicy Bypass -File tools\apk\apk.ps1 -Modo Local  -Tipo release -Instalar

  -Modo GitHub : compila en GitHub Actions (no necesita Java ni Android Studio en el PC) y baja el APK.
                 OJO: compila lo que esta EN GITHUB, no lo que tienes sin commit (el script avisa).
                 Requisito una sola vez:  winget install --id GitHub.cli   y luego   gh auth login
  -Modo Local  : compila en este PC (Java + Android SDK; Flutter SDK si el proyecto es Flutter).
  -Tipo debug  : APK de prueba (se instala a mano en cualquier celular).
  -Tipo release: APK firmado con tu llave (local: keystore.properties; GitHub: secrets ANDROID_*).
  -Instalar    : (solo Local) al terminar lo instala y abre en el emulador o celular conectado.

  Problemas de Windows que resuelve en modo Local:
   - Gradle/Android fallan si la ruta tiene tildes (C:\Users\MichaelGomez con tilde): el repo se
     mapea a una letra de unidad temporal (subst) y la cache de Gradle va en C:\gradle-home.
   - Busca un JDK dentro del rango del proyecto (javaMin..javaMax de apk.config.json).
#>
param(
  [ValidateSet('GitHub', 'Local')][string]$Modo = 'GitHub',
  [ValidateSet('debug', 'release')][string]$Tipo = 'debug',
  [switch]$Instalar
)
$ErrorActionPreference = 'Stop'

function Falla([string]$m) { Write-Host ''; Write-Host "[ERROR] $m" -ForegroundColor Red; exit 1 }
function Paso([string]$m) { Write-Host ''; Write-Host "== $m" -ForegroundColor Cyan }
function Pregunta([string]$m) { (Read-Host "$m (s/N)").Trim().ToLower() -eq 's' }

# Ejecuta un comando externo (npm, npx, gradlew, flutter, apksigner). En PowerShell 5 los avisos que
# estos escriben en stderr NO deben cortar el script: se corta solo por el codigo de salida.
function Invoke-Paso([string]$Descripcion, [scriptblock]$Comando) {
  Write-Host ''
  Write-Host "== $Descripcion" -ForegroundColor Cyan
  $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  & $Comando
  $codigo = $LASTEXITCODE
  $ErrorActionPreference = $prev
  if ($codigo -ne 0) { throw "Fallo: $Descripcion (codigo $codigo)" }
}

function Read-Properties([string]$ruta) {
  $props = @{}
  foreach ($linea in Get-Content $ruta) {
    $l = $linea.Trim()
    if (-not $l -or $l.StartsWith('#')) { continue }
    $i = $l.IndexOf('=')
    if ($i -gt 0) { $props[$l.Substring(0, $i).Trim()] = $l.Substring($i + 1).Trim() }
  }
  return $props
}

# ---------------------------------------------------------------------------
# Configuracion (apk.config.json en la raiz del repo)
# ---------------------------------------------------------------------------
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)   # tools\apk -> tools -> repo
$cfgRuta = Join-Path $repo 'apk.config.json'
if (-not (Test-Path $cfgRuta)) { Falla "No existe $cfgRuta. Pidele a Claude: 'configura Ctrl+Shift+B para generar el APK'." }
$cfg = Get-Content $cfgRuta -Raw -Encoding UTF8 | ConvertFrom-Json
function Valor([string]$campo, $defecto) {
  $p = $cfg.PSObject.Properties[$campo]
  if ($p -and $null -ne $p.Value -and "$($p.Value)" -ne '') { return $p.Value }
  return $defecto
}
$tipoProyecto = Valor 'tipo' 'capacitor'          # capacitor | react-native | expo | flutter | android
$carpetaRel   = Valor 'carpeta' '.'               # carpeta de la app dentro del repo
$nombre       = Valor 'nombre' 'app'              # prefijo del archivo .apk
$rama         = Valor 'rama' 'main'
$workflow     = Valor 'workflow' 'apk.yml'
$artefacto    = Valor 'artefacto' 'apk'
$javaMin      = [int](Valor 'javaMin' 17)
$javaMax      = [int](Valor 'javaMax' 21)
$buildWeb     = Valor 'buildWeb' 'npm run build'  # capacitor: comando que deja la web lista (webDir)
$envArchivo   = Valor 'envArchivo' ''             # capacitor/RN/expo: .env que el build necesita
$antesGradle  = @(Valor 'antesDeGradle' @())      # comandos extra antes de compilar (en la carpeta de la app)
$modulo       = Valor 'modulo' 'app'              # modulo Android con la app (casi siempre "app")
$syncVersion  = [bool](Valor 'sincronizarVersion' ($tipoProyecto -eq 'capacitor'))

$app = if ($carpetaRel -eq '.' -or -not $carpetaRel) { $repo } else { Join-Path $repo $carpetaRel }
$rel = if ($carpetaRel -eq '.' -or -not $carpetaRel) { '' } else { ($carpetaRel -replace '\\', '/').TrimEnd('/') + '/' }
if (-not (Test-Path $app)) { Falla "No existe la carpeta de la app: $app (revisa 'carpeta' en apk.config.json)" }

# Version legible de la app (para el nombre del .apk)
function Get-VersionDe([string]$texto, [string]$archivo) {
  if (-not $texto) { return '0.0.0' }
  if ($archivo -like '*package.json') { try { return (($texto | ConvertFrom-Json).version) } catch { return '0.0.0' } }
  if ($archivo -like '*pubspec.yaml') { if ($texto -match '(?m)^version:\s*([0-9][^\s+]*)') { return $Matches[1] } }
  if ($archivo -like '*build.gradle*') { if ($texto -match 'versionName\s*=?\s*"([^"]+)"') { return $Matches[1] } }
  return '0.0.0'
}
function Get-ArchivoVersion {
  switch ($tipoProyecto) {
    'flutter' { return "${rel}pubspec.yaml" }
    'android' {
      if (Test-Path (Join-Path $app "$modulo\build.gradle.kts")) { return "${rel}$modulo/build.gradle.kts" }
      return "${rel}$modulo/build.gradle"
    }
    default { return "${rel}package.json" }
  }
}

# ===========================================================================
#  MODO GITHUB: compila en GitHub Actions y descarga el APK
# ===========================================================================
if ($Modo -eq 'GitHub') {
  Set-Location $repo
  if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Falla 'No encuentro git (winget install --id Git.Git).' }
  if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Falla ("Falta GitHub CLI (gh). Instalalo una vez:`n" +
           "   winget install --id GitHub.cli`n" +
           "   (cierra y vuelve a abrir VS Code)`n" +
           "   gh auth login     -> GitHub.com, HTTPS, Login with a web browser")
  }
  $ErrorActionPreference = 'Continue'
  gh auth status *> $null
  if ($LASTEXITCODE -ne 0) { Falla 'GitHub CLI sin sesion. Ejecuta una vez en la terminal:  gh auth login' }
  git remote get-url origin *> $null
  if ($LASTEXITCODE -ne 0) { Falla 'Este repo no tiene remoto en GitHub (origin). Crea el repo y: git remote add origin <url> ; git push -u origin main' }

  Paso "1/4 Revisando que tus cambios esten en GitHub (rama $rama)"
  $actual = (git rev-parse --abbrev-ref HEAD).Trim()
  if ($actual -ne $rama) { Write-Host "Estas en la rama '$actual', pero GitHub compila '$rama'." -ForegroundColor Yellow }
  $sinCommit = git status --porcelain
  if ($sinCommit) {
    Write-Host 'Archivos con cambios SIN commit (NO van a quedar en el APK):' -ForegroundColor Yellow
    $sinCommit | Select-Object -First 25 | ForEach-Object { Write-Host "   $_" }
    if (-not (Pregunta 'Compilar igual lo que ya esta en GitHub?')) { Falla 'Cancelado. Haz commit y vuelve a Ctrl+Shift+B.' }
  }
  git fetch -q origin $rama
  if ($LASTEXITCODE -ne 0) { Falla "No se pudo consultar GitHub (git fetch origin $rama). Revisa internet / que la rama exista en GitHub." }
  if ($actual -eq $rama) {
    $sinPush = [int](git rev-list --count "origin/$rama..HEAD")
    if ($sinPush -gt 0) {
      Write-Host "Tienes $sinPush commit(s) que NO estan en GitHub:" -ForegroundColor Yellow
      git log --oneline "origin/$rama..HEAD" | ForEach-Object { Write-Host "   $_" }
      if (Pregunta 'Hacer git push ahora para incluirlos?') {
        git push origin $rama
        if ($LASTEXITCODE -ne 0) { Falla 'git push fallo.' }
        git fetch -q origin $rama
      } else {
        Write-Host 'Se compila lo que ya esta en GitHub (sin esos commits).' -ForegroundColor Yellow
      }
    }
  }
  git cat-file -e "origin/${rama}:.github/workflows/$workflow" 2> $null
  if ($LASTEXITCODE -ne 0) {
    Falla ".github/workflows/$workflow todavia no esta en GitHub (rama $rama). Haz commit y push de .github, tools/apk y apk.config.json."
  }
  $commit = (git rev-parse --short "origin/$rama").Trim()
  $archVer = Get-ArchivoVersion
  $version = Get-VersionDe ((git show "origin/${rama}:$archVer" 2> $null) -join "`n") $archVer
  Write-Host "Version $version, commit $commit, APK $Tipo"

  Paso "2/4 Lanzando el workflow $workflow en GitHub Actions"
  $antes = (Get-Date).ToUniversalTime().AddSeconds(-60)
  gh workflow run $workflow --ref $rama -f "tipo=$Tipo"
  if ($LASTEXITCODE -ne 0) { Falla 'No se pudo lanzar el workflow (permisos del repo, nombre del archivo o el workflow no tiene el input "tipo").' }
  $id = $null
  for ($i = 0; $i -lt 40 -and -not $id; $i++) {
    Start-Sleep -Seconds 3
    $runs = gh run list --workflow $workflow --branch $rama --event workflow_dispatch --limit 5 --json databaseId,createdAt | ConvertFrom-Json
    $nuevo = $runs | Where-Object { ([datetime]$_.createdAt).ToUniversalTime() -ge $antes } | Select-Object -First 1
    if ($nuevo) { $id = $nuevo.databaseId }
  }
  if (-not $id) { Falla 'GitHub no mostro la ejecucion. Revisa la pestana Actions del repo.' }

  Paso "3/4 Compilando en GitHub (ejecucion $id). Tarda unos 5 a 10 minutos..."
  gh run watch $id --exit-status --interval 20
  if ($LASTEXITCODE -ne 0) {
    Write-Host ''
    Write-Host 'Ultimas lineas del paso que fallo:' -ForegroundColor Yellow
    gh run view $id --log-failed | Select-Object -Last 60
    Write-Host ''
    Write-Host "Si el error es de red de GitHub (502/503/504 bajando Gradle o dependencias), reintenta:  gh run rerun $id --failed" -ForegroundColor Yellow
    Falla "La compilacion fallo. Detalle completo:  gh run view $id --web"
  }

  Paso '4/4 Descargando el APK'
  $tmp = Join-Path $env:TEMP "apk_$id"
  if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
  gh run download $id -n $artefacto -D $tmp
  if ($LASTEXITCODE -ne 0) { Falla "No se pudo descargar el artefacto '$artefacto' del run." }
  $apk = Get-ChildItem $tmp -Filter *.apk -Recurse | Select-Object -First 1
  if (-not $apk) { Falla 'El artefacto no trae ningun .apk.' }
  $destDir = Join-Path $app 'apk'
  New-Item -ItemType Directory -Force $destDir | Out-Null
  $dest = Join-Path $destDir "$nombre-v$version-$commit-$Tipo.apk"
  Copy-Item $apk.FullName $dest -Force
  Remove-Item -Recurse -Force $tmp
  Write-Host ''
  Write-Host "LISTO: $dest" -ForegroundColor Green
  Write-Host ("{0:N1} MB" -f ((Get-Item $dest).Length / 1MB))
  try { Start-Process explorer.exe -ArgumentList "/select,`"$dest`"" } catch { }
  exit 0
}

# ===========================================================================
#  MODO LOCAL: compila en este PC
# ===========================================================================
$codigoSalida = 0
$letra = $null
$pushed = $false
try {
  # ---- Herramientas ----
  if ($tipoProyecto -in @('capacitor', 'react-native', 'expo') -and -not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw 'Falta Node.js (winget install OpenJS.NodeJS.LTS).'
  }
  if ($tipoProyecto -eq 'flutter' -and -not (Get-Command flutter -ErrorAction SilentlyContinue)) {
    throw 'Falta Flutter SDK en el PATH (https://docs.flutter.dev/get-started/install/windows).'
  }
  if ($envArchivo -and -not (Test-Path (Join-Path $app $envArchivo))) {
    throw "Falta $(Join-Path $app $envArchivo) (variables del build; no se sube a git)."
  }

  # ---- Firma (release) ----
  $firma = $null
  if ($Tipo -eq 'release') {
    $kp = Join-Path $app 'keystore.properties'
    if (-not (Test-Path $kp)) { throw "Falta $kp para firmar (storeFile, storePassword, keyAlias, keyPassword). Ver la skill: references/firma.md" }
    $firma = Read-Properties $kp
    foreach ($k in 'storeFile', 'storePassword', 'keyAlias', 'keyPassword') { if (-not $firma[$k]) { throw "keystore.properties: falta '$k'." } }
    $ks = $firma['storeFile']
    if (-not [IO.Path]::IsPathRooted($ks)) { $ks = Join-Path $app $ks }
    if (-not (Test-Path $ks)) { throw "No existe el keystore: $ks" }
    $firma['storeFile'] = (Resolve-Path $ks).Path
  }

  # ---- Java dentro del rango del proyecto ----
  function Get-JavaMajor([string]$jhome) {
    $exe = Join-Path $jhome 'bin\java.exe'
    if (-not (Test-Path $exe)) { return 0 }
    $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $txt = (& $exe -version 2>&1 | Out-String)
    $ErrorActionPreference = $prev
    if ($txt -match 'version "(\d+)(\.(\d+))?') { $m = [int]$Matches[1]; if ($m -eq 1) { $m = [int]$Matches[3] }; return $m }
    return 0
  }
  $candidatos = @($env:JAVA_HOME, 'C:\Program Files\Android\Android Studio\jbr')
  foreach ($base in 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', 'C:\Program Files\Microsoft', 'C:\Program Files\Zulu') {
    if (Test-Path $base) { $candidatos += (Get-ChildItem $base -Directory -Filter 'jdk*' | Sort-Object Name -Descending | ForEach-Object FullName) }
  }
  $javaOk = $null
  foreach ($c in $candidatos) {
    if (-not $c) { continue }
    $v = Get-JavaMajor $c
    if ($v -ge $javaMin -and $v -le $javaMax) { $javaOk = $c; break }
  }
  if (-not $javaOk) { throw "No hay un JDK entre $javaMin y $javaMax. Instala uno: winget install EclipseAdoptium.Temurin.$javaMin.JDK" }
  $env:JAVA_HOME = $javaOk
  $env:Path = (Join-Path $javaOk 'bin') + ';' + $env:Path
  Write-Host "Java: $javaOk" -ForegroundColor Cyan

  $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
  if (-not (Test-Path $sdk)) { throw "No se encontro el Android SDK en $sdk. Instala Android Studio y abrelo una vez." }
  $env:ANDROID_HOME = $sdk

  # ---- Rutas con tilde: unidad temporal + cache de Gradle fuera del perfil ----
  $base = $repo
  if ($repo -match '[^\x00-\x7F]') {
    foreach ($l in 'M', 'N', 'P', 'Q', 'R', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z') { if (-not (Test-Path "${l}:\")) { $letra = "${l}:"; break } }
    if (-not $letra) { throw 'La ruta del repo tiene tildes y no hay letras de unidad libres para subst.' }
    subst $letra "$repo"
    $base = "$letra\"
    Write-Host "Repo mapeado en $letra\ (la ruta tiene tildes)" -ForegroundColor Cyan
  }
  if (-not $env:GRADLE_USER_HOME -and ($env:USERPROFILE -match '[^\x00-\x7F]')) {
    $env:GRADLE_USER_HOME = 'C:\gradle-home'
    New-Item -ItemType Directory -Force -Path $env:GRADLE_USER_HOME | Out-Null
  }
  $appB = if ($rel) { Join-Path $base ($rel.TrimEnd('/') -replace '/', '\') } else { $base }
  Push-Location $appB
  $pushed = $true

  function Set-LocalProperties([string]$androidDir) {
    $lp = Join-Path $androidDir 'local.properties'
    if (Test-Path $lp) { return }
    $esc = ($sdk -replace '\\', '\\' -replace ':', '\:')
    $esc = -join ($esc.ToCharArray() | ForEach-Object { if ([int]$_ -gt 127) { '\u{0:x4}' -f [int]$_ } else { $_ } })
    "sdk.dir=$esc" | Set-Content -Encoding ASCII $lp
  }
  # versionName/versionCode de Android = version de package.json (1.3.0 -> 10300)
  function Sync-VersionAndroid([string]$androidDir) {
    $pj = Join-Path $appB 'package.json'
    if (-not (Test-Path $pj)) { return }
    $ver = (Get-Content $pj -Raw | ConvertFrom-Json).version
    if (-not $ver) { return }
    $p = $ver.Split('.') + @('0', '0', '0')
    $code = ([int]($p[0] -replace '\D', '')) * 10000 + ([int]($p[1] -replace '\D', '')) * 100 + [int]($p[2] -replace '\D.*$', '')
    foreach ($g in @((Join-Path $androidDir "$modulo\build.gradle"), (Join-Path $androidDir "$modulo\build.gradle.kts"))) {
      if (-not (Test-Path $g)) { continue }
      $t = Get-Content $g -Raw
      if ($g -like '*.kts') { $t2 = $t -replace 'versionCode\s*=\s*\d+', "versionCode = $code" -replace 'versionName\s*=\s*"[^"]*"', "versionName = `"$ver`"" }
      else { $t2 = $t -replace 'versionCode\s+\d+', "versionCode $code" -replace 'versionName\s+"[^"]*"', "versionName `"$ver`"" }
      if ($t2 -ne $t) { [IO.File]::WriteAllText($g, $t2, (New-Object Text.UTF8Encoding $false)) }
      Write-Host "Android: versionName $ver, versionCode $code"
    }
  }
  function Invoke-Gradle([string]$dir) {
    $tarea = if ($Tipo -eq 'release') { 'assembleRelease' } else { 'assembleDebug' }
    Push-Location $dir
    try {
      $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
      .\gradlew.bat --stop | Out-Null   # daemons viejos (otro Java)
      $ErrorActionPreference = $prev
      Invoke-Paso "gradlew $tarea" { .\gradlew.bat $tarea }
    } finally { Pop-Location }
  }
  function Invoke-Extra { foreach ($c in $antesGradle) { if ($c) { Invoke-Paso $c { cmd.exe /d /c $c } } } }

  # ---- Compilar segun el tipo de proyecto ----
  $salida = $null
  switch ($tipoProyecto) {
    'capacitor' {
      if (-not (Test-Path 'node_modules')) { Invoke-Paso 'npm install (primera vez)' { npm install --no-audit --no-fund } }
      Invoke-Paso "Web: $buildWeb" { cmd.exe /d /c $buildWeb }
      if (-not (Test-Path 'android')) { Invoke-Paso 'Proyecto Android (npx cap add android)' { npx cap add android } }
      Invoke-Paso 'npx cap sync android' { npx cap sync android }
      Invoke-Extra
      if ($syncVersion) { Sync-VersionAndroid (Join-Path $appB 'android') }
      Set-LocalProperties (Join-Path $appB 'android')
      Invoke-Gradle (Join-Path $appB 'android')
      $salida = Join-Path $appB "android\$modulo\build\outputs\apk\$Tipo"
    }
    'react-native' {
      if (-not (Test-Path 'node_modules')) { Invoke-Paso 'npm install (primera vez)' { npm install --no-audit --no-fund } }
      Invoke-Extra
      if ($syncVersion) { Sync-VersionAndroid (Join-Path $appB 'android') }
      Set-LocalProperties (Join-Path $appB 'android')
      Invoke-Gradle (Join-Path $appB 'android')
      $salida = Join-Path $appB "android\$modulo\build\outputs\apk\$Tipo"
    }
    'expo' {
      if (-not (Test-Path 'node_modules')) { Invoke-Paso 'npm install (primera vez)' { npm install --no-audit --no-fund } }
      if (-not (Test-Path 'android')) { Invoke-Paso 'npx expo prebuild --platform android' { npx expo prebuild --platform android --no-install } }
      Invoke-Extra
      Set-LocalProperties (Join-Path $appB 'android')
      Invoke-Gradle (Join-Path $appB 'android')
      $salida = Join-Path $appB "android\$modulo\build\outputs\apk\$Tipo"
    }
    'android' {
      Invoke-Extra
      Set-LocalProperties $appB
      Invoke-Gradle $appB
      $salida = Join-Path $appB "$modulo\build\outputs\apk\$Tipo"
    }
    'flutter' {
      Invoke-Paso 'flutter pub get' { flutter pub get }
      Invoke-Extra
      Invoke-Paso "flutter build apk --$Tipo" { flutter build apk "--$Tipo" }
      $salida = Join-Path $appB 'build\app\outputs\flutter-apk'
    }
    default { throw "Tipo de proyecto desconocido en apk.config.json: '$tipoProyecto' (capacitor, react-native, expo, flutter o android)." }
  }

  $origen = Get-ChildItem (Join-Path $salida '*.apk') -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match $Tipo } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if (-not $origen) { throw "El build no dejo ningun APK en $salida" }

  # ---- Copiar (y firmar el release) a <app>\apk\ ----
  $archVer = Get-ArchivoVersion
  $rutaVer = Join-Path $repo ($archVer -replace '/', '\')
  $version = Get-VersionDe ($(if (Test-Path $rutaVer) { Get-Content $rutaVer -Raw } else { '' })) $archVer
  $destDir = Join-Path $app 'apk'
  New-Item -ItemType Directory -Force -Path $destDir | Out-Null
  $dest = Join-Path $destDir ("$nombre-v$version-$Tipo-" + (Get-Date -Format 'yyyyMMdd-HHmm') + '.apk')
  if ($Tipo -eq 'release') {
    $bt = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory | Sort-Object { [version]($_.Name -replace '[^0-9.].*$', '') } -Descending | Select-Object -First 1
    if (-not $bt) { throw 'No hay Android SDK Build-Tools (Android Studio > SDK Manager > SDK Tools).' }
    $zipalign = Join-Path $bt.FullName 'zipalign.exe'
    $apksigner = Join-Path $bt.FullName 'apksigner.bat'
    $alineado = Join-Path $env:TEMP "$nombre-alineado.apk"
    Invoke-Paso 'zipalign' { & $zipalign -p -f 4 $origen.FullName $alineado }
    # Las claves van por variables de entorno (no quedan en la linea de comandos ni en el historial)
    $env:APK_KS_PASS = $firma['storePassword']; $env:APK_KEY_PASS = $firma['keyPassword']
    try {
      Invoke-Paso 'Firmando (apksigner)' { & $apksigner sign --ks $firma['storeFile'] --ks-key-alias $firma['keyAlias'] --ks-pass env:APK_KS_PASS --key-pass env:APK_KEY_PASS --out $dest $alineado }
    } finally {
      Remove-Item Env:\APK_KS_PASS, Env:\APK_KEY_PASS -ErrorAction SilentlyContinue
      Remove-Item $alineado -ErrorAction SilentlyContinue
    }
    Invoke-Paso 'Verificando firma' { & $apksigner verify $dest }
  } else {
    Copy-Item $origen.FullName $dest -Force
  }
  Write-Host ''
  Write-Host "LISTO: $dest" -ForegroundColor Green
  Write-Host ("{0:N1} MB" -f ((Get-Item $dest).Length / 1MB))
  if ($Instalar) {
    & (Join-Path $PSScriptRoot 'emulador.ps1') -Instalar -Apk $dest
  } else {
    try { Start-Process explorer.exe -ArgumentList "/select,`"$dest`"" } catch { }
  }
}
catch {
  Write-Host ''
  Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
  $codigoSalida = 1
}
finally {
  if ($pushed) { Pop-Location -ErrorAction SilentlyContinue }
  if ($letra) { subst $letra /D }
}
exit $codigoSalida
