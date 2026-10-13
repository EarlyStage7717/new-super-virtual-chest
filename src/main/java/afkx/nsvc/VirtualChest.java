package afkx.nsvc;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class VirtualChestMod implements ModInitializer {
    public static final String MOD_ID = "nsvc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("NS Virtual Chest (Server-side only) initializing...");

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            VirtualChestData.load(server);
        });

        // ---------- 修改：停止时调用 shutdown ----------
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            VirtualChestData.shutdown(server);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            VirtualChestCommand.register(dispatcher);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            VirtualChestData data = VirtualChestData.getInstance();
            long currentTime = server.getWorld(World.OVERWORLD).getTime();

            // 先收集要删除的箱子名，避免遍历时修改集合
            List<String> toDelete = new ArrayList<>();
            for (VirtualChest chest : data.getAllChests()) {
                // 公共箱子：等待 3 游戏天后自动删除
                if (chest.isPendingDeletion() && chest.isPublic()) {
                    if (currentTime - chest.getDeletionStartTime() >= 72000L) {
                        toDelete.add(chest.getName());
                    }
                }
                // 私有箱子：确认超时（1 分钟）自动取消待删除
                if (chest.isPendingDeletion() && !chest.isPublic()) {
                    if (chest.getDeletionStartTime() > 0 && currentTime - chest.getDeletionStartTime() > 1200) {
                        chest.setPendingDeletion(false, 0);
                    }
                }
            }

            // 遍历结束后再统一删除
            for (String name : toDelete) {
                data.deleteChest(name);
                LOGGER.info("Public chest '{}' waiting time expired, auto-deleted.", name);
            }
        });

        LOGGER.info("NS Virtual Chest initialized! Admin tag: 'nsvc_admin'");
    }
}
