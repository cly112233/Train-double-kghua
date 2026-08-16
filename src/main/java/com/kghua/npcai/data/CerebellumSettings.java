package com.kghua.npcai.data;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;

/**
 * 小脑设置。
 * 计数规则：3 个勾选框（错杀好人/杀手互杀不含手雷/杀手互杀手雷）。
 * 惩罚内容：6 个修饰符勾选框（诅咒/高大/晕血症/纳税/偏执/沙哑），
 * 玩家达标后待下次成为杀手阵营角色时强制变为强盗并施加勾选的修饰符。
 */
public class CerebellumSettings {
    private boolean wrongKillInnocentEnabled = true;
    private boolean killerTeamKillNoGrenadeEnabled = false;
    private boolean killerTeamKillGrenadeOnlyEnabled = false;
    private int requiredDeaths = 1;

    // 惩罚修饰符开关（6个勾选框）
    private boolean cursedEnabled = false;
    private boolean tallEnabled = false;
    private boolean hemophobiaEnabled = false;
    private boolean taxedEnabled = false;
    private boolean paranoidEnabled = false;
    private boolean hoarseEnabled = false;

    /** 惩罚修饰符注册ID（基座mod已注册） */
    public static final ResourceLocation MOD_CURSED = ResourceLocation.fromNamespaceAndPath("stupid_express", "cursed");
    public static final ResourceLocation MOD_TALL = ResourceLocation.fromNamespaceAndPath("stupid_express", "tall");
    public static final ResourceLocation MOD_HEMOPHOBIA = ResourceLocation.fromNamespaceAndPath("noellesroles", "hemophobia");
    public static final ResourceLocation MOD_TAXED = ResourceLocation.fromNamespaceAndPath("noellesroles", "taxed");
    public static final ResourceLocation MOD_PARANOID = ResourceLocation.fromNamespaceAndPath("stupid_express", "paranoid");
    public static final ResourceLocation MOD_HOARSE = ResourceLocation.fromNamespaceAndPath("noellesroles", "hoarse");

    public boolean isWrongKillInnocentEnabled() {
        return wrongKillInnocentEnabled;
    }

    public void setWrongKillInnocentEnabled(boolean enabled) {
        this.wrongKillInnocentEnabled = enabled;
    }

    public boolean isKillerTeamKillNoGrenadeEnabled() {
        return killerTeamKillNoGrenadeEnabled;
    }

    public void setKillerTeamKillNoGrenadeEnabled(boolean enabled) {
        this.killerTeamKillNoGrenadeEnabled = enabled;
    }

    public boolean isKillerTeamKillGrenadeOnlyEnabled() {
        return killerTeamKillGrenadeOnlyEnabled;
    }

    public void setKillerTeamKillGrenadeOnlyEnabled(boolean enabled) {
        this.killerTeamKillGrenadeOnlyEnabled = enabled;
    }

    public int getRequiredDeaths() {
        return requiredDeaths;
    }

    public void setRequiredDeaths(int requiredDeaths) {
        this.requiredDeaths = Math.max(1, requiredDeaths);
    }

    public boolean isCursedEnabled() {
        return cursedEnabled;
    }

    public void setCursedEnabled(boolean enabled) {
        this.cursedEnabled = enabled;
    }

    public boolean isTallEnabled() {
        return tallEnabled;
    }

    public void setTallEnabled(boolean enabled) {
        this.tallEnabled = enabled;
    }

    public boolean isHemophobiaEnabled() {
        return hemophobiaEnabled;
    }

    public void setHemophobiaEnabled(boolean enabled) {
        this.hemophobiaEnabled = enabled;
    }

    public boolean isTaxedEnabled() {
        return taxedEnabled;
    }

    public void setTaxedEnabled(boolean enabled) {
        this.taxedEnabled = enabled;
    }

    public boolean isParanoidEnabled() {
        return paranoidEnabled;
    }

    public void setParanoidEnabled(boolean enabled) {
        this.paranoidEnabled = enabled;
    }

    public boolean isHoarseEnabled() {
        return hoarseEnabled;
    }

    public void setHoarseEnabled(boolean enabled) {
        this.hoarseEnabled = enabled;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("wrongKillInnocentEnabled", wrongKillInnocentEnabled);
        obj.addProperty("killerTeamKillNoGrenadeEnabled", killerTeamKillNoGrenadeEnabled);
        obj.addProperty("killerTeamKillGrenadeOnlyEnabled", killerTeamKillGrenadeOnlyEnabled);
        obj.addProperty("requiredDeaths", requiredDeaths);
        obj.addProperty("cursedEnabled", cursedEnabled);
        obj.addProperty("tallEnabled", tallEnabled);
        obj.addProperty("hemophobiaEnabled", hemophobiaEnabled);
        obj.addProperty("taxedEnabled", taxedEnabled);
        obj.addProperty("paranoidEnabled", paranoidEnabled);
        obj.addProperty("hoarseEnabled", hoarseEnabled);
        return obj;
    }

    public static CerebellumSettings fromJson(JsonObject obj) {
        CerebellumSettings settings = new CerebellumSettings();
        if (obj.has("wrongKillInnocentEnabled")) {
            settings.wrongKillInnocentEnabled = obj.get("wrongKillInnocentEnabled").getAsBoolean();
        } else if (obj.has("mode")) {
            // 兼容旧版单模式数据
            try {
                Mode oldMode = Mode.valueOf(obj.get("mode").getAsString());
                switch (oldMode) {
                    case WRONG_KILL_INNOCENT -> settings.wrongKillInnocentEnabled = true;
                    case KILLER_TEAM_KILL_NO_GRENADE -> settings.killerTeamKillNoGrenadeEnabled = true;
                    case KILLER_TEAM_KILL_GRENADE_ONLY -> settings.killerTeamKillGrenadeOnlyEnabled = true;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (obj.has("killerTeamKillNoGrenadeEnabled")) {
            settings.killerTeamKillNoGrenadeEnabled = obj.get("killerTeamKillNoGrenadeEnabled").getAsBoolean();
        }
        if (obj.has("killerTeamKillGrenadeOnlyEnabled")) {
            settings.killerTeamKillGrenadeOnlyEnabled = obj.get("killerTeamKillGrenadeOnlyEnabled").getAsBoolean();
        }
        settings.requiredDeaths = obj.has("requiredDeaths") ? obj.get("requiredDeaths").getAsInt() : 1;
        if (obj.has("cursedEnabled")) settings.cursedEnabled = obj.get("cursedEnabled").getAsBoolean();
        if (obj.has("tallEnabled")) settings.tallEnabled = obj.get("tallEnabled").getAsBoolean();
        if (obj.has("hemophobiaEnabled")) settings.hemophobiaEnabled = obj.get("hemophobiaEnabled").getAsBoolean();
        if (obj.has("taxedEnabled")) settings.taxedEnabled = obj.get("taxedEnabled").getAsBoolean();
        if (obj.has("paranoidEnabled")) settings.paranoidEnabled = obj.get("paranoidEnabled").getAsBoolean();
        if (obj.has("hoarseEnabled")) settings.hoarseEnabled = obj.get("hoarseEnabled").getAsBoolean();
        return settings;
    }

    /**
     * 旧版模式枚举，仅用于兼容旧数据。
     */
    private enum Mode {
        WRONG_KILL_INNOCENT,
        KILLER_TEAM_KILL_NO_GRENADE,
        KILLER_TEAM_KILL_GRENADE_ONLY
    }
}
