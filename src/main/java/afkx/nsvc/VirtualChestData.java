package afkx.nsvc;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class VirtualChestData {
    private static final String FILE_NAME = "nsvc_virtualchests.dat";
    private static VirtualChestData INSTANCE;

    private final Map<String, VirtualChest> chests = new HashMap<>();

    // ---------- 自动保存相关 ----------
    private static final ScheduledExecutorService SAVER_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VirtualChestAutoSave");
                t.setDaemon(true); // 守护线程：JVM 退出时自动结束，避免服务器重启后残留
                return t;
            });
    private static ScheduledFuture<?> autoSaveTask;
    private static MinecraftServer cachedServer;

    private VirtualChestData() {}

    public static VirtualChestData getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new VirtualChestData();
        }
        return INSTANCE;
    }

    public static void load(MinecraftServer server) {
        INSTANCE = new VirtualChestData();
        cachedServer = server;
        Path filePath = getFilePath(server);
        VirtualChestMod.LOGGER.info("尝试从路径加载数据: {}", filePath.toAbsolutePath());
        if (Files.exists(filePath)) {
            try {
                NbtCompound nbt = NbtIo.readCompressed(filePath, net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes());
                NbtList chestList = nbt.getList("chests").orElse(new NbtList());
                RegistryWrapper.WrapperLookup registries = server.getWorld(World.OVERWORLD).getRegistryManager();
                for (int i = 0; i < chestList.size(); i++) {
                    chestList.getCompound(i).ifPresent(chestTag -> {
                        VirtualChest chest = VirtualChest.fromNbt(chestTag, registries);
                        INSTANCE.chests.put(chest.getName(), chest);
                    });
                }
                VirtualChestMod.LOGGER.info("成功加载虚拟箱子数据，共 {} 个箱子。", INSTANCE.chests.size());
            } catch (IOException e) {
                VirtualChestMod.LOGGER.error("加载虚拟箱子数据失败！", e);
            }
        } else {
            VirtualChestMod.LOGGER.info("未找到数据文件，将创建新数据。");
        }
    }

    public static void save(MinecraftServer server) {
        if (INSTANCE == null) {
            VirtualChestMod.LOGGER.warn("尝试保存时 INSTANCE 为空，跳过保存。");
            return;
        }
        Path filePath = getFilePath(server);
        VirtualChestMod.LOGGER.info("正在保存虚拟箱子数据到: {}", filePath.toAbsolutePath());
        try {
            NbtCompound nbt = new NbtCompound();
            NbtList chestList = new NbtList();
            RegistryWrapper.WrapperLookup registries = server.getWorld(World.OVERWORLD).getRegistryManager();
            for (VirtualChest chest : INSTANCE.chests.values()) {
                chestList.add(chest.writeNbt(new NbtCompound(), registries));
            }
            nbt.put("chests", chestList);
            Files.createDirectories(filePath.getParent());
            NbtIo.writeCompressed(nbt, filePath);
            VirtualChestMod.LOGGER.info("成功保存虚拟箱子数据，共 {} 个箱子。", INSTANCE.chests.size());
        } catch (IOException e) {
            VirtualChestMod.LOGGER.error("保存虚拟箱子数据失败！", e);
        }
    }

    // ---------- 自动保存调度 ----------
    public static void scheduleAutoSave(MinecraftServer server) {
        // 取消旧任务
        if (autoSaveTask != null && !autoSaveTask.isDone()) {
            autoSaveTask.cancel(false);
        }
        cachedServer = server;

        // 广播“即将自动保存”提示（黄色）
        broadcastAutoSaveMessage("nsvc.save.auto_start", false);

        // 调度新任务（30 秒后执行）
        autoSaveTask = SAVER_EXECUTOR.schedule(() -> {
            server.execute(() -> {
                try {
                    VirtualChestData.save(server);
                    broadcastAutoSaveMessage("nsvc.save.auto_success", true);
                } catch (Exception e) {
                    VirtualChestMod.LOGGER.error("自动保存失败", e);
                    broadcastAutoSaveMessage("nsvc.save.auto_failed", false);
                } finally {
                    autoSaveTask = null;
                }
            });
        }, 30, TimeUnit.SECONDS);
    }

    // ---------- 按玩家语言广播消息 ----------
    private static void broadcastAutoSaveMessage(String key, boolean success) {
        if (cachedServer == null) return;
        Formatting color = success ? Formatting.GREEN : Formatting.YELLOW;
        for (ServerPlayerEntity player : cachedServer.getPlayerManager().getPlayerList()) {
            Text message = I18n.translate(player, key).copy().formatted(color);
            player.sendMessage(message, false);
        }
        // 控制台使用默认英文
        VirtualChestMod.LOGGER.info(I18n.translate(key).getString());
    }

    public static void shutdown(MinecraftServer server) {
        if (autoSaveTask != null && !autoSaveTask.isDone()) {
            autoSaveTask.cancel(false);
            autoSaveTask = null;
        }
        VirtualChestData.save(server);
        // 不要关闭 SAVER_EXECUTOR，否则单人退出后再进世界会抛 RejectedExecutionException
    }

    // ---------- 业务方法 ----------
    public boolean createChest(String name, VirtualChest.Size size, UUID owner, boolean isPublic) {
        if (chests.containsKey(name)) return false;
        VirtualChest chest = isPublic ? new VirtualChest(name, size) : new VirtualChest(name, size, owner);
        chests.put(name, chest);
        VirtualChestMod.LOGGER.info("已创建箱子 '{}'，所有者: {}，公共: {}", name, owner != null ? owner : "公共", isPublic);
        return true;
    }

    public VirtualChest getChest(String name) {
        return chests.get(name);
    }

    public int getChestCount() {
        return chests.size();
    }

    public Set<String> getChestNames() {
        return chests.keySet();
    }

    public boolean deleteChest(String name) {
        boolean removed = chests.remove(name) != null;
        if (removed) {
            VirtualChestMod.LOGGER.info("已删除箱子 '{}'", name);
        }
        return removed;
    }

    public Collection<VirtualChest> getAllChests() {
        return chests.values();
    }

    private static Path getFilePath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve(FILE_NAME).normalize().toAbsolutePath();
    }
}