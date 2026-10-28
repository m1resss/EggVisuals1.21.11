Add-Type -AssemblyName System.IO.Compression.FileSystem
$path = "C:\Users\admin\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged\1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2\minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2.jar"
$archive = [System.IO.Compression.ZipFile]::OpenRead($path)
foreach ($entry in $archive.Entries) {
    if ($entry.Name -like "*BufferRender*" -or $entry.Name -like "*Tessellator*" -or $entry.Name -like "*ModelTransformationMode*") {
        [Console]::WriteLine($entry.FullName)
    }
}
$archive.Dispose()
