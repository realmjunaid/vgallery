Add-Type -AssemblyName System.Drawing

# The Windows icon is the Android launcher logo, so both apps look like one
# product. `vgallery_logo_fg.png` is the 1080x1080 adaptive-icon foreground;
# the icon itself sits at 196,196 .. 882,882 on a black matte, which we crop,
# scale to 1024 and round off with a transparent squircle.

$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root "app\src\main\res\drawable-nodpi\vgallery_logo_fg.png"
if (-not (Test-Path $source)) {
    throw "Android logo not found at $source"
}

$size = 1024
$cropX = 205
$cropY = 205
$cropSize = 670
$radius = 265

$src = [System.Drawing.Bitmap]::FromFile($source)
$bmp = New-Object System.Drawing.Bitmap($size, $size)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)

# Transparent squircle, a touch rounder than the source so no black matte
# survives in the corners.
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$d = $radius * 2
$path.AddArc(0, 0, $d, $d, 180, 90)
$path.AddArc($size - $d, 0, $d, $d, 270, 90)
$path.AddArc($size - $d, $size - $d, $d, $d, 0, 90)
$path.AddArc(0, $size - $d, $d, $d, 90, 90)
$path.CloseFigure()

$g.SetClip($path)
$g.DrawImage(
    $src,
    (New-Object System.Drawing.Rectangle(0, 0, $size, $size)),
    (New-Object System.Drawing.Rectangle($cropX, $cropY, $cropSize, $cropSize)),
    [System.Drawing.GraphicsUnit]::Pixel
)
$g.ResetClip()

$g.Dispose()
$src.Dispose()
$out = Join-Path $PSScriptRoot "icon-source.png"
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
"wrote $out"
