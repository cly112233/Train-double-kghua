package com.kghua.npcai.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kghua.npcai.NpcAiMod;
import com.kghua.npcai.data.Contribution;
import com.kghua.npcai.data.ContributionRewardStorage;
import com.kghua.npcai.data.ContributionRewardSettings;
import com.kghua.npcai.data.ContributionStorage;
import com.kghua.npcai.data.NpcAdminStorage;
import com.kghua.npcai.webbridge.OfflineUuid;
import com.kghua.npcai.webbridge.WebException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 投稿服务（网站 contrib.* 与游戏内投稿互通）：
 * - 浏览：仅已审核 + 当期（与游戏内浏览口径一致，奖励期归属=审核通过当期）
 * - 提交/点赞：校验规则与 SubmitContributionPacket / LikeContributionPacket 完全一致
 * - 审核：复刻 ApproveContributionPacket（通过=发奖励邮件+改期数，驳回=删除+驳回邮件）
 */
public final class ContributionService {

    private ContributionService() {}

    /** 浏览：仅已审核 + 当期 + 点赞状态（与 sendContributionsTo 的口径一致，前端过滤） */
    public static JsonObject list(MinecraftServer server, String gameId) {
        UUID uuid = OfflineUuid.of(gameId);
        List<Contribution> all = ContributionStorage.loadAll();
        int currentPeriod = Contribution.getCurrentPeriod();

        JsonArray arr = new JsonArray();
        for (Contribution c : all) {
            if (!c.isApproved()) continue;
            if (c.getPeriod() != currentPeriod) continue;
            JsonObject o = contributionJson(c);
            o.addProperty("liked", ContributionStorage.hasLiked(uuid, c.getId()));
            arr.add(o);
        }

        JsonObject r = new JsonObject();
        r.addProperty("currentPeriod", currentPeriod);
        r.addProperty("periodEndAt", Contribution.getPeriodEndAt(currentPeriod));
        r.addProperty("remainingLikes", ContributionStorage.getRemainingLikes(uuid));
        r.addProperty("dailyLikeLimit", ContributionStorage.DAILY_LIKE_LIMIT);
        r.addProperty("maxSubmissions", ContributionStorage.MAX_SUBMISSIONS_PER_PERIOD);
        r.addProperty("mySubmissions",
            ContributionStorage.countSubmissions(uuid, currentPeriod));
        r.add("contributions", arr);
        return r;
    }

    /** 我的投稿：该玩家全部投稿（含未审核/历史期），管理端可看全部 */
    public static JsonObject mine(MinecraftServer server, String gameId) {
        UUID uuid = OfflineUuid.of(gameId);
        JsonArray arr = new JsonArray();
        for (Contribution c : ContributionStorage.loadAll()) {
            if (c.getAuthorId() != null && c.getAuthorId().equals(uuid)) {
                arr.add(contributionJson(c));
            }
        }
        JsonObject r = new JsonObject();
        r.add("contributions", arr);
        return r;
    }

    /** 提交（校验与 SubmitContributionPacket 完全一致） */
    public static JsonObject submit(MinecraftServer server, String gameId, String type, String title,
            String shortDesc, String description, String shop, String background, String faction)
        throws WebException {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            throw new WebException("E_VALIDATION", "投稿标题不能为空");
        }
        if (!Contribution.TYPE_ROLE.equals(type) && !Contribution.TYPE_MODIFIER.equals(type)) {
            throw new WebException("E_VALIDATION", "投稿类型无效");
        }
        String f = faction == null ? "" : faction.trim();
        if (Contribution.TYPE_ROLE.equals(type) && !Arrays.asList(Contribution.FACTIONS).contains(f)) {
            throw new WebException("E_VALIDATION", "请选择阵营");
        }
        UUID uuid = OfflineUuid.of(gameId);
        if (ContributionStorage.countSubmissions(uuid, Contribution.getCurrentPeriod())
            >= ContributionStorage.MAX_SUBMISSIONS_PER_PERIOD) {
            throw new WebException("E_VALIDATION",
                "本期两个分区合计最多投稿" + ContributionStorage.MAX_SUBMISSIONS_PER_PERIOD + "个内容");
        }

        Contribution c = new Contribution(UUID.randomUUID());
        c.setType(type);
        c.setTitle(t);
        c.setShortDesc(shortDesc == null ? "" : shortDesc.trim());
        c.setDescription(description == null ? "" : description.trim());
        c.setShop(shop == null ? "" : shop.trim());
        c.setBackground(background == null ? "" : background.trim());
        c.setFaction(f);
        c.setAuthorName(gameId);
        c.setAuthorId(uuid);
        c.setCreatedAt(System.currentTimeMillis());
        c.setPeriod(Contribution.getCurrentPeriod());
        ContributionStorage.save(c);

        JsonObject r = new JsonObject();
        r.addProperty("submitted", true);
        r.addProperty("id", c.getId().toString());
        return r;
    }

    /** 点赞/取消点赞（校验与 LikeContributionPacket 完全一致：禁自赞、每日上限） */
    public static JsonObject like(MinecraftServer server, String gameId, UUID contributionId)
        throws WebException {
        UUID uuid = OfflineUuid.of(gameId);
        Contribution likeTarget = ContributionStorage.get(contributionId);
        if (likeTarget != null && likeTarget.getAuthorId() != null
            && likeTarget.getAuthorId().equals(uuid)) {
            throw new WebException("E_VALIDATION", "不能给自己的投稿点赞");
        }
        Boolean result = ContributionStorage.toggleLike(uuid, contributionId);
        if (result == null) {
            throw new WebException("E_VALIDATION", "投稿不存在或今日点赞次数已用完");
        }
        JsonObject r = new JsonObject();
        r.addProperty("liked", result);
        r.addProperty("remainingLikes", ContributionStorage.getRemainingLikes(uuid));
        r.addProperty("likes", likeTarget == null ? 0 : likeTarget.getLikes());
        return r;
    }

    /** 管理端审核（复刻 ApproveContributionPacket：奖励邮件/改期数/驳回删除） */
    public static JsonObject approve(MinecraftServer server, String adminName, UUID contributionId, boolean approved)
        throws WebException {
        if (!NpcAdminStorage.isAdmin(OfflineUuid.of(adminName))) {
            throw new WebException("E_PERMISSION", "无权限");
        }
        Contribution c = ContributionStorage.get(contributionId);
        if (c == null) {
            throw new WebException("E_NOT_FOUND", "投稿不存在");
        }
        if (c.isApproved()) {
            throw new WebException("E_VALIDATION", "该投稿已审核通过");
        }

        if (approved) {
            c.setApproved(true);
            c.setPeriod(Contribution.getCurrentPeriod()); // 作品归属期数 = 审核通过当期
            ContributionStorage.save(c);
            ContributionRewardSettings settings = ContributionRewardStorage.getSettings();
            String rewardSummary = NpcAiMod.buildRewardSummary(settings.getPerSubmitCards(),
                settings.getPerSubmitLottery());
            NpcAiMod.sendRewardMail(server, c.getAuthorName(), "投稿奖励！",
                "恭喜您的投稿通过审核，请点击下方领取投稿奖励！"
                    + (rewardSummary.isEmpty() ? "" : "\n奖励：" + rewardSummary));
            if (c.getAuthorId() != null) {
                ServerPlayer author = server.getPlayerList().getPlayer(c.getAuthorId());
                if (author != null) {
                    NpcAiMod.grantReward(author, settings.getPerSubmitCards(), settings.getPerSubmitLottery());
                } else {
                    // 离线：奖励计入待领，上线时补发
                    ContributionRewardStorage.addPending(c.getAuthorId(),
                        settings.getPerSubmitCards(), settings.getPerSubmitLottery());
                }
            }
        } else {
            // 驳回：删除作品 + 发送驳回邮件（无奖励）
            NpcAiMod.sendRewardMail(server, c.getAuthorName(), "投稿驳回！",
                "您的投稿由于特殊原因没有通过审核，无法领取投稿奖励。");
            ContributionStorage.delete(c.getId());
        }
        JsonObject r = new JsonObject();
        r.addProperty("processed", true);
        r.addProperty("approved", approved);
        return r;
    }

    /** 管理端待审核列表（全部未审核，新→旧） */
    public static JsonObject pending(MinecraftServer server, String gameId) throws WebException {
        if (!NpcAdminStorage.isAdmin(OfflineUuid.of(gameId))) {
            throw new WebException("E_PERMISSION", "无权限");
        }
        JsonArray arr = new JsonArray();
        List<Contribution> all = new ArrayList<>(ContributionStorage.loadAll());
        all.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        for (Contribution c : all) {
            if (!c.isApproved()) {
                arr.add(contributionJson(c));
            }
        }
        JsonObject r = new JsonObject();
        r.add("contributions", arr);
        return r;
    }

    /** 管理端导出单个投稿（格式与 ContributionStorage.exportToMarkdownText 一致） */
    public static JsonObject export(MinecraftServer server, String gameId, UUID contributionId)
        throws WebException {
        if (!NpcAdminStorage.isAdmin(OfflineUuid.of(gameId))) {
            throw new WebException("E_PERMISSION", "无权限");
        }
        Contribution c = ContributionStorage.get(contributionId);
        if (c == null) {
            throw new WebException("E_NOT_FOUND", "投稿不存在");
        }
        String[] result = ContributionStorage.exportToMarkdownText(c);
        JsonObject r = new JsonObject();
        r.addProperty("fileName", result[0]);
        r.addProperty("content", result[1]);
        return r;
    }

    // ---------- 工具 ----------

    private static JsonObject contributionJson(Contribution c) {
        JsonObject o = new JsonObject();
        o.addProperty("id", c.getId().toString());
        o.addProperty("type", c.getType());
        o.addProperty("title", c.getTitle());
        o.addProperty("shortDesc", c.getShortDesc());
        o.addProperty("description", c.getDescription());
        o.addProperty("shop", c.getShop());
        o.addProperty("background", c.getBackground());
        o.addProperty("faction", c.getFaction());
        o.addProperty("authorName", c.getAuthorName());
        o.addProperty("createdAt", c.getCreatedAt());
        o.addProperty("likes", c.getLikes());
        o.addProperty("period", c.getPeriod());
        o.addProperty("approved", c.isApproved());
        return o;
    }
}
