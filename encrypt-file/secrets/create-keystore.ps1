<#
# ---------------------------------------------------------------------------
# create-keystore.ps1 — Windows Server용 .p12 키스토어 생성 스크립트
# 실행: 관리자 PowerShell (Run as Administrator)
# ---------------------------------------------------------------------------
#>

$ErrorActionPreference = 'Stop'

Write-Host "🔐 PKCS#12 키스토어 생성을 시작합니다..." -ForegroundColor Cyan

# ✅ 1) 32바이트 난수 기반 STOREPASS 생성
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$STOREPASS = [Convert]::ToBase64String($bytes)

# ✅ 2) 비밀번호를 안전한 파일에 저장 (현재 경로)
$passFile = '.\keystore_pass.txt'
[IO.File]::WriteAllText($passFile, $STOREPASS)
# 현재 사용자만 읽기 권한
icacls $passFile /inheritance:r | Out-Null
icacls $passFile /grant:r "$Env:USERNAME:(R)" | Out-Null

Write-Host "📝 키스토어 비밀번호를 keystore_pass.txt 파일에 저장했습니다." -ForegroundColor Green

# ✅ 3) keytool 경로 확인
$keytool = "$Env:JAVA_HOME\bin\keytool.exe"
if (-not (Test-Path $keytool)) { 
    Throw "keytool.exe not found — JAVA_HOME 환경변수를 확인하세요" 
}

# ✅ 4) .p12 키스토어 생성
Write-Host "🔧 키스토어를 생성 중입니다..." -ForegroundColor Yellow
& $keytool `
  -genkeypair `
  -alias jasypt-key `
  -storetype PKCS12 `
  -keystore keystore.p12 `
  -storepass $STOREPASS `
  -keyalg RSA -keysize 4096 `
  -validity 3650 `
  -dname "CN=jasypt, OU=Dev, O=YourOrg, L=Seoul, C=KR"

Remove-Variable STOREPASS  # 메모리 해제
Write-Host "" 
Write-Host "✅ [완료] keystore.p12 생성 완료 — 비밀번호는 $passFile 파일에 저장되었습니다." -ForegroundColor Green

Write-Host ""
Write-Host "🔧 다음 단계:" -ForegroundColor Cyan
Write-Host "1. `$env:JASYPT_STOREPASS = Get-Content keystore_pass.txt 명령으로 환경변수 설정"
Write-Host "2. 애플리케이션 시작: cd .. && .\gradlew.bat bootRun --args='--spring.profiles.active=local'"
Write-Host ""
Write-Host "🛡️  보안 주의사항:" -ForegroundColor Yellow
Write-Host "- keystore_pass.txt 파일과 keystore.p12 파일은 절대 버전 관리에 포함하지 마세요"
Write-Host "- 운영 환경에서는 적절한 권한 설정을 하세요"
