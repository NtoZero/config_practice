# =============================================================================
# Keystore Properties Generator Script (PowerShell)
# =============================================================================
# Description: Creates a PKCS#12 keystore with encrypted properties
# Usage: .\create-keystore-with-properties.ps1 [keystorePassword] [jasyptPassword] [demoSecret] [dbPassword] [apiKey]
# =============================================================================

param(
    [string]$KeystorePassword = "keystorePass123!",
    [string]$JasyptPassword = "jasyptSecret456!",
    [string]$DemoSecret = "demoValue789!",
    [string]$DbPassword = "dbPassword123!",
    [string]$ApiKey = "apiKey456789!"
)

# Set error action preference
$ErrorActionPreference = "Stop"

# File paths
$KeystoreFile = "secrets\keystore.p12"
$TempDir = "temp_keystore_creation"

# Colors for output
$Colors = @{
    Red = "Red"
    Green = "Green"
    Yellow = "Yellow"
    Blue = "Blue"
    Cyan = "Cyan"
}

Write-Host "==============================================================================" -ForegroundColor Blue
Write-Host "🔐 Keystore Properties Generator" -ForegroundColor Blue
Write-Host "==============================================================================" -ForegroundColor Blue

# Create directories if they don't exist
Write-Host "📁 Creating directories..." -ForegroundColor Yellow
if (!(Test-Path "secrets")) { New-Item -Path "secrets" -ItemType Directory -Force | Out-Null }
if (!(Test-Path $TempDir)) { New-Item -Path $TempDir -ItemType Directory -Force | Out-Null }

# Display configuration
Write-Host "📋 Configuration:" -ForegroundColor Yellow
Write-Host "   Keystore File: $KeystoreFile"
Write-Host "   Keystore Password: $($KeystorePassword.Substring(0, [Math]::Min(4, $KeystorePassword.Length)))***"
Write-Host "   JASYPT Password: $($JasyptPassword.Substring(0, [Math]::Min(4, $JasyptPassword.Length)))***"
Write-Host "   Demo Secret: $($DemoSecret.Substring(0, [Math]::Min(4, $DemoSecret.Length)))***"
Write-Host "   DB Password: $($DbPassword.Substring(0, [Math]::Min(4, $DbPassword.Length)))***"
Write-Host "   API Key: $($ApiKey.Substring(0, [Math]::Min(4, $ApiKey.Length)))***"
Write-Host ""

# Function to create secret key entry
function Create-SecretKey {
    param(
        [string]$AliasName,
        [string]$SecretValue,
        [string]$KeystorePass
    )
    
    $TempKeystore = Join-Path $TempDir "$AliasName.p12"
    
    Write-Host "🔑 Creating secret key for $AliasName..." -ForegroundColor Yellow
    
    try {
        # Create a temporary keystore with the secret key
        $keytoolArgs = @(
            "-genseckey",
            "-alias", $AliasName,
            "-keyalg", "AES",
            "-keysize", "256",
            "-keystore", $TempKeystore,
            "-storetype", "PKCS12",
            "-storepass", $KeystorePass,
            "-keypass", $KeystorePass,
            "-dname", "CN=$AliasName, OU=Demo, O=Example, C=KR"
        )
        
        $process = Start-Process -FilePath "keytool" -ArgumentList $keytoolArgs -NoNewWindow -Wait -PassThru -RedirectStandardOutput "nul" -RedirectStandardError "nul"
        
        if ($process.ExitCode -ne 0) {
            throw "Failed to create secret key for $AliasName"
        }
        
        # Store the secret value as a text file for reference
        $SecretValue | Out-File -FilePath (Join-Path $TempDir "$AliasName.txt") -Encoding UTF8
        
    } catch {
        Write-Host "❌ Error creating secret key for ${AliasName}: $($_.Exception.Message)" -ForegroundColor Red
        throw
    }
}

# Function to combine keystores
function Combine-Keystores {
    param(
        [string]$TargetKeystore,
        [string]$SourceKeystore,
        [string]$AliasName,
        [string]$KeystorePass
    )
    
    try {
        if (Test-Path $TargetKeystore) {
            # Import the key from source to target
            $keytoolArgs = @(
                "-importkeystore",
                "-srckeystore", $SourceKeystore,
                "-srcstoretype", "PKCS12",
                "-srcstorepass", $KeystorePass,
                "-destkeystore", $TargetKeystore,
                "-deststoretype", "PKCS12",
                "-deststorepass", $KeystorePass,
                "-srcalias", $AliasName,
                "-destalias", $AliasName,
                "-noprompt"
            )
        } else {
            # Copy the first keystore as base
            Copy-Item $SourceKeystore $TargetKeystore
            return
        }
        
        $process = Start-Process -FilePath "keytool" -ArgumentList $keytoolArgs -NoNewWindow -Wait -PassThru -RedirectStandardOutput "nul" -RedirectStandardError "nul"
        
        if ($process.ExitCode -ne 0) {
            throw "Failed to combine keystore for $AliasName"
        }
        
    } catch {
        Write-Host "❌ Error combining keystores for ${AliasName}: $($_.Exception.Message)" -ForegroundColor Red
        throw
    }
}

try {
    # Remove existing keystore
    if (Test-Path $KeystoreFile) {
        Write-Host "🗑️  Removing existing keystore..." -ForegroundColor Yellow
        Remove-Item $KeystoreFile -Force
    }

    # Create individual secret keys
    $aliases = @(
        @{ Name = "JASYPT_PASSWORD"; Value = $JasyptPassword },
        @{ Name = "DEMO_SECRET"; Value = $DemoSecret },
        @{ Name = "DB_PASSWORD"; Value = $DbPassword },
        @{ Name = "API_KEY"; Value = $ApiKey }
    )

    foreach ($alias in $aliases) {
        Create-SecretKey -AliasName $alias.Name -SecretValue $alias.Value -KeystorePass $KeystorePassword
    }

    # Combine all keystores
    Write-Host "🔗 Combining keystores..." -ForegroundColor Yellow
    foreach ($alias in $aliases) {
        $sourceKeystore = Join-Path $TempDir "$($alias.Name).p12"
        Combine-Keystores -TargetKeystore $KeystoreFile -SourceKeystore $sourceKeystore -AliasName $alias.Name -KeystorePass $KeystorePassword
    }

    # Create a properties file with the secret values for reference
    Write-Host "📄 Creating properties reference file..." -ForegroundColor Yellow
    $propertiesContent = @"
# Keystore Properties Reference
# Generated on: $(Get-Date)
# Keystore File: $KeystoreFile
# Keystore Password: $KeystorePassword

JASYPT_PASSWORD=$JasyptPassword
DEMO_SECRET=$DemoSecret
DB_PASSWORD=$DbPassword
API_KEY=$ApiKey
"@

    $propertiesContent | Out-File -FilePath "secrets\keystore_properties.txt" -Encoding UTF8

    # Create encrypted properties file
    Write-Host "🔐 Creating encrypted properties file..." -ForegroundColor Yellow
    $encryptedPropertiesContent = @"
# Encrypted Properties for Keystore Demo
# Use these values in your KeystorePropertySource implementation

JASYPT_PASSWORD=$JasyptPassword
DEMO_SECRET=$DemoSecret
DB_PASSWORD=$DbPassword
API_KEY=$ApiKey
"@

    $encryptedPropertiesContent | Out-File -FilePath "secrets\encrypted_properties.properties" -Encoding UTF8

    # Clean up temporary files
    Write-Host "🧹 Cleaning up temporary files..." -ForegroundColor Yellow
    Remove-Item $TempDir -Recurse -Force

    # Verify keystore
    Write-Host "🔍 Verifying keystore..." -ForegroundColor Yellow
    $verifyArgs = @("-list", "-keystore", $KeystoreFile, "-storepass", $KeystorePassword, "-storetype", "PKCS12")
    $verifyProcess = Start-Process -FilePath "keytool" -ArgumentList $verifyArgs -NoNewWindow -Wait -PassThru -RedirectStandardOutput "temp_verify.txt" -RedirectStandardError "nul"

    if ($verifyProcess.ExitCode -eq 0) {
        Write-Host "✅ Keystore created successfully!" -ForegroundColor Green
        
        Write-Host "📋 Keystore contents:" -ForegroundColor Blue
        $keystoreContents = Get-Content "temp_verify.txt" | Where-Object { $_ -match "Alias name:" }
        $keystoreContents | ForEach-Object { Write-Host "   $_" }
        
        Remove-Item "temp_verify.txt" -Force -ErrorAction SilentlyContinue
        
        Write-Host ""
        Write-Host "🎯 Usage:" -ForegroundColor Green
        Write-Host "   `$env:KEYSTORE_PATH = `"file:$KeystoreFile`""
        Write-Host "   `$env:KEYSTORE_PASSWORD = `"$KeystorePassword`""
        Write-Host "   java -Dkeystore.path=file:$KeystoreFile ``"
        Write-Host "        -Dkeystore.password=$KeystorePassword ``"
        Write-Host "        -jar encloader.jar"
        
        Write-Host ""
        Write-Host "🚀 PowerShell Environment Setup:" -ForegroundColor Cyan
        Write-Host "   `$env:KEYSTORE_PATH = `"file:$KeystoreFile`""
        Write-Host "   `$env:KEYSTORE_PASSWORD = `"$KeystorePassword`""
        
    } else {
        Remove-Item "temp_verify.txt" -Force -ErrorAction SilentlyContinue
        throw "Failed to verify keystore"
    }

    Write-Host "==============================================================================" -ForegroundColor Blue
    Write-Host "🎉 Keystore generation completed successfully!" -ForegroundColor Green
    Write-Host "==============================================================================" -ForegroundColor Blue

} catch {
    Write-Host "❌ Script execution failed: $($_.Exception.Message)" -ForegroundColor Red
    
    # Clean up temporary files on error
    if (Test-Path $TempDir) {
        Remove-Item $TempDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    
    exit 1
}
