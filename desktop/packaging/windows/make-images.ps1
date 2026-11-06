# Makes the small bitmaps of the installer window from the Daybreak logo (Inno Setup wants BMP).
# Run it again only when the logo changes; the BMPs are committed.
#   powershell -File desktop/packaging/windows/make-images.ps1
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
$logo = [System.Drawing.Image]::FromFile((Resolve-Path (Join-Path $root '..\src\main\resources\daybreak-icon.png')))
foreach ($size in 55, 64, 80, 96, 110) {
    $bitmap = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bitmap)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::White)
    $g.DrawImage($logo, 0, 0, $size, $size)
    $g.Dispose()
    $bitmap.Save((Join-Path $PSScriptRoot "wizard-small-$size.bmp"), [System.Drawing.Imaging.ImageFormat]::Bmp)
    $bitmap.Dispose()
}
$logo.Dispose()
