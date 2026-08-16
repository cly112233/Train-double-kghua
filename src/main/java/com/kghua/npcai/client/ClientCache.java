package com.kghua.npcai.client;

import com.kghua.npcai.data.CerebellumSettings;
import com.kghua.npcai.data.CompensationRule;
import com.kghua.npcai.data.ContributionRewardSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端缓存服务器同步下来的配置数据。
 */
public class ClientCache {
    private static final List<CompensationRule> COMPENSATION_RULES = new ArrayList<>();
    private static CerebellumSettings cerebellumSettings = new CerebellumSettings();
    private static String serverDefaultAiApiUrl = null;
    private static boolean mapGroupMember = false;
    private static boolean npcAdmin = false;
    /** 玩家是否拥有 OP 权限（服务端 hasPermissions(2) 同步），AI 对话指令视为管理员身份 */
    private static boolean op = false;
    // 问卷绑定邮箱状态（服务端同步）
    private static String boundMailQuestionnaireId = "";
    private static String boundMailQuestionnaireTitle = "";
    // 投稿奖励设置（服务端同步）
    private static ContributionRewardSettings contributionRewards = new ContributionRewardSettings();

    public static List<CompensationRule> getCompensationRules() {
        return new ArrayList<>(COMPENSATION_RULES);
    }

    public static String getServerDefaultAiApiUrl() {
        return serverDefaultAiApiUrl;
    }

    public static void setServerDefaultAiApiUrl(String url) {
        serverDefaultAiApiUrl = url;
    }

    public static boolean isMapGroupMember() {
        return mapGroupMember;
    }

    public static void setMapGroupMember(boolean member) {
        mapGroupMember = member;
    }

    public static boolean isNpcAdmin() {
        return npcAdmin;
    }

    public static void setNpcAdmin(boolean admin) {
        npcAdmin = admin;
    }

    public static boolean isOp() {
        return op;
    }

    public static void setOp(boolean value) {
        op = value;
    }

    public static String getBoundMailQuestionnaireId() {
        return boundMailQuestionnaireId;
    }

    public static String getBoundMailQuestionnaireTitle() {
        return boundMailQuestionnaireTitle;
    }

    public static void setMailBinding(String questionnaireId, String questionnaireTitle) {
        boundMailQuestionnaireId = questionnaireId != null ? questionnaireId : "";
        boundMailQuestionnaireTitle = questionnaireTitle != null ? questionnaireTitle : "";
    }

    /** 客户端判断当前是否对局进行中（大厅/准备阶段返回 false） */
    public static boolean isGameInProgress() {
        var gameComponent = io.wifi.starrailexpress.client.SREClient.gameComponent;
        return gameComponent != null
            && gameComponent.isRunning()
            && !io.wifi.starrailexpress.client.SREClient.isInLobby;
    }

    public static void setCompensationRules(List<CompensationRule> rules) {
        COMPENSATION_RULES.clear();
        COMPENSATION_RULES.addAll(rules);
    }

    public static ContributionRewardSettings getContributionRewards() {
        return contributionRewards;
    }

    public static void setContributionRewards(ContributionRewardSettings settings) {
        if (settings != null) contributionRewards = settings;
    }

    public static CerebellumSettings getCerebellumSettings() {
        return cerebellumSettings;
    }

    public static void setCerebellumSettings(CerebellumSettings settings) {
        cerebellumSettings = settings;
    }

    private static List<com.kghua.npcai.network.SyncCerebellumSettingsPacket.CerebellumEntry> cerebellumLeaderboard = new ArrayList<>();

    public static List<com.kghua.npcai.network.SyncCerebellumSettingsPacket.CerebellumEntry> getCerebellumLeaderboard() {
        return cerebellumLeaderboard;
    }

    public static void setCerebellumLeaderboard(List<com.kghua.npcai.network.SyncCerebellumSettingsPacket.CerebellumEntry> lb) {
        cerebellumLeaderboard = lb;
    }
}
