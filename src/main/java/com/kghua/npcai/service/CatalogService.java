package com.kghua.npcai.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.index.TMMDescItems;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.RoleInitialItems;
import org.agmas.noellesroles.utils.FlagUtils;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 图鉴服务（网站 catalog.all 与游戏内图鉴 RoleIntroduceScreen 同源）：
 * 运行时枚举 角色(TMMRoles.ROLES)/修饰符(HMLModifiers.MODIFIERS)/道具(TMMDescItems.introItems)，
 * 并解析 zh_cn 翻译键，产出 JSON 供后端缓存。解析规则严格对照 RoleIntroduceScreen：
 * <pre>
 *   名称/颜色  RoleUtils.getRoleOrModifierOrItemNameWithColor + color()
 *   小字       getSimpleDescription（道具：&lt;descId&gt;.desc.simple → .tooltip → 名称）
 *   描述       getRoleOrModifierOrItemDescription（道具：&lt;descId&gt;.desc → .tooltip → 名称）
 *   小故事     "star.story.&lt;type&gt;.&lt;path&gt;"  设定 "star.settings.&lt;type&gt;.&lt;path&gt;"
 *   商店       ShopContent.getShopEntries(identifier)（仅名称+价格，与商店页签一致）
 *   初始物品   RoleInitialItems.getInitialItemsForRole(role)
 *   阵营       PlayerRoleWeightManager.getRoleType → 1平民 2中立 3杀手中立 4杀手 5警长
 * </pre>
 */
public final class CatalogService {

    private CatalogService() {}

    /** 全量图鉴：{roles:[], modifiers:[], items:[]}（角色按游戏内排序：阵营逆序→名称） */
    public static JsonObject all() {
        JsonObject r = new JsonObject();

        JsonArray roles = new JsonArray();
        try {
            for (SRERole role : Noellesroles.getAllRolesSorted(true)) {
                try {
                    roles.add(roleJson(role));
                } catch (Exception ignored) {
                    // 单个角色解析失败不拖垮全量
                }
            }
        } catch (Exception ignored) {
        }
        r.add("roles", roles);

        JsonArray modifiers = new JsonArray();
        try {
            for (SREModifier m : HMLModifiers.MODIFIERS) {
                try {
                    modifiers.add(modifierJson(m));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        r.add("modifiers", modifiers);

        JsonArray items = new JsonArray();
        try {
            for (Item it : TMMDescItems.introItems) {
                try {
                    items.add(itemJson(it));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        r.add("items", items);
        return r;
    }

    // ---------- 角色 ----------

    private static JsonObject roleJson(SRERole role) {
        ResourceLocation id = role.identifier();
        int rt = PlayerRoleWeightManager.getRoleType(role);
        JsonObject o = baseJson(id.toString(), RoleUtils.getRoleName(role).getString(),
            role.color() & 0xFFFFFF,
            role.hasSimpleDescription() ? role.getSimpleDescription().getString() : null,
            RoleUtils.getRoleDescription(role).getString(), "role", id.getPath());
        o.addProperty("roleType", rt);
        o.addProperty("faction", RoleUtils.getTeamNameWithoutColor(rt).getString());
        o.addProperty("goal", role.getGoal().getString());
        o.addProperty("canBeRandomed", role.canBeRandomedDefination());
        if (!role.getFlags().isEmpty()) {
            JsonArray flags = new JsonArray();
            for (String f : role.getFlags()) {
                flags.add(FlagUtils.getFlagName(f).getString());
            }
            o.add("flags", flags);
        }
        o.add("shop", shopJson(role));
        o.add("initialItems", initialItemsJson(role));
        o.add("related", relatedJson(role));
        return o;
    }

    /** 商店页签：仅名称+价格（与 ShopTab.render 一致） */
    private static JsonArray shopJson(SRERole role) {
        JsonArray arr = new JsonArray();
        List<ShopEntry> entries = ShopContent.getShopEntries(role.identifier());
        for (ShopEntry e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("itemId", BuiltInRegistries.ITEM.getKey(e.stack().getItem()).toString());
            o.addProperty("name", e.stack().getHoverName().getString());
            o.addProperty("price", e.price());
            arr.add(o);
        }
        return arr;
    }

    /** 初始物品页签（RoleInitialItems 无玩家重载，服务器安全） */
    private static JsonArray initialItemsJson(SRERole role) {
        JsonArray arr = new JsonArray();
        for (var stack : RoleInitialItems.getInitialItemsForRole(role)) {
            if (stack == null || stack.isEmpty()) continue;
            JsonObject o = new JsonObject();
            o.addProperty("itemId", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            o.addProperty("name", stack.getHoverName().getString());
            arr.add(o);
        }
        return arr;
    }

    /** 相关对象页签：职业关系/敌对关系/其他关系/相关修饰符（RelatedObjectTab 同源） */
    private static JsonObject relatedJson(SRERole role) {
        JsonObject o = new JsonObject();
        o.add("occupation", refArray(role.getoccupationRoles()));
        o.add("opposite", refArray(role.getOpposingRoles()));
        Set<SRERole> others = new java.util.LinkedHashSet<>();
        others.addAll(role.occupationedRoles);
        others.addAll(role.getRelatedRoles());
        o.add("other", refArray(others));
        o.add("modifiers", refArrayModifiers(role.getRelatedModifiers()));
        return o;
    }

    // ---------- 修饰符 / 道具 ----------

    private static JsonObject modifierJson(SREModifier m) {
        return baseJson(m.identifier().toString(), RoleUtils.getModifierName(m).getString(),
            m.color() & 0xFFFFFF,
            m.hasSimpleDescription() ? m.getSimpleDescription().getString() : null,
            RoleUtils.getModifierDescription(m).getString(), "modifier", m.identifier().getPath());
    }

    private static JsonObject itemJson(Item it) {
        String descId = it.getDescriptionId();
        // 道具：小字 <descId>.desc.simple → <descId>.tooltip → 名称；描述 <descId>.desc → tooltip → 名称
        //（与 getRoleOrModifierOrItemSimpleDescription / getRoleOrModifierOrItemDescription 一致）
        String simple = it.getDescription().getString();
        if (Language.getInstance().has(descId + ".desc.simple")) {
            simple = Component.translatable(descId + ".desc.simple").getString();
        } else if (Language.getInstance().has(descId + ".tooltip")) {
            simple = Component.translatable(descId + ".tooltip").getString();
        }
        String desc = it.getDescription().getString();
        if (Language.getInstance().has(descId + ".desc")) {
            desc = Component.translatable(descId + ".desc").getString();
        } else if (Language.getInstance().has(descId + ".tooltip")) {
            desc = Component.translatable(descId + ".tooltip").getString();
        }
        JsonObject o = baseJson(BuiltInRegistries.ITEM.getKey(it).toString(),
            it.getDescription().getString(), 0xFFFFFF, simple, desc, "item",
            BuiltInRegistries.ITEM.getKey(it).toLanguageKey());
        return o;
    }

    /**
     * 公共字段：id/name/color/小字/描述/小故事/设定。
     * type ∈ {role, modifier, item}；path 为故事/设定键后缀（道具用 toLanguageKey()）。
     */
    private static JsonObject baseJson(String id, String name, int color, String simpleDesc, String desc,
            String type, String path) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("color", color);
        if (simpleDesc != null) {
            o.addProperty("simpleDesc", simpleDesc);
        }
        o.addProperty("desc", desc);
        putIfTranslated(o, "story", "star.story." + type + "." + path);
        putIfTranslated(o, "settings", "star.settings." + type + "." + path);
        return o;
    }

    // ---------- 工具 ----------

    /** 翻译键存在才写入（与各页签 isVisible 的 Language.has 判断一致） */
    private static void putIfTranslated(JsonObject o, String field, String key) {
        if (Language.getInstance().has(key)) {
            o.addProperty(field, Component.translatable(key).getString());
        }
    }

    private static JsonArray refArray(Collection<SRERole> roles) {
        JsonArray arr = new JsonArray();
        for (SRERole r : roles) {
            try {
                JsonObject o = new JsonObject();
                o.addProperty("id", r.identifier().toString());
                o.addProperty("name", RoleUtils.getRoleName(r).getString());
                o.addProperty("color", r.color() & 0xFFFFFF);
                arr.add(o);
            } catch (Exception ignored) {
            }
        }
        return arr;
    }

    private static JsonArray refArrayModifiers(Set<SREModifier> mods) {
        JsonArray arr = new JsonArray();
        for (SREModifier m : mods) {
            try {
                JsonObject o = new JsonObject();
                o.addProperty("id", m.identifier().toString());
                o.addProperty("name", RoleUtils.getModifierName(m).getString());
                o.addProperty("color", m.color() & 0xFFFFFF);
                arr.add(o);
            } catch (Exception ignored) {
            }
        }
        return arr;
    }
}
