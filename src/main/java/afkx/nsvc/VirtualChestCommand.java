package afkx.nsvc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class VirtualChestCommand {
    private static final long THREE_DAYS_TICKS = 72000L;
    private static final String ADMIN_TAG = "nsvc_admin";

    private static final SuggestionProvider<ServerCommandSource> PLAYER_SUGGEST = (ctx, builder) -> {
        ctx.getSource().getServer().getPlayerManager().getPlayerList()
                .forEach(p -> builder.suggest(p.getName().getString()));
        builder.suggest("allplayer");
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> SIZE_SUGGEST = (ctx, builder) -> {
        builder.suggest("small");
        builder.suggest("large");
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("NSvc")
            // =========================================================
            // save
            // =========================================================
            .then(literal("save").executes(VirtualChestCommand::saveData))

            // =========================================================
            // help 系列
            // =========================================================
            .then(literal("help").executes(ctx -> showHelp(ctx, "all")))
            .then(literal("-help").executes(ctx -> showHelp(ctx, "all")))
            .then(literal("--help").executes(ctx -> showHelp(ctx, "all")))
            .then(literal("h").executes(ctx -> showHelp(ctx, "all")))
            .then(literal("oldhelp").executes(ctx -> showHelp(ctx, "old")))
            .then(literal("-oldhelp").executes(ctx -> showHelp(ctx, "old")))
            .then(literal("--oldhelp").executes(ctx -> showHelp(ctx, "old")))
            .then(literal("ohelp").executes(ctx -> showHelp(ctx, "old")))
            .then(literal("-ohelp").executes(ctx -> showHelp(ctx, "old")))
            .then(literal("--ohelp").executes(ctx -> showHelp(ctx, "old")))
            .then(literal("newhelp").executes(ctx -> showHelp(ctx, "new")))
            .then(literal("-newhelp").executes(ctx -> showHelp(ctx, "new")))
            .then(literal("--newhelp").executes(ctx -> showHelp(ctx, "new")))
            .then(literal("nhelp").executes(ctx -> showHelp(ctx, "new")))
            .then(literal("-nhelp").executes(ctx -> showHelp(ctx, "new")))
            .then(literal("--nhelp").executes(ctx -> showHelp(ctx, "new")))

            // =========================================================
            // create / mcreate（给自己创建）
            // =========================================================
            .then(literal("create")
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> createOwnChest(ctx, "large"))
                    .then(argument("size", StringArgumentType.word()).suggests(SIZE_SUGGEST)
                        .executes(ctx -> createOwnChest(ctx, StringArgumentType.getString(ctx, "size"))))))
            .then(literal("mcreate")
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> createOwnChest(ctx, "large"))
                    .then(argument("size", StringArgumentType.word()).suggests(SIZE_SUGGEST)
                        .executes(ctx -> createOwnChest(ctx, StringArgumentType.getString(ctx, "size"))))))

            // =========================================================
            // pcreate / padd（创建公共箱子）
            // =========================================================
            .then(literal("pcreate")
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> createPublicChest(ctx, "large"))
                    .then(argument("size", StringArgumentType.word()).suggests(SIZE_SUGGEST)
                        .executes(ctx -> createPublicChest(ctx, StringArgumentType.getString(ctx, "size"))))))
            .then(literal("padd")
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> createPublicChest(ctx, "large"))
                    .then(argument("size", StringArgumentType.word()).suggests(SIZE_SUGGEST)
                        .executes(ctx -> createPublicChest(ctx, StringArgumentType.getString(ctx, "size"))))))

            // =========================================================
            // name <chest> create/open/delete/remove/transfer
            // =========================================================
            .then(literal("name")
                .then(argument("chest", StringArgumentType.word())
                    .then(literal("create")
                        .executes(ctx -> createOwnChestByName(ctx, "large"))
                        .then(argument("size", StringArgumentType.word()).suggests(SIZE_SUGGEST)
                            .executes(ctx -> createOwnChestByName(ctx, StringArgumentType.getString(ctx, "size")))))
                    .then(literal("open").executes(VirtualChestCommand::openChestGeneric))
                    .then(literal("delete")
                        .executes(ctx -> deleteChestGeneric(ctx, false))
                        .then(literal("close").executes(ctx -> deleteChestGeneric(ctx, true))))
                    .then(literal("remove")
                        .executes(ctx -> deleteChestGeneric(ctx, false))
                        .then(literal("close").executes(ctx -> deleteChestGeneric(ctx, true))))
                    .then(literal("transfer")
                        .then(argument("target", StringArgumentType.word()).suggests(PLAYER_SUGGEST)
                            .executes(VirtualChestCommand::transferChestByName)))))

            // =========================================================
            // open <chest>
            // =========================================================
            .then(literal("open")
                .then(argument("chest", StringArgumentType.word())
                    .executes(VirtualChestCommand::openChestGeneric)))

            // =========================================================
            // popen <chest>
            // =========================================================
            .then(literal("popen")
                .then(argument("chest", StringArgumentType.word())
                    .executes(VirtualChestCommand::openPublicChest)))

            // =========================================================
            // delete / remove <chest> [close]
            // =========================================================
            .then(literal("delete")
                .then(argument("chest", StringArgumentType.word())
                    .executes(ctx -> deleteChestGeneric(ctx, false))
                    .then(literal("close").executes(ctx -> deleteChestGeneric(ctx, true)))))
            .then(literal("remove")
                .then(argument("chest", StringArgumentType.word())
                    .executes(ctx -> deleteChestGeneric(ctx, false))
                    .then(literal("close").executes(ctx -> deleteChestGeneric(ctx, true)))))

            // =========================================================
            // premove / pdelete <chest> [close]
            // =========================================================
            .then(literal("premove")
                .then(argument("chest", StringArgumentType.word())
                    .executes(ctx -> deletePublicChest(ctx, false))
                    .then(literal("close").executes(ctx -> deletePublicChest(ctx, true)))))
            .then(literal("pdelete")
                .then(argument("chest", StringArgumentType.word())
                    .executes(ctx -> deletePublicChest(ctx, false))
                    .then(literal("close").executes(ctx -> deletePublicChest(ctx, true)))))

            // =========================================================
            // transfer <chest> <target>
            // =========================================================
            .then(literal("transfer")
                .then(argument("chest", StringArgumentType.word())
                    .then(argument("target", StringArgumentType.word()).suggests(PLAYER_SUGGEST)
                        .executes(VirtualChestCommand::transferChestSelf))))

            // =========================================================
            // list [玩家]
            // =========================================================
            .then(literal("list")
                .executes(VirtualChestCommand::listAllChests)
                .then(argument("player", StringArgumentType.word()).suggests(PLAYER_SUGGEST)
                    .executes(VirtualChestCommand::listFromPlayerArg)))

            // plist（公共）
            .then(literal("plist").executes(VirtualChestCommand::listPublicChests))

            // mlist（自己）
            .then(literal("mlist").executes(VirtualChestCommand::listOwnChests))

            // =========================================================
            // players <玩家> list/open/create/delete/remove/transfer
            // =========================================================
            .then(literal("players")
                .then(argument("player", StringArgumentType.word()).suggests(PLAYER_SUGGEST)
                    .then(literal("list").executes(VirtualChestCommand::listFromPlayerArg))
                    .then(literal("open")
                        .then(argument("chest", StringArgumentType.word())
                            .executes(VirtualChestCommand::playersOpenChest)))
                    .then(literal("create")
                        .then(argument("chest", StringArgumentType.word())
                            .executes(ctx -> playersCreateChest(ctx, "large"))
                            .then(argument("size", StringArgumentType.word()).suggests(SIZE_SUGGEST)
                                .executes(ctx -> playersCreateChest(ctx, StringArgumentType.getString(ctx, "size"))))))
                    .then(literal("delete")
                        .then(argument("chest", StringArgumentType.word())
                            .executes(ctx -> playersDeleteChest(ctx, false))
                            .then(literal("close").executes(ctx -> playersDeleteChest(ctx, true)))))
                    .then(literal("remove")
                        .then(argument("chest", StringArgumentType.word())
                            .executes(ctx -> playersDeleteChest(ctx, false))
                            .then(literal("close").executes(ctx -> playersDeleteChest(ctx, true)))))
                    .then(literal("transfer")
                        .then(argument("chest", StringArgumentType.word())
                            .then(argument("target", StringArgumentType.word()).suggests(PLAYER_SUGGEST)
                                .executes(VirtualChestCommand::playersTransferChest))))))

            // =========================================================
            // 旧命令兼容：<target> create/open/delete/remove/list
            // =========================================================
            .then(argument("target", StringArgumentType.word()).suggests(PLAYER_SUGGEST)
                .then(literal("create")
                    .then(argument("chest", StringArgumentType.word())
                        .executes(ctx -> legacyCreateChest(ctx, "large"))
                        .then(argument("size", StringArgumentType.word()).suggests(SIZE_SUGGEST)
                            .executes(ctx -> legacyCreateChest(ctx, StringArgumentType.getString(ctx, "size"))))))
                .then(literal("open")
                    .then(argument("chest", StringArgumentType.word())
                        .executes(VirtualChestCommand::legacyOpenChest)))
                .then(literal("delete")
                    .then(argument("chest", StringArgumentType.word())
                        .executes(ctx -> legacyDeleteChest(ctx, false))
                        .then(literal("close").executes(ctx -> legacyDeleteChest(ctx, true)))))
                .then(literal("remove")
                    .then(argument("chest", StringArgumentType.word())
                        .executes(ctx -> legacyDeleteChest(ctx, false))
                        .then(literal("close").executes(ctx -> legacyDeleteChest(ctx, true)))))
                .then(literal("list").executes(VirtualChestCommand::listFromTargetArg)))
        );
    }

    // ==================== 工具方法 ====================

    private static boolean hasAdminPermission(ServerCommandSource source) {
        ServerPlayerEntity p = source.getPlayer();
        return p == null || p.getCommandTags().contains(ADMIN_TAG);
    }

    private static UUID resolvePlayerUuid(ServerCommandSource source, String name) {
        ServerPlayerEntity p = source.getServer().getPlayerManager().getPlayer(name);
        return p != null ? p.getUuid() : null;
    }

    private static VirtualChest.Size parseSize(String s) {
        return "small".equalsIgnoreCase(s) ? VirtualChest.Size.SMALL : VirtualChest.Size.LARGE;
    }

    // ==================== save ====================

    private static int saveData(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        if (!hasAdminPermission(source)) {
            source.sendError(I18n.translate(source.getPlayer(), "nsvc.error.no_permission_save"));
            return 0;
        }
        VirtualChestData.save(source.getServer());
        source.sendFeedback(() -> I18n.translate(source.getPlayer(), "nsvc.save.success").copy().formatted(Formatting.GREEN), true);
        return 1;
    }

    // ==================== help ====================

    private static int showHelp(CommandContext<ServerCommandSource> ctx, String type) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        String[] keys;
        if ("old".equals(type)) {
            keys = new String[]{
                "nsvc.help.old.header",
                "nsvc.help.old.1", "nsvc.help.old.2", "nsvc.help.old.3", "nsvc.help.old.4"
            };
        } else if ("new".equals(type)) {
            keys = new String[]{
                "nsvc.help.new.header",
                "nsvc.help.new.1", "nsvc.help.new.2", "nsvc.help.new.3", "nsvc.help.new.4",
                "nsvc.help.new.5", "nsvc.help.new.6", "nsvc.help.new.7", "nsvc.help.new.8",
                "nsvc.help.new.9", "nsvc.help.new.10", "nsvc.help.new.11", "nsvc.help.new.12",
                "nsvc.help.new.13", "nsvc.help.new.14", "nsvc.help.new.15", "nsvc.help.new.16",
                "nsvc.help.new.17", "nsvc.help.new.18"
            };
        } else {
            keys = new String[]{
                "nsvc.help.new.header",
                "nsvc.help.new.1", "nsvc.help.new.2", "nsvc.help.new.3", "nsvc.help.new.4",
                "nsvc.help.new.5", "nsvc.help.new.6", "nsvc.help.new.7", "nsvc.help.new.8",
                "nsvc.help.new.9", "nsvc.help.new.10", "nsvc.help.new.11", "nsvc.help.new.12",
                "nsvc.help.new.13", "nsvc.help.new.14", "nsvc.help.new.15", "nsvc.help.new.16",
                "nsvc.help.new.17", "nsvc.help.new.18",
                "nsvc.help.old.header",
                "nsvc.help.old.1", "nsvc.help.old.2", "nsvc.help.old.3", "nsvc.help.old.4"
            };
        }
        for (String k : keys) {
            source.sendFeedback(() -> I18n.translate(player, k).copy().formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    // ==================== create ====================

    private static int createOwnChest(CommandContext<ServerCommandSource> ctx, String sizeArg) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity self = source.getPlayerOrThrow();
        String name = StringArgumentType.getString(ctx, "name");
        return doCreate(source, self.getUuid(), name, sizeArg, false);
    }

    /** /NSvc name <箱子名> create [size] —— 用 chest 参数创建属于自己的箱子 */
    private static int createOwnChestByName(CommandContext<ServerCommandSource> ctx, String sizeArg) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity self = source.getPlayerOrThrow();
        String name = StringArgumentType.getString(ctx, "chest");
        return doCreate(source, self.getUuid(), name, sizeArg, false);
    }

    private static int createPublicChest(CommandContext<ServerCommandSource> ctx, String sizeArg) {
        ServerCommandSource source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        return doCreate(source, null, name, sizeArg, true);
    }

    private static int playersCreateChest(CommandContext<ServerCommandSource> ctx, String sizeArg) {
        ServerCommandSource source = ctx.getSource();
        String target = StringArgumentType.getString(ctx, "player");
        String name = StringArgumentType.getString(ctx, "chest");
        return doPlayerCreate(source, target, name, sizeArg);
    }

    /** 旧命令：<target> create <name> [size] */
    private static int legacyCreateChest(CommandContext<ServerCommandSource> ctx, String sizeArg) {
        ServerCommandSource source = ctx.getSource();
        String target = StringArgumentType.getString(ctx, "target");
        String name = StringArgumentType.getString(ctx, "chest");
        boolean isPublic = target.equalsIgnoreCase("allplayer");
        if (isPublic) {
            return doCreate(source, null, name, sizeArg, true);
        }
        return doPlayerCreate(source, target, name, sizeArg);
    }

    private static int doPlayerCreate(ServerCommandSource source, String target, String name, String sizeArg) {
        ServerPlayerEntity self = source.getPlayer();
        UUID targetUuid = resolvePlayerUuid(source, target);
        if (targetUuid == null) {
            source.sendError(I18n.translate(self, "nsvc.error.player_offline", target));
            return 0;
        }
        if (!hasAdminPermission(source)) {
            if (self == null || !self.getUuid().equals(targetUuid)) {
                source.sendError(I18n.translate(self, "nsvc.error.create.other_player"));
                return 0;
            }
        }
        return doCreate(source, targetUuid, name, sizeArg, false);
    }

    private static int doCreate(ServerCommandSource source, UUID owner, String name, String sizeArg, boolean isPublic) {
        ServerPlayerEntity player = source.getPlayer();
        VirtualChest.Size size = parseSize(sizeArg);
        VirtualChestData data = VirtualChestData.getInstance();
        if (data.getChest(name) != null) {
            source.sendError(I18n.translate(player, "nsvc.error.chest_exists", name));
            return 0;
        }
        if (data.createChest(name, size, owner, isPublic)) {
            String sizeKey = size == VirtualChest.Size.SMALL ? "nsvc.size.small" : "nsvc.size.large";
            String sizeStr = I18n.translate(player, sizeKey).getString();
            source.sendFeedback(() -> I18n.translate(player, "nsvc.create.success", name, sizeStr + " " + size.getSlots()).copy().formatted(Formatting.GREEN), true);
            return 1;
        }
        source.sendError(I18n.translate(player, "nsvc.error.create_failed"));
        return 0;
    }

    // ==================== open ====================

    private static int openChestGeneric(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String name = StringArgumentType.getString(ctx, "chest");
        return doOpen(source, player, name, false);
    }

    private static int openPublicChest(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String name = StringArgumentType.getString(ctx, "chest");
        return doOpen(source, player, name, true);
    }

    private static int playersOpenChest(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String name = StringArgumentType.getString(ctx, "chest");
        return doOpen(source, player, name, false);
    }

    /** 旧命令：<target> open <chest>；也用于 <玩家> open <chest> 简写 */
    private static int legacyOpenChest(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String name = StringArgumentType.getString(ctx, "chest");
        return doOpen(source, player, name, false);
    }

    private static int doOpen(ServerCommandSource source, ServerPlayerEntity player, String name, boolean requirePublic) {
        VirtualChestData data = VirtualChestData.getInstance();
        VirtualChest chest = data.getChest(name);
        if (chest == null) {
            source.sendError(I18n.translate(player, "nsvc.error.not_exist", name));
            return 0;
        }
        if (requirePublic && !chest.isPublic()) {
            source.sendError(I18n.translate(player, "nsvc.error.not_public", name));
            return 0;
        }
        if (chest.isPendingDeletion()) {
            source.sendError(I18n.translate(player, "nsvc.error.pending_deletion"));
            return 0;
        }
        if (!hasAdminPermission(source)) {
            if (!chest.isPublic() && !player.getUuid().equals(chest.getOwner())) {
                source.sendError(I18n.translate(player, "nsvc.error.no_permission_private"));
                return 0;
            }
        }
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return I18n.translate(player, "nsvc.chest.title", name);
            }
            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                int rows = chest.getChestSize() == VirtualChest.Size.SMALL ? 3 : 6;
                ScreenHandlerType<?> type = rows == 3 ? ScreenHandlerType.GENERIC_9X3 : ScreenHandlerType.GENERIC_9X6;
                return new GenericContainerScreenHandler(type, syncId, inv, chest, rows);
            }
        });
        VirtualChestData.scheduleAutoSave(source.getServer());
        source.sendFeedback(() -> I18n.translate(player, "nsvc.open.opening", name).copy().formatted(Formatting.GRAY), false);
        return 1;
    }

    // ==================== delete ====================

    private static int deleteChestGeneric(CommandContext<ServerCommandSource> ctx, boolean cancel) {
        ServerCommandSource source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "chest");
        VirtualChest chest = VirtualChestData.getInstance().getChest(name);
        if (chest == null) {
            source.sendError(I18n.translate(source.getPlayer(), "nsvc.error.not_exist", name));
            return 0;
        }
        return performDelete(source, chest, cancel);
    }

    private static int deletePublicChest(CommandContext<ServerCommandSource> ctx, boolean cancel) {
        ServerCommandSource source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "chest");
        VirtualChest chest = VirtualChestData.getInstance().getChest(name);
        if (chest == null) {
            source.sendError(I18n.translate(source.getPlayer(), "nsvc.error.not_exist", name));
            return 0;
        }
        if (!chest.isPublic()) {
            source.sendError(I18n.translate(source.getPlayer(), "nsvc.error.not_public", name));
            return 0;
        }
        return performDelete(source, chest, cancel);
    }

    private static int playersDeleteChest(CommandContext<ServerCommandSource> ctx, boolean cancel) {
        ServerCommandSource source = ctx.getSource();
        String target = StringArgumentType.getString(ctx, "player");
        String name = StringArgumentType.getString(ctx, "chest");
        UUID targetUuid = resolvePlayerUuid(source, target);
        VirtualChest chest = VirtualChestData.getInstance().getChest(name);
        if (chest == null) {
            source.sendError(I18n.translate(source.getPlayer(), "nsvc.error.not_exist", name));
            return 0;
        }
        // 保护：必须验证箱子属于指定玩家
        if (targetUuid == null || chest.isPublic() || !targetUuid.equals(chest.getOwner())) {
            source.sendError(I18n.translate(source.getPlayer(), "nsvc.error.not_owner_target", target, name));
            return 0;
        }
        return performDelete(source, chest, cancel);
    }

    /** 旧命令：<target> delete/remove <chest> [close] */
    private static int legacyDeleteChest(CommandContext<ServerCommandSource> ctx, boolean cancel) {
        ServerCommandSource source = ctx.getSource();
        String target = StringArgumentType.getString(ctx, "target");
        String name = StringArgumentType.getString(ctx, "chest");
        boolean isPublic = target.equalsIgnoreCase("allplayer");
        VirtualChest chest = VirtualChestData.getInstance().getChest(name);
        if (chest == null) {
            source.sendError(I18n.translate(source.getPlayer(), "nsvc.error.not_exist", name));
            return 0;
        }
        if (!isPublic) {
            UUID targetUuid = resolvePlayerUuid(source, target);
            if (targetUuid == null) {
                source.sendError(I18n.translate(source.getPlayer(), "nsvc.error.player_offline", target));
                return 0;
            }
            if (chest.isPublic() || !targetUuid.equals(chest.getOwner())) {
                source.sendError(I18n.translate(source.getPlayer(), "nsvc.error.not_owner_target", target, name));
                return 0;
            }
        }
        return performDelete(source, chest, cancel);
    }

    private static int performDelete(ServerCommandSource source, VirtualChest chest, boolean cancel) {
        ServerPlayerEntity player = source.getPlayer();
        String name = chest.getName();
        boolean isPublic = chest.isPublic();
        boolean isAdmin = hasAdminPermission(source);

        if (cancel) {
            if (!chest.isPendingDeletion()) {
                source.sendError(I18n.translate(player, "nsvc.error.not_pending"));
                return 0;
            }
            if (!isAdmin && !isPublic && (player == null || !player.getUuid().equals(chest.getOwner()))) {
                source.sendError(I18n.translate(player, "nsvc.error.no_permission_cancel"));
                return 0;
            }
            chest.setPendingDeletion(false, 0);
            source.sendFeedback(() -> I18n.translate(player, "nsvc.delete.cancelled", name).copy().formatted(Formatting.GREEN), true);
            return 1;
        }

        if (isAdmin) {
            chest.setPendingDeletion(false, 0);
            VirtualChestData.getInstance().deleteChest(name);
            source.sendFeedback(() -> I18n.translate(player, "nsvc.delete.admin_success", name).copy().formatted(Formatting.GREEN), true);
            return 1;
        }

        if (isPublic) {
            long currentTime = source.getWorld().getTime();
            if (!chest.isPendingDeletion()) {
                chest.setPendingDeletion(true, currentTime);
                source.sendFeedback(() -> I18n.translate(player, "nsvc.delete.public_pending", name, name).copy().formatted(Formatting.YELLOW), false);
                return 1;
            }
            if (currentTime - chest.getDeletionStartTime() >= THREE_DAYS_TICKS) {
                VirtualChestData.getInstance().deleteChest(name);
                source.sendFeedback(() -> I18n.translate(player, "nsvc.delete.public_done", name).copy().formatted(Formatting.GREEN), true);
                return 1;
            }
            long remaining = THREE_DAYS_TICKS - (currentTime - chest.getDeletionStartTime());
            source.sendError(I18n.translate(player, "nsvc.delete.remaining_days", remaining / 24000));
            return 0;
        } else {
            if (player == null || !player.getUuid().equals(chest.getOwner())) {
                source.sendError(I18n.translate(player, "nsvc.error.no_permission_private"));
                return 0;
            }
            if (!chest.isPendingDeletion()) {
                chest.setPendingDeletion(true, source.getWorld().getTime());
                source.sendFeedback(() -> I18n.translate(player, "nsvc.delete.private_confirm", name).copy().formatted(Formatting.YELLOW), false);
                return 1;
            }
            long confirmTime = source.getWorld().getTime() - chest.getDeletionStartTime();
            if (confirmTime > 1200) {
                chest.setPendingDeletion(false, 0);
                source.sendError(I18n.translate(player, "nsvc.delete.confirm_timeout"));
                return 0;
            }
            chest.setPendingDeletion(false, 0);
            VirtualChestData.getInstance().deleteChest(name);
            source.sendFeedback(() -> I18n.translate(player, "nsvc.delete.private_done", name).copy().formatted(Formatting.GREEN), true);
            return 1;
        }
    }

    // ==================== transfer ====================

    /** /NSvc transfer <chest> <target> —— 转移自己拥有的箱子 */
    private static int transferChestSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity self = source.getPlayerOrThrow();
        String chestName = StringArgumentType.getString(ctx, "chest");
        String targetName = StringArgumentType.getString(ctx, "target");

        VirtualChest chest = VirtualChestData.getInstance().getChest(chestName);
        if (chest == null) {
            source.sendError(I18n.translate(self, "nsvc.error.not_exist", chestName));
            return 0;
        }
        if (!hasAdminPermission(source) && !self.getUuid().equals(chest.getOwner())) {
            source.sendError(I18n.translate(self, "nsvc.error.no_permission_transfer", chestName));
            return 0;
        }
        return doTransfer(source, self, chest, targetName);
    }

    /** /NSvc name <chest> transfer <target> —— 与 transferChestSelf 相同 */
    private static int transferChestByName(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        return transferChestSelf(ctx);
    }

    /** /NSvc players <player> <chest> transfer <target> —— 管理员/本人操作指定玩家箱子 */
    private static int playersTransferChest(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity self = source.getPlayer();
        String ownerName = StringArgumentType.getString(ctx, "player");
        String chestName = StringArgumentType.getString(ctx, "chest");
        String targetName = StringArgumentType.getString(ctx, "target");

        VirtualChest chest = VirtualChestData.getInstance().getChest(chestName);
        if (chest == null) {
            source.sendError(I18n.translate(self, "nsvc.error.not_exist", chestName));
            return 0;
        }
        UUID ownerUuid = resolvePlayerUuid(source, ownerName);
        // 保护：箱子必须属于声明的玩家
        if (ownerUuid == null || chest.isPublic() || !ownerUuid.equals(chest.getOwner())) {
            source.sendError(I18n.translate(self, "nsvc.error.not_owner_target", ownerName, chestName));
            return 0;
        }
        if (!hasAdminPermission(source)) {
            if (self == null || !self.getUuid().equals(chest.getOwner())) {
                source.sendError(I18n.translate(self, "nsvc.error.no_permission_transfer", chestName));
                return 0;
            }
        }
        return doTransfer(source, self, chest, targetName);
    }

    /** 实际执行转移 */
    private static int doTransfer(ServerCommandSource source, ServerPlayerEntity actor, VirtualChest chest, String targetName) {
        if (chest.isPublic()) {
            source.sendError(I18n.translate(actor, "nsvc.error.cannot_transfer_public", chest.getName()));
            return 0;
        }
        UUID targetUuid = resolvePlayerUuid(source, targetName);
        if (targetUuid == null) {
            source.sendError(I18n.translate(actor, "nsvc.error.player_offline", targetName));
            return 0;
        }
        if (chest.getOwner() != null && chest.getOwner().equals(targetUuid)) {
            source.sendError(I18n.translate(actor, "nsvc.error.transfer_to_self", targetName));
            return 0;
        }
        chest.transferTo(targetUuid);
        VirtualChestData.scheduleAutoSave(source.getServer());
        source.sendFeedback(() -> I18n.translate(actor, "nsvc.transfer.success",
                chest.getName(), targetName).copy().formatted(Formatting.GREEN), true);
        return 1;
    }

    // ==================== list ====================

    /** /NSvc list —— 列出全部箱子 */
    private static int listAllChests(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        boolean isAdmin = hasAdminPermission(source);
        VirtualChestData data = VirtualChestData.getInstance();
        int count = 0;
        source.sendFeedback(() -> I18n.translate(player, "nsvc.list.all.header").copy().formatted(Formatting.GOLD), false);
        for (VirtualChest chest : data.getAllChests()) {
            count++;
            if (isAdmin) {
                String ownerName = chest.isPublic() ? I18n.translate(player, "nsvc.list.owner.public").getString()
                        : (chest.getOwner() != null ? chest.getOwner().toString() : "?");
                source.sendFeedback(() -> I18n.translate(player, "nsvc.list.entry.owner", chest.getName(),
                        chest.getChestSize().getDisplayName(), ownerName, chest.isPendingDeletion() ? " [Pending]" : "")
                        .copy().formatted(Formatting.GRAY), false);
            } else {
                source.sendFeedback(() -> I18n.translate(player, "nsvc.list.entry", chest.getName(),
                        chest.getChestSize().getDisplayName(), chest.isPendingDeletion() ? " [Pending]" : "")
                        .copy().formatted(Formatting.GRAY), false);
            }
        }
        if (count == 0) {
            source.sendFeedback(() -> I18n.translate(player, "nsvc.list.empty").copy().formatted(Formatting.YELLOW), false);
        }
        return count;
    }

    /** /NSvc plist —— 列出所有公共箱子 */
    private static int listPublicChests(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        return doList(source, player, "allplayer", true, null);
    }

    /** /NSvc list <player> 或 /NSvc players <player> list —— 从 player 参数读取 */
    private static int listFromPlayerArg(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        String target = StringArgumentType.getString(ctx, "player");
        boolean isPublic = target.equalsIgnoreCase("allplayer");
        UUID targetUuid = isPublic ? null : resolvePlayerUuid(source, target);
        if (!isPublic && targetUuid == null) {
            source.sendError(I18n.translate(player, "nsvc.error.player_offline", target));
            return 0;
        }
        return doList(source, player, target, isPublic, targetUuid);
    }

    /** 旧命令：<target> list —— 从 target 参数读取 */
    private static int listFromTargetArg(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        String target = StringArgumentType.getString(ctx, "target");
        boolean isPublic = target.equalsIgnoreCase("allplayer");
        UUID targetUuid = isPublic ? null : resolvePlayerUuid(source, target);
        if (!isPublic && targetUuid == null) {
            source.sendError(I18n.translate(player, "nsvc.error.player_offline", target));
            return 0;
        }
        return doList(source, player, target, isPublic, targetUuid);
    }

    /** /NSvc mlist —— 自己的所有箱子 */
    private static int listOwnChests(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity self = source.getPlayer();
        if (self == null) {
            source.sendError(I18n.translate("nsvc.error.player_only"));
            return 0;
        }
        return doList(source, self, self.getName().getString(), false, self.getUuid());
    }

    private static int doList(ServerCommandSource source, ServerPlayerEntity player, String label, boolean isPublic, UUID targetUuid) {
        VirtualChestData data = VirtualChestData.getInstance();
        boolean isAdmin = hasAdminPermission(source);
        int count = 0;
        source.sendFeedback(() -> I18n.translate(player, "nsvc.list.header", label).copy().formatted(Formatting.GOLD), false);
        for (VirtualChest chest : data.getAllChests()) {
            if (chest.isPublic() != isPublic) continue;
            if (!isPublic) {
                if (targetUuid != null && !targetUuid.equals(chest.getOwner())) continue;
                if (targetUuid == null && !isAdmin && (player == null || !player.getUuid().equals(chest.getOwner()))) continue;
            }
            count++;
            String status = chest.isPendingDeletion() ? " [Pending]" : "";
            source.sendFeedback(() -> I18n.translate(player, "nsvc.list.entry", chest.getName(),
                    chest.getChestSize().getDisplayName() + ", " + chest.size(), status)
                    .copy().formatted(Formatting.GRAY), false);
        }
        if (count == 0) {
            source.sendFeedback(() -> I18n.translate(player, "nsvc.list.empty").copy().formatted(Formatting.YELLOW), false);
        }
        return count;
    }
}
