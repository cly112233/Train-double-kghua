package xiao.hua.roles;



import io.wifi.starrailexpress.api.NormalRole;

import io.wifi.starrailexpress.api.RoleSkill;

import io.wifi.starrailexpress.api.SRERole;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;

import io.wifi.starrailexpress.index.TMMItems;

import io.wifi.starrailexpress.game.GameConstants;

import io.wifi.starrailexpress.util.ShopEntry;

import io.wifi.starrailexpress.util.SREItemUtils;

import static dev.doctor4t.wathe.util.ShopEntry.Type;

import net.minecraft.ChatFormatting;

import net.minecraft.core.component.DataComponents;

import net.minecraft.network.chat.Component;

import net.minecraft.resources.ResourceLocation;

import net.minecraft.world.item.component.ItemLore;

import net.minecraft.world.entity.LivingEntity;

import net.minecraft.world.entity.player.Player;

import net.minecraft.world.item.ItemStack;

import net.minecraft.world.item.Items;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;

import xiao.hua.init.HuaItems;



import java.awt.Color;

import java.util.ArrayList;

import java.util.Collections;

import java.util.List;

import java.util.UUID;



public class VengeanceAgent extends NormalRole {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("huarolemods", "vengeance_agent");

    private static final int CONTRACT_BONUS_REWARD = 100; // extra on top of base killer 100

    private static final int OTHER_KILL_REWARD = 30;      // was 20

    public static final int CONTRACT_TARGET_PURPLE_COLOR = -8388353;



    public VengeanceAgent() {

        super(ID, new Color(139, 0, 0).getRGB(), false, true, SRERole.MoodType.FAKE, -1, true);

        setCanUseInstinct(true);

        setDefaultMax(1);

        setDefaultEnableNeededPlayerCount(-1);

        setDefaultEnableChance(-1);

        // 不允许翻尸体（canGetBodyItems 默认 false，删除开启调用）

        RoleSkill.register(this, this::onAbilityUse);

    }



    @Override

    public void onInit(MinecraftServer server, net.minecraft.server.level.ServerPlayer player) {

        super.onInit(server, player);

        // Give initial 50 gold coins at game start

        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);

        if (shopComponent != null) {

            shopComponent.addToBalance(50);

        }

    }



    @Override

    public void onKill(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason) {

        super.onKill(victim, spawnBody, killer, deathReason);

        

        if (killer == null) {

            return;

        }

        

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(killer.level());

        SRERole killerRole = gameWorld.getRole(killer);

        

        if (killerRole instanceof VengeanceAgent) {

            VengeanceAgentComponent component = VengeanceAgentComponent.getKey().get(killer);

            if (component != null) {

                handleVengeanceAgentKillReward(killer, victim, component);

                component.removeContractTarget(victim.getUUID());

            }

        }

    }



    private void handleVengeanceAgentKillReward(Player killer, Player victim, VengeanceAgentComponent component) {

        if (component.isContractTarget(victim.getUUID()))

            giveKillerReward(killer, CONTRACT_BONUS_REWARD);

    }



    private void giveKillerReward(Player player, int amount) {

        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);

        shopComponent.balance += amount;

    }



    @Override

    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason, boolean forceDeath) {

        super.onDeath(victim, spawnBody, killer, deathReason, forceDeath);

        

        MinecraftServer server = victim.level().getServer();

        if (server == null) return;

        

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());

        

        for (Player player : server.getPlayerList().getPlayers()) {

            if (player != victim && !player.isSpectator()) {

                SRERole role = gameWorld.getRole(player);

                if (role instanceof VengeanceAgent) {

                    VengeanceAgentComponent component = VengeanceAgentComponent.getKey().get(player);

                    if (component != null && component.isContractTarget(victim.getUUID())) {

                        if (killer != null && killer != player)

                            giveKillerReward(player, OTHER_KILL_REWARD);

                        component.removeContractTarget(victim.getUUID());

                        List<UUID> remainingTargets = component.getContractTargets();

                        if (!remainingTargets.isEmpty()) {

                            if (victim.getUUID().equals(component.getCurrentLensTarget()))

                                component.setCurrentLensTarget(remainingTargets.get(0));

                            continue;

                        }

                        component.setCurrentLensTarget(null);

                    }

                }

            }

        }

    }



    private void onAbilityUse(RoleSkill.RoleSkillContext context) {

        Player player = context.player();

        VengeanceAgentComponent component = VengeanceAgentComponent.getKey().get(player);

        if (component == null || !component.isLensEnabled()) return;



        if (player.isCrouching()) {

            switchLensTarget(component);

            return;

        }



        if (component.isLensOnCooldown()) return;



        UUID target = component.getCurrentLensTarget();

        if (target == null) {

            List<UUID> targets = component.getContractTargets();

            if (targets.isEmpty()) return;

            Collections.shuffle(targets);

            target = targets.get(0);

            component.setCurrentLensTarget(target);

        }



        component.setLensCooldown();

        component.setProtectedUntil(System.currentTimeMillis() + 2500L);

        component.startInspecting();

        openLensViewScreen(player, target);

    }



    private void switchLensTarget(VengeanceAgentComponent component) {

        List<UUID> targets = component.getContractTargets();

        if (targets.size() <= 1) return;

        UUID current = component.getCurrentLensTarget();

        List<UUID> available = new ArrayList<>();

        for (UUID uuid : targets) {

            if (!uuid.equals(current)) available.add(uuid);

        }

        if (available.isEmpty()) return;

        Collections.shuffle(available);

        component.setCurrentLensTarget(available.get(0));

    }



    private void openLensViewScreen(Player player, UUID targetUuid) {

        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {

            return;

        }

        net.minecraft.server.level.ServerPlayer targetPlayer = serverPlayer.server.getPlayerList().getPlayer(targetUuid);

        if (targetPlayer == null) return;



        serverPlayer.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<UUID>() {

            @Override

            public UUID getScreenOpeningData(net.minecraft.server.level.ServerPlayer opener) {

                return targetUuid;

            }



            @Override

            public Component getDisplayName() {

                return Component.translatable("screen.huarolemods.lens_view", targetPlayer.getName().getString());

            }



            @Override

            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory inv, Player player) {

                return new xiao.hua.client.screen.LensViewScreenHandler(syncId, inv, targetPlayer);

            }

        });

    }



    @Override

    public boolean allowDeath(Player victim, @Nullable Player killer, ResourceLocation deathReason, boolean spawnBody) {

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());

        SRERole victimRole = gameWorld.getRole(victim);

        

        if (victimRole instanceof VengeanceAgent) {

            VengeanceAgentComponent component = VengeanceAgentComponent.getKey().get(victim);

            if (component != null && component.isProtected())

                return false;

        }

        return super.allowDeath(victim, killer, deathReason, spawnBody);

    }



    @Override

    public List<ShopEntry> getShopEntries() {

        List<ShopEntry> entries = new ArrayList<>();

        entries.add(new ShopEntry(HuaItems.VENGEANCE_KNIFE.getDefaultInstance(), 100, ShopEntry.Type.WEAPON) {

            @Override

            public boolean canBuy(@NotNull Player player) {

                return super.canBuy(player) && !SREItemUtils.hasItem(player, HuaItems.VENGEANCE_KNIFE);

            }



            @Override

            public boolean onBuy(@NotNull Player player) {

                if (!super.onBuy(player)) return false;


                return true;

            }

        });



        ItemStack revolver = TMMItems.REVOLVER.getDefaultInstance();

        revolver.set(DataComponents.CUSTOM_NAME, Component.translatable("item.huarolemods.robber_pistol"));

        revolver.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.huarolemods.robber_pistol.desc").withStyle(ChatFormatting.GRAY))));

        entries.add(new ShopEntry(revolver, 290, ShopEntry.Type.WEAPON) {

            @Override

            public boolean canBuy(@NotNull Player player) {

                return super.canBuy(player) && !SREItemUtils.hasItem(player, TMMItems.REVOLVER);

            }



            @Override

            public boolean onBuy(@NotNull Player player) {

                if (!super.onBuy(player)) return false;


                return true;

            }

        });



        entries.add(new ShopEntry(TMMItems.PSYCHO_MODE.getDefaultInstance(), 400, ShopEntry.Type.WEAPON) {

            @Override

            public boolean onBuy(@NotNull Player player) {

                return io.wifi.starrailexpress.cca.SREPlayerShopComponent.usePsychoMode(player);

            }



            @Override

            public boolean canBuy(@NotNull Player player) {

                if (!super.canBuy(player))

                    return false;

                if (SREItemUtils.hasItem(player, TMMItems.PSYCHO_MODE))

                    return false;

                io.wifi.starrailexpress.cca.SREPlayerPsychoComponent psychoComponent = io.wifi.starrailexpress.cca.SREPlayerPsychoComponent.KEY.maybeGet(player).orElse(null);

                if (psychoComponent != null && psychoComponent.psychoTicks > 0)

                    return false;

                return true;

            }

        });

        entries.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 90, ShopEntry.Type.TOOL) {

            @Override

            public boolean canBuy(@NotNull Player player) {

                return super.canBuy(player) && !SREItemUtils.hasItem(player, TMMItems.LOCKPICK);

            }



            @Override

            public boolean onBuy(@NotNull Player player) {

                if (!super.onBuy(player)) return false;


                return true;

            }

        });

        entries.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(), 35, ShopEntry.Type.TOOL) {

            @Override

            public boolean canBuy(@NotNull Player player) {

                return super.canBuy(player) && !SREItemUtils.hasItem(player, TMMItems.CROWBAR);

            }



            @Override

            public boolean onBuy(@NotNull Player player) {

                if (!super.onBuy(player)) return false;


                return true;

            }

        });

        entries.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(), 90, ShopEntry.Type.TOOL) {

            @Override

            public boolean canBuy(@NotNull Player player) {

                return super.canBuy(player) && !SREItemUtils.hasItem(player, TMMItems.BODY_BAG);

            }



            @Override

            public boolean onBuy(@NotNull Player player) {

                if (!super.onBuy(player)) return false;


                return true;

            }

        });

        entries.add(new ShopEntry(TMMItems.POISON_VIAL.getDefaultInstance(), 150, ShopEntry.Type.WEAPON) {

            @Override

            public boolean canBuy(@NotNull Player player) {

                return super.canBuy(player) && !SREItemUtils.hasItem(player, TMMItems.POISON_VIAL);

            }



            @Override

            public boolean onBuy(@NotNull Player player) {

                if (!super.onBuy(player)) return false;


                return true;

            }

        });

        entries.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(), 390, ShopEntry.Type.WEAPON) {

            @Override

            public boolean canBuy(@NotNull Player player) {

                return super.canBuy(player) && !SREItemUtils.hasItem(player, TMMItems.GRENADE);

            }



            @Override

            public boolean onBuy(@NotNull Player player) {

                if (!super.onBuy(player)) return false;


                return true;

            }

        });

        entries.add(new ShopEntry(HuaItems.VENGEANCE_LENS.getDefaultInstance(), 125, ShopEntry.Type.TOOL) {

            @Override

            public boolean onBuy(Player player) {

                if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {

                    return false;

                }

                SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(serverPlayer);

                if (shopComponent.balance < 125)

                    return false;

                VengeanceAgentComponent component = VengeanceAgentComponent.getKey().get(serverPlayer);

                if (component != null && !component.isLensEnabled()) {

                    // 注意：扣款由 SRE 商店框架在 onBuy 成功后自动执行，这里不再手动扣（避免双扣）

                    component.enableLens();

                    List<UUID> targets = component.getContractTargets();

                    if (!targets.isEmpty()) {

                        Collections.shuffle(targets);

                        component.setCurrentLensTarget(targets.get(0));

                    }

                    return true;

                }

                return false;

            }



            @Override

            public boolean canBuy(@NotNull Player player) {

                if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {

                    return false;

                }

                SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(serverPlayer);

                VengeanceAgentComponent component = VengeanceAgentComponent.getKey().get(serverPlayer);

                return component != null && !component.isLensEnabled() && shopComponent.balance >= 125;

            }



            @Override

            public boolean canDisplay(@NotNull Player player) {

                VengeanceAgentComponent component = VengeanceAgentComponent.getKey().maybeGet(player).orElse(null);

                if (component == null)

                    return true;

                return !component.isLensEnabled();

            }

        });



        ItemStack contractPaper = Items.PAPER.getDefaultInstance();

        contractPaper.set(DataComponents.CUSTOM_NAME, Component.translatable("item.huarolemods.reset_contract"));

        entries.add(new ShopEntry(contractPaper, 30, ShopEntry.Type.TOOL) {

            @Override

            public boolean canBuy(@NotNull Player player) {

                if (!super.canBuy(player))

                    return false;

                VengeanceAgentComponent component = VengeanceAgentComponent.getKey().get(player);

                return component != null && component.getContractTargets().isEmpty();

            }



            @Override

            public boolean canDisplay(@NotNull Player player) {

                VengeanceAgentComponent component = VengeanceAgentComponent.getKey().get(player);

                return component != null && component.getContractTargets().isEmpty();

            }



            @Override

            public boolean onBuy(Player player) {

                if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {

                    return false;

                }

                SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(serverPlayer);

                if (shopComponent.balance < 30)

                    return false;

                VengeanceAgentComponent component = VengeanceAgentComponent.getKey().get(serverPlayer);

                if (component != null && component.getContractTargets().isEmpty()) {

                    // 注意：扣款由 SRE 商店框架在 onBuy 成功后自动执行，这里不再手动扣（避免双扣）

                    SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverPlayer.level());

                    List<net.minecraft.server.level.ServerPlayer> innocentPlayers = new ArrayList<>();

                    for (net.minecraft.server.level.ServerPlayer onlinePlayer : serverPlayer.server.getPlayerList().getPlayers()) {

                        if (onlinePlayer != serverPlayer && !onlinePlayer.isSpectator() && onlinePlayer.isAlive()) {

                            SRERole role = gameWorld.getRole(onlinePlayer);

                            if (role != null && role.isInnocent())

                                innocentPlayers.add(onlinePlayer);

                        }

                    }

                    Collections.shuffle(innocentPlayers);

                    int count = Math.max(1, Math.min(2, innocentPlayers.size()));

                    for (int i = 0; i < count; i++)

                        component.addContractTarget(innocentPlayers.get(i).getUUID());

                    if (!component.getContractTargets().isEmpty())

                        component.setCurrentLensTarget(component.getContractTargets().get(0));

                    return true;

                }

                return false;

            }

        });



        return entries;

    }

}

