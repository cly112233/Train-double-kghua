package com.kghua.npcai.mailbridge;

import com.habitrain.lottery.mail.LocalMailboxStore;
import com.habitrain.lottery.mail.LocalMailboxStore.MailJson;
import com.habitrain.lottery.mail.MailDraft;
import com.habitrain.lottery.mail.MailService;
import com.habitrain.lottery.network.LotteryNetwork;
import com.habitrain.lottery.storage.WorldLotteryPaths;
import com.kghua.npcai.NpcAiMod;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;

/**
 * 邮件桥接层：将 npcai 的邮件业务接入第三方模组 habitrain_lottery 的邮箱系统
 * （邮箱方块 habitrain_lottery:mailbox，数据存 &lt;世界&gt;/habitrain_lottery/mail/players/&lt;uuid&gt;.json）。
 *
 * 注意：
 * - 未读数用 LocalMailboxStore.load 统计（无副作用），绝不能用 MailService.list ——
 *   它会把未读邮件置为已读并落盘，破坏未读状态。
 * - habitrain 的领取（MailService.claim）只解析 hltmail:v1: 结构化奖励
 *   （DRAWS/COINS/FACTION_CARD），不执行任意命令 → 我方奖励（身份卡）
 *   改为在投递时立即发放给收件人，邮件作为通知凭证。
 * - 2026-08-16：抽奖系统已断开（官方皮肤系统不开放，自建前不接），
 *   抽奖次数奖励不再发放（lottery 参数保留仅为兼容调用方，忽略）。
 * - 全部投递发生在玩家在线时（管理员投递在线目标 + JOIN 时补发），无需 sendOffline。
 */
public class MailBridge {

    private MailBridge() {}

    /** 4种身份卡类型（顺序与基座mod进度背包展示一致：杀手/平民/独赢中立/杀手中立） */
    private static final ProgressionState.FactionCardType[] REWARD_CARD_TYPES = {
        ProgressionState.FactionCardType.KILLER,
        ProgressionState.FactionCardType.CIVILIAN,
        ProgressionState.FactionCardType.NEUTRAL,
        ProgressionState.FactionCardType.NEUTRAL_FOR_KILLER
    };

    /** 未读邮件数（未过期 && 未领取 && 未读）。habitrain 未初始化或异常时安全返回 0。 */
    public static int getUnreadCount(ServerPlayer player) {
        try {
            if (!WorldLotteryPaths.ready()) return 0;
            long now = System.currentTimeMillis();
            int count = 0;
            for (MailJson m : LocalMailboxStore.load(player.getUUID())) {
                if (m.claimed) continue;
                if (m.read) continue;
                if (m.expiresAt > 0 && now > m.expiresAt) continue;
                count++;
            }
            return count;
        } catch (Throwable e) {
            NpcAiMod.LOGGER.warn("MailBridge: failed to count unread mails for {}", player.getName().getString(), e);
            return 0;
        }
    }

    /**
     * 投递邮件到 habitrain 邮箱：先立即发放奖励（4种身份卡各 cards[i] 张），
     * 再通过 MailService.send 投递（邮件作为通知凭证）。返回是否投递成功。
     * lottery 参数保留仅为兼容调用方，抽奖系统已断开，忽略。
     */
    public static boolean sendMail(ServerPlayer target, String sender, String title,
                                   String content, long expiresAt, int[] cards, int lottery) {
        try {
            if (!WorldLotteryPaths.ready()) return false;
            grantReward(target, cards, lottery);
            MailDraft draft = new MailDraft(sender, title, content, expiresAt, Collections.emptyList());
            return MailService.send(target, draft);
        } catch (Throwable e) {
            NpcAiMod.LOGGER.error("MailBridge: failed to send mail '{}' to {}", title, target.getName().getString(), e);
            return false;
        }
    }

    /** 发放奖励：身份卡（基座mod进度背包）。lottery 参数保留仅为兼容调用方，抽奖系统已断开，忽略。 */
    private static void grantReward(ServerPlayer player, int[] cards, int lottery) {
        for (int i = 0; i < 4 && cards != null && i < cards.length; i++) {
            if (cards[i] > 0) {
                ProgressionDataManager.addFactionCard(player, REWARD_CARD_TYPES[i], cards[i]);
            }
        }
        // 抽奖系统已断开（2026-08-16），不再发放抽奖次数
    }

    /** 打开 habitrain_lottery 的邮箱界面（与邮箱方块右键同界面）。 */
    public static void openMailbox(ServerPlayer player) {
        try {
            LotteryNetwork.sendOpenMailbox(player);
        } catch (Throwable e) {
            NpcAiMod.LOGGER.warn("MailBridge: failed to open mailbox for {}", player.getName().getString(), e);
        }
    }
}
