Add-Type -AssemblyName System.IO.Compression.FileSystem
$path = "C:\Users\admin\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged\1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2\minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2.jar"
$archive = [System.IO.Compression.ZipFile]::OpenRead($path)
foreach ($e in $archive.Entries) {
    if ($e.Name -like "*BakedModel*.class") {
        [Console]::WriteLine($e.FullName)
    }
}
$archive.Dispose()
