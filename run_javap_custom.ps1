$javap = 'C:\Program Files\Java\jdk-21.0.11\bin\javap.exe'
$cp = 'C:\Users\admin\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged\1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2\minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2.jar'
& $javap -p -cp $cp 'net.minecraft.client.render.command.OrderedRenderCommandQueue$Custom'
