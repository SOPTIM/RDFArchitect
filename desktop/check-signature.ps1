#
#    Copyright (c) 2024-2026 SOPTIM AG
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#

<#
.SYNOPSIS
Checks that a file carries a valid Authenticode signature, optionally by a given signer.

.DESCRIPTION
Exits with 1 if the signature is missing, invalid or by someone else. A signature without a
timestamp passes with a warning: it stops being valid when the certificate expires.

.PARAMETER Path
The file to check.

.PARAMETER ExpectedSigner
The organization (O=) the subject of the signer's certificate has to name, exactly and as its only
organization, e.g. "SOPTIM AG". Any valid signer passes when it is empty. Checking it needs
PowerShell 7.3 or newer.

.PARAMETER AllowUnsigned
Report a missing or invalid signature as a warning only.
#>
param(
    [Parameter(Mandatory = $true)][string]$Path,
    [string]$ExpectedSigner = "",
    [switch]$AllowUnsigned
)

$ErrorActionPreference = "Stop"

$signature = Get-AuthenticodeSignature -FilePath $Path
$signer = if ($signature.SignerCertificate) { $signature.SignerCertificate.Subject } else { "nobody" }
Write-Host "$($Path): $($signature.Status), signed by $signer"

if ($signature.Status -ne "Valid") {
    if ($AllowUnsigned) {
        Write-Host "::warning::$($Path) is not validly signed ($($signature.Status)), accepted as asked."
        exit 0
    }
    Write-Host "::error::$($Path) is not validly signed: $($signature.Status). $($signature.StatusMessage)"
    exit 1
}
if ($ExpectedSigner) {
    # 2.5.4.10 is the object identifier of the organization (O=).
    $organizations = @(
        $signature.SignerCertificate.SubjectName.EnumerateRelativeDistinguishedNames($true) |
            Where-Object { -not $_.HasMultipleElements -and $_.GetSingleElementType().Value -eq "2.5.4.10" } |
            ForEach-Object { $_.GetSingleElementValue() }
    )
    if ($organizations.Count -ne 1 -or $organizations[0] -cne $ExpectedSigner) {
        Write-Host "::error::$($Path) is signed by $signer, not by the organization $ExpectedSigner."
        exit 1
    }
}
if (-not $signature.TimeStamperCertificate) {
    Write-Host "::warning::$($Path) carries no timestamp, so its signature ends with the certificate."
}
exit 0
