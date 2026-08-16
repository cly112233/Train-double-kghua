package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.CerebellumSettings;
import com.kghua.npcai.data.ContributionRewardSettings;
import com.kghua.npcai.data.CompensationRule;
import com.kghua.npcai.data.FeedbackEntry;
import com.kghua.npcai.data.MailRecord;
import com.kghua.npcai.data.NpcData;
import com.kghua.npcai.data.Questionnaire;
import com.kghua.npcai.data.TeleportPoint;
import com.kghua.npcai.client.ClientCache;
import com.kghua.npcai.network.*;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class NpcAdminScreen extends Screen {
    private final int entityId;
    private String displayName = "";
    private String skinName = "";
    private double npcX;
    private double npcY;
    private double npcZ;
    private NpcData.FollowMode followMode = NpcData.FollowMode.FIXED;
    private NpcData.ViewMode viewMode = NpcData.ViewMode.RANDOM;
    private float scale = 1.0f;
    private String heldItem = "";
    private final List<TeleportPoint> points = new ArrayList<>();
    private final List<Questionnaire> questionnaires = new ArrayList<>();
    private final List<SyncPlayerListPacket.PlayerInfo> players = new ArrayList<>();
    private final List<MailRecord> mails = new ArrayList<>();
    private final List<CompensationRule> compensationRules = new ArrayList<>();
    private CerebellumSettings cerebellumSettings = new CerebellumSettings();
    private int lastMouseX;
    private int lastMouseY;
    private int cerebellumExportBtnX = -1;
    private int cerebellumExportBtnY = -1;
    private List<com.kghua.npcai.network.SyncCerebellumSettingsPacket.CerebellumEntry> cerebellumLeaderboard = new ArrayList<>();
    private double cerebellumPreviewScroll = 0;

    public void setCerebellumLeaderboard(List<com.kghua.npcai.network.SyncCerebellumSettingsPacket.CerebellumEntry> lb) {
        this.cerebellumLeaderboard = lb;
    }

    private int activeTab = 0; // 0=设置, 1=传送, 2=管理, 3=问卷, 4=邮箱, 5=补偿, 6=小脑, 7=反馈
    private EditBox nameBox;
    private EditBox skinBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox scaleBox;
    private EditBox heldItemBox;
    private Button modeButton;
    private Button viewModeButton;
    private EditBox roamXBox;
    private EditBox roamYBox;
    private EditBox roamZBox;
    private EditBox roamRadiusBox;
    private double roamX, roamY, roamZ, roamRadius = -1;
    // 设置页滚动
    private double settingsScrollOffset = 0;
    private net.minecraft.client.gui.components.Button saveBtn;
    private net.minecraft.client.gui.components.Button deleteBtn;
    private int nameBaseY;
    private int skinBaseY;
    private int coordBaseY;
    private int roamBaseY;
    private int modeBaseY;
    private int viewModeBaseY;
    private int scaleBaseY;
    private int heldItemBaseY;
    private int saveBtnBaseY;
    private int deleteBtnBaseY;

    private void applySettingsScroll() {
        int py = (this.height - PANEL_H) / 2;
        int scrollTop = py + 48;
        int scrollBottom = py + PANEL_H - 10;
        int totalHeight = Math.max(0, deleteBtnBaseY + 20 - scrollTop);
        int visibleHeight = scrollBottom - scrollTop;
        if (totalHeight <= visibleHeight) {
            settingsScrollOffset = 0;
        } else {
            settingsScrollOffset = Math.max(0, Math.min(settingsScrollOffset, totalHeight - visibleHeight));
        }
        setWidgetScroll(nameBox, nameBaseY, scrollTop, scrollBottom);
        setWidgetScroll(skinBox, skinBaseY, scrollTop, scrollBottom);
        setWidgetScroll(xBox, coordBaseY, scrollTop, scrollBottom);
        setWidgetScroll(yBox, coordBaseY, scrollTop, scrollBottom);
        setWidgetScroll(zBox, coordBaseY, scrollTop, scrollBottom);
        if (roamXBox != null) {
            setWidgetScroll(roamXBox, roamBaseY, scrollTop, scrollBottom);
            setWidgetScroll(roamYBox, roamBaseY, scrollTop, scrollBottom);
            setWidgetScroll(roamZBox, roamBaseY, scrollTop, scrollBottom);
            setWidgetScroll(roamRadiusBox, roamBaseY, scrollTop, scrollBottom);
        }
        setWidgetScroll(modeButton, modeBaseY, scrollTop, scrollBottom);
        setWidgetScroll(viewModeButton, viewModeBaseY, scrollTop, scrollBottom);
        setWidgetScroll(scaleBox, scaleBaseY, scrollTop, scrollBottom);
        // 保存/删除按钮用独立滚动（非setWidgetScroll，因为按钮已用addRenderableWidget）
        int saveY = saveBtnBaseY - (int) settingsScrollOffset;
        int delY = deleteBtnBaseY - (int) settingsScrollOffset;
        if (saveBtn != null) { saveBtn.setY(saveY); saveBtn.visible = saveY + 20 > scrollTop && saveY < scrollBottom; }
        if (deleteBtn != null) { deleteBtn.setY(delY); deleteBtn.visible = delY + 20 > scrollTop && delY < scrollBottom; }
    }

    private static final int PANEL_W = 520;
    private static final int PANEL_H = 320;
    private static final int CARD_H = 44;
    private static final int CARD_GAP = 6;
    private static final int COLUMNS = 4;
    // 投稿奖励编辑区布局常量（左右两个独立页面：左侧每次投稿固定顶部，右侧前三名独立滚动）
    private static final int REWARD_CONTENT_TOP_OFFSET = 76;
    private static final int REWARD_CONTENT_BOTTOM_OFFSET = 40;
    private static final int REWARD_ROW_H = 20;
    private static final int REWARD_SECTION_H = 116; // 16标题 + 5行*20
    private static final int REWARD_SECTION_GAP = 4;
    private static final int REWARD_LEFT_X = 12;
    private static final int REWARD_LEFT_BOX_X = 104;
    private static final int REWARD_RIGHT_X = 262;
    private static final int REWARD_RIGHT_BOX_X = 354;
    private static final int REWARD_DIVIDER_X = 257;
    private double scrollOffset = 0;

    private final String[] TAB_LABELS = {"设置", "传送点设置", "管理设置", "问卷调查", "发布邮箱", "补偿设置", "小脑设置", "投稿设置", "反馈设置"};

    // 投稿设置状态
    private final List<com.kghua.npcai.data.Contribution> contributions = new ArrayList<>();
    private int contributionSubTab = 0; // 0=角色, 1=修饰符
    private double contributionScroll = 0;
    private int contributionSelectedPeriod = com.kghua.npcai.data.Contribution.getCurrentPeriod();
    private boolean contributionPeriodMenuOpen = false;
    private double contributionPeriodScroll = 0;
    private int contributionMaxPeriod = com.kghua.npcai.data.Contribution.getCurrentPeriod();
    // 投稿奖励设置（tab7第三子分区：每次投稿 + 每期前三名）
    private ContributionRewardSettings editingContributionRewards = new ContributionRewardSettings();
    private final List<EditBox> contributionRewardBoxes = new ArrayList<>();
    private final List<Integer> contributionRewardBaseYs = new ArrayList<>();

    public void setContributions(List<com.kghua.npcai.data.Contribution> list) {
        this.contributions.clear();
        this.contributions.addAll(list);
        // 更新最大期数
        for (com.kghua.npcai.data.Contribution c : list) {
            if (c.getPeriod() > contributionMaxPeriod) {
                contributionMaxPeriod = c.getPeriod();
            }
        }
    }

    // 传送点分类与搜索
    private int teleportCategoryIndex = 0;
    private EditBox teleportSearchBox;
    private String teleportSearchText = "";
    // 删除确认状态
    private String pendingDeleteTeleport = null;
    private UUID pendingDeleteQuestionnaire = null;
    private UUID pendingDeleteMail = null;
    private boolean pendingDeleteNpc = false;

    // 补偿设置内嵌编辑状态
    private CompensationRule editingCompensationRule = null;
    private boolean compensationIsAdding = false;
    private EditBox compensationTitleBox;
    private Button compensationReasonButton;
    private EditBox compensationRequiredDeathsBox;
    private final List<EditBox> compensationCommandNameBoxes = new ArrayList<>();
    private final List<EditBox> compensationCommandCmdBoxes = new ArrayList<>();
    private final List<Button> compensationCommandDeleteButtons = new ArrayList<>();

    // 小脑设置内嵌编辑状态
    private CerebellumSettings editingCerebellumSettings;
    private EditBox cerebellumRequiredDeathsBox;
    private final int[] cerebellumCheckboxX = new int[3];
    private final int[] cerebellumCheckboxY = new int[3];
    private final int[] cerebellumCheckboxW = new int[3];
    private final int[] cerebellumCheckboxH = new int[3];
    private final int[] cerebellumCheckboxBaseY = new int[3];
    // 惩罚修饰符勾选框（诅咒/高大/晕血症/纳税/偏执/沙哑）
    private final int[] cerebellumModifierCheckboxX = new int[6];
    private final int[] cerebellumModifierCheckboxY = new int[6];
    private final int[] cerebellumModifierCheckboxW = new int[6];
    private final int[] cerebellumModifierCheckboxH = new int[6];
    private final int[] cerebellumModifierCheckboxBaseY = new int[6];

    // 补偿编辑滚动基准位置
    private int compensationTitleBaseY;
    private int compensationReasonBaseY;
    private int compensationRequiredDeathsBaseY;
    private int compensationAddCommandBaseY;
    private Button compensationAddCommandButton;
    private final List<Integer> compensationCommandNameBaseYs = new ArrayList<>();
    private final List<Integer> compensationCommandCmdBaseYs = new ArrayList<>();
    private final List<Integer> compensationCommandDeleteBaseYs = new ArrayList<>();

    // 小脑编辑滚动基准位置
    private int cerebellumRequiredDeathsBaseY;

    // 反馈设置内嵌状态
    private static final DateTimeFormatter FEEDBACK_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int FEEDBACK_CARD_H = 44;
    private static final int FEEDBACK_CARD_GAP = 4;
    private final List<FeedbackEntry> feedbackEntries = new ArrayList<>();
    private final Set<String> feedbackSelected = new HashSet<>();
    private double feedbackScrollOffset = 0;
    private EditBox feedbackStartYearBox;
    private EditBox feedbackStartMonthBox;
    private EditBox feedbackStartDayBox;
    private EditBox feedbackEndYearBox;
    private EditBox feedbackEndMonthBox;
    private EditBox feedbackEndDayBox;

    // 发布邮箱内嵌编辑状态
    private static final DateTimeFormatter MAIL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private EditBox mailTitleBox;
    private EditBox mailContentBox;
    private final EditBox[] mailCardBoxes = new EditBox[4]; // 4种身份卡数量输入框
    private EditBox mailLotteryBox;
    private EditBox mailNamesBox;
    private EditBox mailStartBox;
    private EditBox mailEndBox;
    private Button mailModeButton;
    private int mailSendMode = 0; // 0=全部, 1=白名单, 2=黑名单
    // 问卷绑定邮箱（发送模式3：绑定问卷，发布不直接发送，玩家提交问卷后自动发送）
    private String boundMailQuestionnaireId = "";
    private String boundMailQuestionnaireTitle = "";
    private Button mailChooseButton;
    // 邮件编辑器内容持久化（打开选择弹窗/返回/切换标签重建后不丢失）
    private String mailEditorTitle = "";
    private String mailEditorContent = "";
    private String[] mailEditorCards = {"0", "0", "0", "0"};
    private String mailEditorLottery = "0";
    private String mailEditorNames = "";
    private String mailEditorStart = "";
    private String mailEditorEnd = "";

    public NpcAdminScreen(int entityId) {
        super(Component.literal("NPC 管理"));
        this.entityId = entityId;
        this.editingCerebellumSettings = new CerebellumSettings();
        // 从客户端缓存恢复已保存的设置（深拷贝，编辑过程不影响缓存），未同步过则用全零默认值
        ContributionRewardSettings cached = ClientCache.getContributionRewards();
        this.editingContributionRewards = cached != null
            ? ContributionRewardSettings.fromJson(cached.toJson())
            : new ContributionRewardSettings();
    }

    public void setData(String displayName, String skinName, List<TeleportPoint> points) {
        this.displayName = displayName;
        this.skinName = skinName;
        this.points.clear();
        this.points.addAll(points);
    }

    public void setCoords(double x, double y, double z) {
        this.npcX = x;
        this.npcY = y;
        this.npcZ = z;
    }

    public void setFollowMode(NpcData.FollowMode mode) {
        this.followMode = mode;
    }

    public void setViewMode(NpcData.ViewMode mode) {
        this.viewMode = mode;
    }

    public void setScale(float scale) {
        this.scale = Math.max(0.2f, Math.min(2.0f, scale));
    }

    public void setHeldItem(String heldItem) {
        this.heldItem = heldItem;
    }

    public void setRoam(double x, double y, double z, double radius) {
        this.roamX = x;
        this.roamY = y;
        this.roamZ = z;
        this.roamRadius = radius;
    }

    public void setQuestionnaires(List<Questionnaire> questionnaires) {
        this.questionnaires.clear();
        this.questionnaires.addAll(questionnaires);
    }

    public void setPlayerList(List<SyncPlayerListPacket.PlayerInfo> players) {
        this.players.clear();
        this.players.addAll(players);
    }

    public void setMails(List<MailRecord> mails) {
        this.mails.clear();
        this.mails.addAll(mails);
    }

    /** 服务端同步的问卷绑定状态：刷新“选择问卷”按键文本 */
    public void setMailBinding(String questionnaireId, String questionnaireTitle) {
        this.boundMailQuestionnaireId = questionnaireId != null ? questionnaireId : "";
        this.boundMailQuestionnaireTitle = questionnaireTitle != null ? questionnaireTitle : "";
        refreshMailChooseButton();
    }

    public String getBoundMailQuestionnaireId() {
        return boundMailQuestionnaireId;
    }

    public String getBoundMailQuestionnaireTitle() {
        return boundMailQuestionnaireTitle;
    }

    /** 打开问卷选择弹窗（发送模式为“绑定问卷”时） */
    private void openMailBindScreen() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new MailBindScreen(this, questionnaires));
        }
    }

    private void refreshMailChooseButton() {
        if (mailChooseButton == null) return;
        mailChooseButton.visible = mailSendMode == 3;
        if (mailSendMode == 3) {
            mailChooseButton.setMessage(Component.literal(mailChooseButtonLabel()));
        }
    }

    private String mailChooseButtonLabel() {
        String text = boundMailQuestionnaireTitle.isEmpty() ? "未选择" : boundMailQuestionnaireTitle;
        if (this.font != null && this.font.width(text) > 270) {
            text = this.font.plainSubstrByWidth(text, 270) + "...";
        }
        return "选择问卷：" + text;
    }

    /** 采集当前邮件编辑区模板快照（绑定问卷时随包发给服务端） */
    public BindMailQuestionnairePacket captureMailBindSnapshot(String questionnaireId) {
        int[] cards = readMailEditorCards();
        int lottery = 0;
        if (mailLotteryBox != null) {
            try {
                lottery = Math.max(0, Integer.parseInt(mailLotteryBox.getValue().trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        long endAt = 0;
        if (mailEndBox != null) {
            endAt = parseMailTime(mailEndBox.getValue().trim());
        }
        return new BindMailQuestionnairePacket(questionnaireId,
            mailTitleBox.getValue().trim(), mailContentBox.getValue().trim(),
            cards, lottery, endAt);
    }

    public void setFeedback(List<FeedbackEntry> entries) {
        this.feedbackEntries.clear();
        this.feedbackEntries.addAll(entries);
        this.feedbackSelected.clear();
    }

    public void setCompensationRules(List<CompensationRule> rules) {
        this.compensationRules.clear();
        this.compensationRules.addAll(rules);
        if (editingCompensationRule != null && !compensationIsAdding) {
            for (CompensationRule r : compensationRules) {
                if (r.getId().equals(editingCompensationRule.getId())) {
                    editingCompensationRule = r;
                    break;
                }
            }
        }
    }

    public void setCerebellumSettings(CerebellumSettings settings) {
        this.cerebellumSettings = settings;
        if (editingCerebellumSettings == null) {
            this.editingCerebellumSettings = new CerebellumSettings();
        }
        this.editingCerebellumSettings.setWrongKillInnocentEnabled(settings.isWrongKillInnocentEnabled());
        this.editingCerebellumSettings.setKillerTeamKillNoGrenadeEnabled(settings.isKillerTeamKillNoGrenadeEnabled());
        this.editingCerebellumSettings.setKillerTeamKillGrenadeOnlyEnabled(settings.isKillerTeamKillGrenadeOnlyEnabled());
        this.editingCerebellumSettings.setRequiredDeaths(settings.getRequiredDeaths());
        this.editingCerebellumSettings.setCursedEnabled(settings.isCursedEnabled());
        this.editingCerebellumSettings.setTallEnabled(settings.isTallEnabled());
        this.editingCerebellumSettings.setHemophobiaEnabled(settings.isHemophobiaEnabled());
        this.editingCerebellumSettings.setTaxedEnabled(settings.isTaxedEnabled());
        this.editingCerebellumSettings.setParanoidEnabled(settings.isParanoidEnabled());
        this.editingCerebellumSettings.setHoarseEnabled(settings.isHoarseEnabled());
    }

    /** 服务端同步的投稿奖励设置（投稿奖励分区编辑框数值） */
    public void setContributionRewards(ContributionRewardSettings settings) {
        if (settings == null) return;
        for (int i = 0; i < 4; i++) editingContributionRewards.setPerSubmitCard(i, settings.getPerSubmitCard(i));
        editingContributionRewards.setPerSubmitLottery(settings.getPerSubmitLottery());
        for (int p = 0; p < 3; p++) {
            for (int i = 0; i < 4; i++) editingContributionRewards.setPlaceCard(p, i, settings.getPlaceCard(p, i));
            editingContributionRewards.setPlaceLottery(p, settings.getPlaceLottery(p));
        }
    }

    public int getEntityId() {
        return entityId;
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        // 首页不显示返回按钮，仅子页面显示
        if (activeTab == 0) {
            // 垂直布局：名称/皮肤/坐标/活动范围(坐标下)/移动/视角/缩放/手持物/保存/删除
            // 每行22px，从 py+52 开始，超出滚动
            int rowY = py + 52;
            int inputX = px + 90;
            int inputW = 180;

            this.nameBox = new EditBox(this.font, inputX, rowY, inputW, 18, Component.literal(""));
            this.nameBox.setValue(displayName);
            this.addRenderableWidget(this.nameBox);
            nameBaseY = rowY;
            rowY += 22;

            this.skinBox = new EditBox(this.font, inputX, rowY, inputW, 18, Component.literal(""));
            this.skinBox.setValue(skinName);
            this.addRenderableWidget(this.skinBox);
            skinBaseY = rowY;
            rowY += 22;

            this.xBox = new EditBox(this.font, inputX, rowY, 50, 18, Component.literal(""));
            this.yBox = new EditBox(this.font, inputX + 60, rowY, 50, 18, Component.literal(""));
            this.zBox = new EditBox(this.font, inputX + 120, rowY, 50, 18, Component.literal(""));
            this.xBox.setValue(formatCoord(npcX));
            this.yBox.setValue(formatCoord(npcY));
            this.zBox.setValue(formatCoord(npcZ));
            this.addRenderableWidget(this.xBox);
            this.addRenderableWidget(this.yBox);
            this.addRenderableWidget(this.zBox);
            coordBaseY = rowY;
            rowY += 22;

            // 活动范围（坐标下方，始终显示）
            this.roamXBox = new EditBox(this.font, inputX, rowY, 50, 18, Component.literal(""));
            this.roamYBox = new EditBox(this.font, inputX + 60, rowY, 50, 18, Component.literal(""));
            this.roamZBox = new EditBox(this.font, inputX + 120, rowY, 50, 18, Component.literal(""));
            this.roamRadiusBox = new EditBox(this.font, inputX + 200, rowY, 50, 18, Component.literal(""));
            this.roamXBox.setValue(formatCoord(roamX));
            this.roamYBox.setValue(formatCoord(roamY));
            this.roamZBox.setValue(formatCoord(roamZ));
            this.roamRadiusBox.setValue(roamRadius > 0 ? formatCoord(roamRadius) : "");
            this.roamRadiusBox.setHint(Component.literal("半径"));
            this.addRenderableWidget(this.roamXBox);
            this.addRenderableWidget(this.roamYBox);
            this.addRenderableWidget(this.roamZBox);
            this.addRenderableWidget(this.roamRadiusBox);
            roamBaseY = rowY;
            rowY += 22;

            this.modeButton = new NoShadowButton(inputX, rowY, inputW, 20,
                Component.literal(modeLabel()), btn -> toggleMode());
            this.addRenderableWidget(this.modeButton);
            modeBaseY = rowY;
            rowY += 22;

            this.viewModeButton = new NoShadowButton(inputX, rowY, inputW, 20,
                Component.literal(viewModeLabel()), btn -> toggleViewMode());
            this.addRenderableWidget(this.viewModeButton);
            viewModeBaseY = rowY;
            rowY += 22;

            this.scaleBox = new EditBox(this.font, inputX, rowY, 50, 18, Component.literal(""));
            this.scaleBox.setValue(String.valueOf(scale));
            this.scaleBox.setFilter(s -> s.matches("\\d*\\.?\\d*"));
            this.addRenderableWidget(this.scaleBox);
            scaleBaseY = rowY;
            rowY += 22;

            // 保存设置（左对齐页面组件），删除在其下方
            this.saveBtn = new NoShadowButton(inputX, rowY, 80, 20,
                Component.literal("保存设置"), btn -> saveSettings());
            this.addRenderableWidget(this.saveBtn);
            saveBtnBaseY = rowY;
            rowY += 22;
            this.deleteBtn = new NoShadowButton(inputX, rowY, 80, 20,
                Component.literal("删除 NPC").withStyle(ChatFormatting.RED), btn -> pendingDeleteNpc = true);
            this.addRenderableWidget(this.deleteBtn);
            deleteBtnBaseY = rowY;

            applySettingsScroll();
        } else if (activeTab == 1) {
            // 子板块栏
            int subTabY = py + 34;
            int subTabW = Math.max(36, (PANEL_W - 130) / 4 - 2);
            for (int i = 0; i < com.kghua.npcai.data.TeleportPoint.CATEGORIES.length; i++) {
                final int idx = i;
                this.addRenderableWidget(new NoShadowButton(px + 10 + i * (subTabW + 2), subTabY, subTabW, 16,
                    Component.literal(com.kghua.npcai.data.TeleportPoint.CATEGORIES[i]), btn -> {
                        teleportCategoryIndex = idx;
                        teleportSearchText = "";
                        if (teleportSearchBox != null) teleportSearchBox.setValue("");
                    }));
            }
            this.addRenderableWidget(new NoShadowButton(px + PANEL_W - 110, subTabY, 100, 16,
                Component.literal("+ 添加传送点"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new AddTeleportPointScreen(entityId, null, this));
                    }
                }));
            // 搜索栏
            this.teleportSearchBox = new EditBox(this.font, px + 10, subTabY + 20, PANEL_W - 20, 16, Component.literal(""));
            this.teleportSearchBox.setMaxLength(32);
            this.teleportSearchBox.setHint(Component.literal("搜索传送点..."));
            this.teleportSearchBox.setResponder(text -> {
                this.teleportSearchText = text;
                this.scrollOffset = 0;
            });
            this.addRenderableWidget(this.teleportSearchBox);
        } else if (activeTab == 2) {
            ClientPlayNetworking.send(new RequestPlayerListPacket());
        } else if (activeTab == 3) {
            ClientPlayNetworking.send(new RequestQuestionnairesPacket());
            this.addRenderableWidget(new NoShadowButton(px + PANEL_W - 110, py + 34, 100, 18,
                Component.literal("+ 添加问卷"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new AddQuestionnaireScreen(this));
                    }
                }));
        } else if (activeTab == 4) {
            rebuildMailEditorWidgets();
        } else if (activeTab == 5) {
            rebuildCompensationEditorWidgets();
        } else if (activeTab == 6) {
            scrollOffset = 0;
            rebuildCerebellumEditorWidgets();
        } else if (activeTab == 7) {
            // 投稿设置：请求数据 + 子板块按钮（整组居中）
            ClientPlayNetworking.send(new RequestContributionsPacket());
            contributionSelectedPeriod = com.kghua.npcai.data.Contribution.getCurrentPeriod();
            // 小分区按键整排铺满面板宽度：左右与顶部板块边缘对齐，间距紧凑不分散
            int subY = py + 34;
            int gap = 6;
            int btnW = (PANEL_W - 16 - 3 * gap) / 4;
            int startX = px + 8;
            for (int i = 0; i < com.kghua.npcai.data.Contribution.TYPES.length; i++) {
                final int idx = i;
                this.addRenderableWidget(new NoShadowButton(startX + i * (btnW + gap), subY, btnW, 16,
                    Component.literal(com.kghua.npcai.data.Contribution.TYPES[i]), btn -> {
                        contributionSubTab = idx;
                        contributionScroll = 0;
                        contributionPeriodMenuOpen = false;
                        rebuildWidgets();
                    }));
            }
            // 投稿奖励子板块（第三个子分区）
            this.addRenderableWidget(new NoShadowButton(startX + 2 * (btnW + gap), subY, btnW, 16,
                Component.literal("投稿奖励"), btn -> {
                    contributionSubTab = 2;
                    contributionScroll = 0;
                    contributionPeriodMenuOpen = false;
                    rebuildWidgets();
                }));
            // 期数按钮（子板块右侧）
            this.addRenderableWidget(new NoShadowButton(startX + 3 * (btnW + gap), subY, btnW, 16,
                Component.literal("第" + contributionSelectedPeriod + "期"), btn -> {
                    contributionPeriodMenuOpen = !contributionPeriodMenuOpen;
                    contributionPeriodScroll = 0;
                }));
            if (contributionSubTab == 2) {
                rebuildContributionRewardWidgets();
            }
        } else if (activeTab == 8) {
            rebuildFeedbackWidgets(px, py);
        }
    }

    private String formatCoord(double v) {
        if (v == (long) v) return String.valueOf((long) v);
        return String.format("%.2f", v);
    }

    private String modeLabel() {
        return switch (followMode) {
            case FIXED -> "状态：固定位置";
            case RANDOM_WALK -> "状态：随机移动";
            case FOLLOW_RED_DOT -> "状态：追随红点";
        };
    }

    private void toggleMode() {
        followMode = switch (followMode) {
            case FIXED -> NpcData.FollowMode.RANDOM_WALK;
            case RANDOM_WALK -> NpcData.FollowMode.FOLLOW_RED_DOT;
            case FOLLOW_RED_DOT -> NpcData.FollowMode.FIXED;
        };
        if (modeButton != null) {
            modeButton.setMessage(Component.literal(modeLabel()));
        }
    }

    private String viewModeLabel() {
        return viewMode == NpcData.ViewMode.RANDOM ? "视角：随机" : "视角：跟随最近玩家";
    }

    private void toggleViewMode() {
        viewMode = viewMode == NpcData.ViewMode.RANDOM
            ? NpcData.ViewMode.FOLLOW_NEAREST_PLAYER
            : NpcData.ViewMode.RANDOM;
        if (viewModeButton != null) {
            viewModeButton.setMessage(Component.literal(viewModeLabel()));
        }
    }

    private void saveSettings() {
        try {
            double x = xBox.getValue().trim().isEmpty() ? npcX : Double.parseDouble(xBox.getValue().trim());
            double y = yBox.getValue().trim().isEmpty() ? npcY : Double.parseDouble(yBox.getValue().trim());
            double z = zBox.getValue().trim().isEmpty() ? npcZ : Double.parseDouble(zBox.getValue().trim());
            float newScale = scale;
            try {
                newScale = Float.parseFloat(scaleBox.getValue().trim());
                newScale = Math.max(0.2f, Math.min(2.0f, newScale));
            } catch (NumberFormatException ignored) {
            }
            // 活动范围（非固定模式）
            double rX = roamX, rY = roamY, rZ = roamZ, rRadius = roamRadius;
            if (roamXBox != null) {
                try { rX = Double.parseDouble(roamXBox.getValue().trim()); } catch (NumberFormatException ignored) {}
                try { rY = Double.parseDouble(roamYBox.getValue().trim()); } catch (NumberFormatException ignored) {}
                try { rZ = Double.parseDouble(roamZBox.getValue().trim()); } catch (NumberFormatException ignored) {}
                try {
                    rRadius = Double.parseDouble(roamRadiusBox.getValue().trim());
                    if (rRadius <= 0) rRadius = -1;
                } catch (NumberFormatException ignored) {
                    rRadius = -1;
                }
            }
            ClientPlayNetworking.send(new SaveNpcSettingsPacket(
                entityId,
                nameBox.getValue().trim(),
                skinBox.getValue().trim(),
                x, y, z,
                followMode.ordinal(),
                viewMode.ordinal(),
                newScale,
                heldItem,
                rX, rY, rZ, rRadius
            ));
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.literal("§a设置已保存"));
            }
        } catch (NumberFormatException e) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.literal("§c坐标格式错误"));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);

        // 标签页位于返回按钮右侧
        int tabX = px + 66;
        int tabW = Math.max(28, (PANEL_W - 132) / TAB_LABELS.length - 2);
        for (int i = 0; i < TAB_LABELS.length; i++) {
            drawTab(graphics, TAB_LABELS[i], tabX + i * (tabW + 2), py + 10, tabW, activeTab == i, mouseX, mouseY);
        }

        int contentTop = py + 58;
        int contentHeight = PANEL_H - 68;

        // 内容区结界：所有组件渲染裁剪在面板内，超出即消失（作用到所有标签页）
        // 顶部从板块栏下方（py+28）开始，不裁掉各页面顶部组件
        graphics.enableScissor(px + 4, py + 28, px + PANEL_W - 4, py + PANEL_H - 6);

        if (activeTab == 0) {
            // 标签跟随滚动
            int labelX = px + 20;
            int sTop = py + 48;
            int sBottom = py + PANEL_H - 10;
            drawScrollLabel(graphics, "显示名称", labelX, nameBaseY + 2, sTop, sBottom);
            drawScrollLabel(graphics, "皮肤账号", labelX, skinBaseY + 2, sTop, sBottom);
            drawScrollLabel(graphics, "坐标", labelX, coordBaseY + 2, sTop, sBottom);
            drawScrollLabel(graphics, "活动中心", labelX, roamBaseY + 2, sTop, sBottom);
            drawScrollLabel(graphics, "半径", labelX + 250, roamBaseY + 2, sTop, sBottom);
            drawScrollLabel(graphics, "移动", labelX, modeBaseY + 4, sTop, sBottom);
            drawScrollLabel(graphics, "视角", labelX, viewModeBaseY + 4, sTop, sBottom);
            drawScrollLabel(graphics, "模型大小", labelX, scaleBaseY + 2, sTop, sBottom);
        } else if (activeTab == 1) {
            // 子板块栏 + 搜索栏额外占 40px
            renderTeleportList(graphics, px + 10, contentTop + 40, PANEL_W - 20, contentHeight - 40, mouseX, mouseY);
        } else if (activeTab == 2) {
            renderPlayerList(graphics, px + 10, contentTop, PANEL_W - 20, contentHeight, mouseX, mouseY);
        } else if (activeTab == 3) {
            renderQuestionnaireList(graphics, px + 10, contentTop, PANEL_W - 20, contentHeight, mouseX, mouseY);
        } else if (activeTab == 4) {
            renderMailEditorTab(graphics, px, py);
        } else if (activeTab == 5) {
            renderCompensationTab(graphics, px, py);
        } else if (activeTab == 6) {
            renderCerebellumTab(graphics, px, py);
        } else if (activeTab == 7) {
            renderContributionTab(graphics, px, py, mouseX, mouseY);
        } else if (activeTab == 8) {
            renderFeedbackTab(graphics, px, py);
        }

        graphics.disableScissor();

        super.render(graphics, mouseX, mouseY, delta);

        // 删除确认弹窗必须在 super.render() 之后绘制，确保浮动在最上层
        if (pendingDeleteNpc) {
            renderDeleteConfirm(graphics, px + PANEL_W / 2 - 110, py + PANEL_H / 2 - 30, mouseX, mouseY, "确认删除该 NPC？", () -> {
                ClientPlayNetworking.send(new DeleteNpcPacket(entityId));
                pendingDeleteNpc = false;
                if (this.minecraft != null) {
                    this.minecraft.setScreen(null);
                }
            }, () -> pendingDeleteNpc = false);
        }
    }

    private void drawTab(GuiGraphics graphics, String label, int x, int y, int tabW, boolean active, int mouseX, int mouseY) {
        if (active) {
            TrainStyleRenderHelper.renderSelectedCard(graphics, x, y, tabW, 20);
        } else {
            TrainStyleRenderHelper.renderCard(graphics, x, y, tabW, 20);
        }
        int textColor = active ? 0xFFFFFF : 0x333333;
        int textW = this.font.width(label);
        int textX = x + (tabW - textW) / 2;
        graphics.drawString(this.font, Component.literal(label), textX, y + 6, textColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pendingDeleteTeleport != null || pendingDeleteQuestionnaire != null || pendingDeleteMail != null || pendingDeleteNpc) {
            int px = (this.width - PANEL_W) / 2;
            int py = (this.height - PANEL_H) / 2;
            // 所有删除确认弹窗统一用面板中心坐标（与渲染一致）
            handleDeleteConfirmClick(mouseX, mouseY, px + PANEL_W / 2 - 110, py + PANEL_H / 2 - 30);
            return true;
        }

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        if (button == 0) {
            int tabX = px + 66;
            // 标签条左右对称：左留边（返回按钮区）≈右留边，整体居中
            int tabW = Math.max(28, (PANEL_W - 132) / TAB_LABELS.length - 2);
            for (int i = 0; i < TAB_LABELS.length; i++) {
                if (isMouseOver(tabX + i * (tabW + 2), py + 10, tabW, 20, (int) mouseX, (int) mouseY)) {
                    activeTab = i;
                    scrollOffset = 0;
                    settingsScrollOffset = 0;
                    rebuildWidgets();
                    return true;
                }
            }

            if (activeTab == 1) {
                handleTeleportClick(mouseX, mouseY, px, py);
            } else if (activeTab == 2) {
                handlePlayerClick(mouseX, mouseY, px, py);
            } else if (activeTab == 3) {
                handleQuestionnaireClick(mouseX, mouseY, px, py);
            } else if (activeTab == 4) {
                handleMailClick(mouseX, mouseY, px, py);
            } else if (activeTab == 5) {
                handleCompensationClick(mouseX, mouseY, px, py);
            } else if (activeTab == 6) {
                handleCerebellumClick(mouseX, mouseY, px, py);
            } else if (activeTab == 7) {
                handleContributionClick(mouseX, mouseY, px, py);
            } else if (activeTab == 8) {
                handleFeedbackClick(mouseX, mouseY, px, py);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleTeleportClick(double mouseX, double mouseY, int px, int py) {
        String selectedCategory = com.kghua.npcai.data.TeleportPoint.CATEGORIES[teleportCategoryIndex];
        List<TeleportPoint> filtered = new ArrayList<>();
        for (TeleportPoint p : points) {
            String cat = p.category() != null ? p.category() : "其他";
            if (!cat.equals(selectedCategory)) continue;
            if (!teleportSearchText.isEmpty() && !p.name().toLowerCase().contains(teleportSearchText.toLowerCase())) continue;
            filtered.add(p);
        }

        int contentTop = py + 58 + 40; // 子板块栏+搜索栏额外偏移
        int contentX = px + 10;
        int contentW = PANEL_W - 20;
        int cardW = (contentW - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        int currentY = contentTop - (int) scrollOffset;
        for (int i = 0; i < filtered.size(); i++) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int cardX = contentX + col * (cardW + CARD_GAP);
            int cardY = currentY + row * (CARD_H + CARD_GAP);
            if (mouseY >= cardY && mouseY <= cardY + CARD_H
                && mouseX >= cardX && mouseX <= cardX + cardW) {
                int deleteX = cardX + cardW - 18;
                int deleteY = cardY + 4;
                if (isMouseOver(deleteX, deleteY, 14, 14, (int) mouseX, (int) mouseY)) {
                    pendingDeleteTeleport = filtered.get(i).name();
                } else if (this.minecraft != null) {
                    // 点击卡片主体进入编辑（返回时回到管理端）
                    this.minecraft.setScreen(new AddTeleportPointScreen(entityId, filtered.get(i), this));
                }
                return;
            }
        }
    }

    private void handlePlayerClick(double mouseX, double mouseY, int px, int py) {
        int contentTop = py + 58;
        int contentX = px + 10;
        int contentW = PANEL_W - 20;
        int rowH = 28;
        int currentY = contentTop - (int) scrollOffset;
        for (int i = 0; i < players.size(); i++) {
            int rowY = currentY + i * rowH;
            if (mouseY >= rowY && mouseY <= rowY + rowH && mouseX >= contentX + contentW - 50 && mouseX <= contentX + contentW - 50 + 40) {
                SyncPlayerListPacket.PlayerInfo p = players.get(i);
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new PlayerManagementScreen(p, this));
                }
                return;
            }
        }
    }

    private void handleQuestionnaireClick(double mouseX, double mouseY, int px, int py) {
        int contentTop = py + 58;
        int contentX = px + 10;
        int contentW = PANEL_W - 20;
        int cardW = (contentW - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        int currentY = contentTop - (int) scrollOffset;
        for (int i = 0; i < questionnaires.size(); i++) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int cardX = contentX + col * (cardW + CARD_GAP);
            int cardY = currentY + row * (CARD_H + CARD_GAP);
            if (mouseY >= cardY && mouseY <= cardY + CARD_H && mouseX >= cardX && mouseX <= cardX + cardW) {
                int delX = cardX + cardW - 18;
                int btnY = cardY + 4;
                if (isMouseOver(delX, btnY, 14, 14, (int) mouseX, (int) mouseY)) {
                    pendingDeleteQuestionnaire = questionnaires.get(i).getId();
                } else if (this.minecraft != null) {
                    this.minecraft.setScreen(new QuestionnaireDetailScreen(questionnaires.get(i), this));
                }
                return;
            }
        }
    }

    private void handleMailClick(double mouseX, double mouseY, int px, int py) {
        int contentTop = py + 58;
        int contentX = px + 10;
        int contentW = PANEL_W - 20;
        int cardW = (contentW - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        int currentY = contentTop - (int) scrollOffset;
        for (int i = 0; i < mails.size(); i++) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int cardX = contentX + col * (cardW + CARD_GAP);
            int cardY = currentY + row * (CARD_H + CARD_GAP);
            if (mouseY >= cardY && mouseY <= cardY + CARD_H && mouseX >= cardX && mouseX <= cardX + cardW) {
                int delX = cardX + cardW - 18;
                int btnY = cardY + 4;
                if (isMouseOver(delX, btnY, 14, 14, (int) mouseX, (int) mouseY)) {
                    pendingDeleteMail = mails.get(i).getId();
                } else if (this.minecraft != null) {
                    this.minecraft.setScreen(new MailDetailScreen(mails.get(i)));
                }
                return;
            }
        }
    }

    private void renderTeleportList(GuiGraphics graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        String selectedCategory = com.kghua.npcai.data.TeleportPoint.CATEGORIES[teleportCategoryIndex];
        // 过滤：分类 + 搜索
        List<TeleportPoint> filtered = new ArrayList<>();
        for (TeleportPoint p : points) {
            String cat = p.category() != null ? p.category() : "其他";
            if (!cat.equals(selectedCategory)) continue;
            if (!teleportSearchText.isEmpty()) {
                String lower = p.name().toLowerCase();
                if (!lower.contains(teleportSearchText.toLowerCase())) continue;
            }
            filtered.add(p);
        }

        int cardW = (w - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        int totalHeight = gridRows(filtered.size()) * (CARD_H + CARD_GAP);
        clampScroll(totalHeight, h);

        int currentY = y - (int) scrollOffset;
        for (int i = 0; i < filtered.size(); i++) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int cardX = x + col * (cardW + CARD_GAP);
            int cardY = currentY + row * (CARD_H + CARD_GAP);
            if (cardY + CARD_H < y || cardY > y + h) continue;
            TeleportPoint p = filtered.get(i);
            TrainStyleRenderHelper.renderCard(graphics, cardX, cardY, cardW, CARD_H);

            // 名称带搜索高亮
            String name = p.name();
            if (!teleportSearchText.isEmpty()) {
                drawHighlightedText(graphics, name, cardX + 6, cardY + 6, 0x333333, 0xFF55FF55, teleportSearchText);
            } else {
                if (this.font.width(name) > cardW - 24) {
                    name = this.font.plainSubstrByWidth(name, cardW - 24) + "...";
                }
                graphics.drawString(this.font, Component.literal(name), cardX + 6, cardY + 6, 0x333333, false);
            }
            String coord = String.format("%.0f, %.0f, %.0f", p.x(), p.y(), p.z());
            graphics.drawString(this.font, Component.literal(coord), cardX + 6, cardY + 20, 0x666666, false);

            int btnX = cardX + cardW - 18;
            int btnY = cardY + 4;
            TrainStyleRenderHelper.renderInlineButton(graphics, this.font, "×", btnX, btnY, 14, 14,
                true, isMouseOver(btnX, btnY, 14, 14, mouseX, mouseY));
        }

        if (pendingDeleteTeleport != null) {
            // 弹窗统一用面板中心坐标（与点击检测一致）
            int px = (this.width - PANEL_W) / 2;
            int py = (this.height - PANEL_H) / 2;
            renderDeleteConfirm(graphics, px + PANEL_W / 2 - 110, py + PANEL_H / 2 - 30, mouseX, mouseY, "确认删除传送点？", () -> {
                ClientPlayNetworking.send(new RemoveTeleportPointPacket(entityId, pendingDeleteTeleport));
                pendingDeleteTeleport = null;
            }, () -> pendingDeleteTeleport = null);
        }
    }

    private void drawHighlightedText(GuiGraphics graphics, String text, int x, int y, int normalColor, int highlightColor, String searchTerm) {
        if (searchTerm.isEmpty()) {
            graphics.drawString(this.font, Component.literal(text), x, y, normalColor, false);
            return;
        }
        String lower = text.toLowerCase();
        String lowerSearch = searchTerm.toLowerCase();
        int idx = lower.indexOf(lowerSearch);
        if (idx < 0) {
            graphics.drawString(this.font, Component.literal(text), x, y, normalColor, false);
            return;
        }
        String before = text.substring(0, idx);
        int curX = x;
        if (!before.isEmpty()) {
            graphics.drawString(this.font, Component.literal(before), curX, y, normalColor, false);
            curX += this.font.width(before);
        }
        String keyword = text.substring(idx, idx + searchTerm.length());
        graphics.drawString(this.font, Component.literal(keyword), curX, y, highlightColor, false);
        curX += this.font.width(keyword);
        String after = text.substring(idx + searchTerm.length());
        if (!after.isEmpty()) {
            graphics.drawString(this.font, Component.literal(after), curX, y, normalColor, false);
        }
    }

    private void renderPlayerList(GuiGraphics graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        int rowH = 28;
        int totalHeight = players.size() * rowH;
        clampScroll(totalHeight, h);

        int currentY = y - (int) scrollOffset;
        for (int i = 0; i < players.size(); i++) {
            SyncPlayerListPacket.PlayerInfo p = players.get(i);
            int rowY = currentY + i * rowH;
            if (rowY + rowH < y || rowY > y + h) continue;
            TrainStyleRenderHelper.renderCard(graphics, x, rowY, w, rowH);

            ResourceLocation skin = getPlayerSkinTexture(p);
            PlayerFaceRenderer.draw(graphics, skin, x + 6, rowY + 4, 20);

            int nameX = x + 32;
            int textY = rowY + (rowH - 8) / 2;
            int badgeY = textY - 8;
            int currentX = nameX;

            int teamColor = parseColor(p.teamColor());
            int playerColor = parseColor(p.playerColor());

            if (!p.teamName().isEmpty()) {
                String prefix = "[" + p.teamName() + "] ";
                graphics.drawString(this.font, Component.literal(prefix), currentX, textY - 6, teamColor, false);
                currentX += this.font.width(prefix);
            }
            graphics.drawString(this.font, Component.literal(p.name()), currentX, textY - 6, playerColor, false);
            currentX += this.font.width(p.name()) + 6;

            if (p.npcAdmin()) {
                drawBadge(graphics, "N", currentX, badgeY, 0xFFFF8800, 0xFFCC6600);
                currentX += 18;
            }
            if (p.op()) {
                drawBadge(graphics, "管", currentX, badgeY, 0xFFFFFF00, 0xFFFFCC00);
                currentX += 18;
            }
            if (p.mapGroup()) {
                drawBadge(graphics, "地", currentX, badgeY, 0xFF55FF55, 0xFF00AA00);
            }

            int btnX = x + w - 50;
            int btnY = rowY + 5;
            TrainStyleRenderHelper.renderInlineButton(graphics, this.font, "设置", btnX, btnY, 40, 18,
                true, isMouseOver(btnX, btnY, 40, 18, mouseX, mouseY));
        }
    }

    private void drawBadge(GuiGraphics graphics, String text, int x, int y, int bgColor, int borderColor) {
        graphics.fill(x, y, x + 14, y + 14, bgColor);
        graphics.renderOutline(x, y, 14, 14, borderColor);
        int textW = this.font.width(text);
        graphics.drawString(this.font, Component.literal(text), x + (14 - textW) / 2, y + 3, 0xFF333333, false);
    }

    private void renderQuestionnaireList(GuiGraphics graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        int cardW = (w - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        int totalHeight = gridRows(questionnaires.size()) * (CARD_H + CARD_GAP);
        clampScroll(totalHeight, h);

        int currentY = y - (int) scrollOffset;
        for (int i = 0; i < questionnaires.size(); i++) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int cardX = x + col * (cardW + CARD_GAP);
            int cardY = currentY + row * (CARD_H + CARD_GAP);
            if (cardY + CARD_H < y || cardY > y + h) continue;
            Questionnaire q = questionnaires.get(i);
            TrainStyleRenderHelper.renderCard(graphics, cardX, cardY, cardW, CARD_H);
            String title = q.getTitle();
            if (this.font.width(title) > cardW - 44) {
                title = this.font.plainSubstrByWidth(title, cardW - 44) + "...";
            }
            graphics.drawString(this.font, Component.literal(title), cardX + 6, cardY + 6, 0x333333, false);
            String sub = formatQuestionnaireTime(q.getCreatedAt()) + " | " + q.getQuestions().size() + " 题";
            graphics.drawString(this.font, Component.literal(sub), cardX + 6, cardY + 20, 0x666666, false);

            int delX = cardX + cardW - 18;
            int btnY = cardY + 4;
            TrainStyleRenderHelper.renderInlineButton(graphics, this.font, "×", delX, btnY, 14, 14,
                true, isMouseOver(delX, btnY, 14, 14, mouseX, mouseY));
        }

        if (pendingDeleteQuestionnaire != null) {
            int px = (this.width - PANEL_W) / 2;
            int py = (this.height - PANEL_H) / 2;
            renderDeleteConfirm(graphics, px + PANEL_W / 2 - 110, py + PANEL_H / 2 - 30, mouseX, mouseY, "确认删除问卷？", () -> {
                ClientPlayNetworking.send(new DeleteQuestionnairePacket(pendingDeleteQuestionnaire));
                pendingDeleteQuestionnaire = null;
            }, () -> pendingDeleteQuestionnaire = null);
        }
    }

    private void renderMailList(GuiGraphics graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        int cardW = (w - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        int totalHeight = gridRows(mails.size()) * (CARD_H + CARD_GAP);
        clampScroll(totalHeight, h);

        int currentY = y - (int) scrollOffset;
        for (int i = 0; i < mails.size(); i++) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int cardX = x + col * (cardW + CARD_GAP);
            int cardY = currentY + row * (CARD_H + CARD_GAP);
            if (cardY + CARD_H < y || cardY > y + h) continue;
            MailRecord m = mails.get(i);
            TrainStyleRenderHelper.renderCard(graphics, cardX, cardY, cardW, CARD_H);
            String title = m.getTitle();
            if (this.font.width(title) > cardW - 12) {
                title = this.font.plainSubstrByWidth(title, cardW - 12) + "...";
            }
            graphics.drawString(this.font, Component.literal(title), cardX + 6, cardY + 6, 0x333333, false);
            String mode = switch (m.getSendMode()) {
                case 0 -> "全部";
                case 1 -> "白名单";
                case 2 -> "黑名单";
                default -> "全部";
            };
            graphics.drawString(this.font, Component.literal(mode), cardX + 6, cardY + 20, 0x666666, false);

            int delX = cardX + cardW - 18;
            int btnY = cardY + 4;
            TrainStyleRenderHelper.renderInlineButton(graphics, this.font, "×", delX, btnY, 14, 14,
                true, isMouseOver(delX, btnY, 14, 14, mouseX, mouseY));
        }

        if (pendingDeleteMail != null) {
            int px = (this.width - PANEL_W) / 2;
            int py = (this.height - PANEL_H) / 2;
            renderDeleteConfirm(graphics, px + PANEL_W / 2 - 110, py + PANEL_H / 2 - 30, mouseX, mouseY, "确认删除邮件？", () -> {
                ClientPlayNetworking.send(new DeleteMailPacket(pendingDeleteMail));
                pendingDeleteMail = null;
            }, () -> pendingDeleteMail = null);
        }
    }

    private void renderDeleteConfirm(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, String title, Runnable onConfirm, Runnable onCancel) {
        TrainStyleRenderHelper.renderPanel(graphics, x, y, 220, 60);
        graphics.drawString(this.font, Component.literal(title), x + 10, y + 10, 0xFFFFFFFF, false);

        int my = y + 32;
        int confirmX = x + 40;
        int cancelX = x + 120;
        TrainStyleRenderHelper.renderInlineButton(graphics, this.font, "确认", confirmX, my, 50, 20,
            true, isMouseOver(confirmX, my, 50, 20, mouseX, mouseY));
        TrainStyleRenderHelper.renderInlineButton(graphics, this.font, "取消", cancelX, my, 50, 20,
            true, isMouseOver(cancelX, my, 50, 20, mouseX, mouseY));

        if (deleteConfirmClicked) {
            deleteConfirmClicked = false;
            if (pendingDeleteConfirm) {
                pendingDeleteConfirm = false;
                onConfirm.run();
            } else {
                onCancel.run();
            }
        }
    }

    private boolean deleteConfirmClicked = false;
    private boolean pendingDeleteConfirm = false;

    private void handleDeleteConfirmClick(double mouseX, double mouseY, int x, int y) {
        int my = y + 32;
        int confirmX = x + 40;
        int cancelX = x + 120;
        if (mouseY >= my && mouseY <= my + 20) {
            if (mouseX >= confirmX && mouseX <= confirmX + 50) {
                deleteConfirmClicked = true;
                pendingDeleteConfirm = true;
            } else if (mouseX >= cancelX && mouseX <= cancelX + 50) {
                deleteConfirmClicked = true;
                pendingDeleteConfirm = false;
            }
        }
    }

    // ================== 补偿设置内嵌标签 ==================

    private void renderCompensationTab(GuiGraphics graphics, int px, int py) {
        if (editingCompensationRule == null) {
            renderCompensationRuleList(graphics, px + 10, py + 58, PANEL_W - 20, PANEL_H - 68);
        } else {
            int scrollTop = py + 52;
            int scrollBottom = py + PANEL_H - 28;
            drawScrollLabel(graphics, "标题", px + 10, compensationTitleBaseY + 4, scrollTop, scrollBottom);
            drawScrollLabel(graphics, "死亡原因", px + 10, compensationReasonBaseY + 4, scrollTop, scrollBottom);
            drawScrollLabel(graphics, "需要死亡次数", px + 10, compensationRequiredDeathsBaseY + 4, scrollTop, scrollBottom);
        }
    }

    private void drawScrollLabel(GuiGraphics graphics, String text, int x, int baseY, int scrollTop, int scrollBottom) {
        int y = baseY - (int) scrollOffset;
        if (y + 8 > scrollTop && y < scrollBottom) {
            graphics.drawString(this.font, Component.literal(text), x, y, 0xFFFFFFFF, false);
        }
    }

    private void renderCompensationRuleList(GuiGraphics graphics, int x, int y, int w, int h) {
        int totalHeight = compensationRules.size() * (CARD_H + CARD_GAP) + 10;
        clampScroll(totalHeight, h);

        int currentY = y - (int) scrollOffset;
        for (int i = 0; i < compensationRules.size(); i++) {
            CompensationRule rule = compensationRules.get(i);
            if (currentY + CARD_H >= y && currentY <= y + h) {
                TrainStyleRenderHelper.renderCard(graphics, x, currentY, w, CARD_H);
                String title = rule.getTitle().isEmpty() ? "未命名规则" : rule.getTitle();
                if (this.font.width(title) > w - 12) {
                    title = this.font.plainSubstrByWidth(title, w - 12) + "...";
                }
                graphics.drawString(this.font, Component.literal(title), x + 6, currentY + 6, 0x333333, false);
                String reason = CompensationSettingsScreen.translateDeathReason(rule.getDeathReason());
                graphics.drawString(this.font, Component.literal(reason + " / 需 " + rule.getRequiredDeaths() + " 次"),
                    x + 6, currentY + 22, 0x666666, false);
            }
            currentY += CARD_H + CARD_GAP;
        }
    }

    private void handleCompensationClick(double mouseX, double mouseY, int px, int py) {
        if (editingCompensationRule == null) {
            int contentTop = py + 58;
            int contentX = px + 10;
            int contentW = PANEL_W - 20;
            int currentY = contentTop - (int) scrollOffset;
            for (int i = 0; i < compensationRules.size(); i++) {
                if (mouseY >= currentY && mouseY <= currentY + CARD_H
                    && mouseX >= contentX && mouseX <= contentX + contentW) {
                    startEditCompensationRule(compensationRules.get(i));
                    return;
                }
                currentY += CARD_H + CARD_GAP;
            }
        }
    }

    private void startAddCompensationRule() {
        this.editingCompensationRule = new CompensationRule(UUID.randomUUID());
        this.editingCompensationRule.setTitle("");
        this.editingCompensationRule.setDeathReason(CompensationSettingsScreen.DEATH_REASONS[0]);
        this.editingCompensationRule.setRequiredDeaths(1);
        this.compensationIsAdding = true;
        this.scrollOffset = 0;
        rebuildCompensationEditorWidgets();
    }

    private void startEditCompensationRule(CompensationRule rule) {
        this.editingCompensationRule = rule;
        this.compensationIsAdding = false;
        this.scrollOffset = 0;
        rebuildCompensationEditorWidgets();
    }

    private void closeCompensationEditor() {
        this.editingCompensationRule = null;
        this.compensationIsAdding = false;
        this.scrollOffset = 0;
        rebuildWidgets();
    }

    private void rebuildCompensationEditorWidgets() {
        if (activeTab != 5) return;
        clearWidgets();
        compensationCommandNameBoxes.clear();
        compensationCommandCmdBoxes.clear();
        compensationCommandDeleteButtons.clear();
        compensationCommandNameBaseYs.clear();
        compensationCommandCmdBaseYs.clear();
        compensationCommandDeleteBaseYs.clear();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        if (editingCompensationRule == null) {
            this.addRenderableWidget(new NoShadowButton(px + PANEL_W - 110, py + 34, 100, 18,
                Component.literal("+ 添加补偿机制"), btn -> startAddCompensationRule()));
            return;
        }

        this.compensationTitleBox = new EditBox(this.font, px + 90, py + 52, 200, 16, Component.literal(""));
        this.compensationTitleBox.setMaxLength(64);
        this.compensationTitleBox.setValue(editingCompensationRule.getTitle());
        this.addRenderableWidget(this.compensationTitleBox);
        this.compensationTitleBaseY = this.compensationTitleBox.getY();

        this.compensationReasonButton = new NoShadowButton(px + 90, py + 72, 200, 18,
            Component.literal(CompensationSettingsScreen.translateDeathReason(editingCompensationRule.getDeathReason())), btn -> cycleCompensationReason());
        this.addRenderableWidget(this.compensationReasonButton);
        this.compensationReasonBaseY = this.compensationReasonButton.getY();

        this.compensationRequiredDeathsBox = new EditBox(this.font, px + 140, py + 94, 50, 16, Component.literal(""));
        this.compensationRequiredDeathsBox.setMaxLength(4);
        this.compensationRequiredDeathsBox.setValue(String.valueOf(editingCompensationRule.getRequiredDeaths()));
        this.compensationRequiredDeathsBox.setFilter(s -> s.matches("\\d*"));
        this.addRenderableWidget(this.compensationRequiredDeathsBox);
        this.compensationRequiredDeathsBaseY = this.compensationRequiredDeathsBox.getY();

        int cmdY = py + 116;
        List<CompensationRule.CommandEntry> entries = editingCompensationRule.getCommands();
        for (int i = 0; i < entries.size(); i++) {
            CompensationRule.CommandEntry entry = entries.get(i);
            EditBox nameBox = new EditBox(this.font, px + 10, cmdY, 90, 16, Component.literal(""));
            nameBox.setMaxLength(64);
            nameBox.setValue(entry.name);
            nameBox.setHint(Component.literal("名称"));
            this.compensationCommandNameBoxes.add(nameBox);
            this.addRenderableWidget(nameBox);
            this.compensationCommandNameBaseYs.add(nameBox.getY());

            EditBox cmdBox = new EditBox(this.font, px + 104, cmdY, 230, 16, Component.literal(""));
            cmdBox.setMaxLength(256);
            cmdBox.setValue(entry.command);
            cmdBox.setHint(Component.literal("指令，可用 @p 指代玩家"));
            this.compensationCommandCmdBoxes.add(cmdBox);
            this.addRenderableWidget(cmdBox);
            this.compensationCommandCmdBaseYs.add(cmdBox.getY());

            final int index = i;
            Button delBtn = new NoShadowButton(px + 408, cmdY, 18, 16,
                Component.literal("×"), btn -> deleteCompensationCommand(index));
            this.compensationCommandDeleteButtons.add(delBtn);
            this.addRenderableWidget(delBtn);
            this.compensationCommandDeleteBaseYs.add(delBtn.getY());

            cmdY += 22;
        }

        this.compensationAddCommandButton = new NoShadowButton(px + 10, cmdY, 80, 18,
            Component.literal("+ 添加补偿"), btn -> addCompensationCommand());
        this.addRenderableWidget(this.compensationAddCommandButton);
        this.compensationAddCommandBaseY = this.compensationAddCommandButton.getY();

        int bottomY = py + PANEL_H - 28;
        this.addRenderableWidget(new NoShadowButton(px + 60, bottomY, 60, 20,
            Component.literal("保存"), btn -> saveCompensationRule()));
        this.addRenderableWidget(new NoShadowButton(px + 130, bottomY, 60, 20,
            Component.literal("取消"), btn -> closeCompensationEditor()));
        if (!compensationIsAdding) {
            this.addRenderableWidget(new NoShadowButton(px + 200, bottomY, 60, 20,
                Component.literal("删除"), btn -> deleteCompensationRule()));
        }

        applyCompensationEditorScroll();
    }

    private void applyCompensationEditorScroll() {
        if (editingCompensationRule == null || compensationTitleBox == null) return;
        int py = (this.height - PANEL_H) / 2;
        int scrollTop = py + 52;
        int scrollBottom = py + PANEL_H - 28;
        int visibleHeight = scrollBottom - scrollTop;
        int totalHeight = Math.max(0, compensationAddCommandBaseY + 18 - scrollTop);

        if (totalHeight <= visibleHeight) {
            scrollOffset = 0;
        } else {
            scrollOffset = Math.max(0, Math.min(scrollOffset, totalHeight - visibleHeight));
        }

        setWidgetScroll(compensationTitleBox, compensationTitleBaseY, scrollTop, scrollBottom);
        setWidgetScroll(compensationReasonButton, compensationReasonBaseY, scrollTop, scrollBottom);
        setWidgetScroll(compensationRequiredDeathsBox, compensationRequiredDeathsBaseY, scrollTop, scrollBottom);
        for (int i = 0; i < compensationCommandNameBoxes.size(); i++) {
            setWidgetScroll(compensationCommandNameBoxes.get(i), compensationCommandNameBaseYs.get(i), scrollTop, scrollBottom);
            setWidgetScroll(compensationCommandCmdBoxes.get(i), compensationCommandCmdBaseYs.get(i), scrollTop, scrollBottom);
            setWidgetScroll(compensationCommandDeleteButtons.get(i), compensationCommandDeleteBaseYs.get(i), scrollTop, scrollBottom);
        }
        if (this.compensationAddCommandButton != null) {
            setWidgetScroll(this.compensationAddCommandButton, compensationAddCommandBaseY, scrollTop, scrollBottom);
        }
    }

    private void setWidgetScroll(AbstractWidget widget, int baseY, int scrollTop, int scrollBottom) {
        int y = baseY - (int) scrollOffset;
        widget.setY(y);
        widget.visible = y + widget.getHeight() > scrollTop && y < scrollBottom;
    }

    private void cycleCompensationReason() {
        if (editingCompensationRule == null) return;
        String current = editingCompensationRule.getDeathReason();
        int index = 0;
        for (int i = 0; i < CompensationSettingsScreen.DEATH_REASONS.length; i++) {
            if (CompensationSettingsScreen.DEATH_REASONS[i].equals(current)) {
                index = i;
                break;
            }
        }
        editingCompensationRule.setDeathReason(CompensationSettingsScreen.DEATH_REASONS[(index + 1) % CompensationSettingsScreen.DEATH_REASONS.length]);
        if (compensationReasonButton != null) {
            compensationReasonButton.setMessage(Component.literal(CompensationSettingsScreen.translateDeathReason(editingCompensationRule.getDeathReason())));
        }
    }

    private void addCompensationCommand() {
        if (editingCompensationRule == null) return;
        editingCompensationRule.getCommands().add(new CompensationRule.CommandEntry());
        rebuildCompensationEditorWidgets();
    }

    private void deleteCompensationCommand(int index) {
        if (editingCompensationRule == null || index < 0 || index >= editingCompensationRule.getCommands().size()) return;
        editingCompensationRule.getCommands().remove(index);
        rebuildCompensationEditorWidgets();
    }

    private void saveCompensationRule() {
        if (editingCompensationRule == null) return;
        editingCompensationRule.setTitle(compensationTitleBox.getValue().trim());
        try {
            editingCompensationRule.setRequiredDeaths(Integer.parseInt(compensationRequiredDeathsBox.getValue().trim()));
        } catch (NumberFormatException ignored) {
            editingCompensationRule.setRequiredDeaths(1);
        }

        List<CompensationRule.CommandEntry> entries = editingCompensationRule.getCommands();
        for (int i = 0; i < entries.size(); i++) {
            CompensationRule.CommandEntry entry = entries.get(i);
            entry.name = compensationCommandNameBoxes.get(i).getValue().trim();
            entry.command = compensationCommandCmdBoxes.get(i).getValue().trim();
        }

        if (compensationIsAdding) {
            compensationRules.add(editingCompensationRule);
        } else {
            for (int i = 0; i < compensationRules.size(); i++) {
                if (compensationRules.get(i).getId().equals(editingCompensationRule.getId())) {
                    compensationRules.set(i, editingCompensationRule);
                    break;
                }
            }
        }

        ClientPlayNetworking.send(new SaveCompensationRulesPacket(new ArrayList<>(compensationRules)));
        ClientCache.setCompensationRules(new ArrayList<>(compensationRules));
        closeCompensationEditor();
    }

    private void deleteCompensationRule() {
        if (editingCompensationRule == null) return;
        compensationRules.removeIf(r -> r.getId().equals(editingCompensationRule.getId()));
        ClientPlayNetworking.send(new SaveCompensationRulesPacket(new ArrayList<>(compensationRules)));
        ClientCache.setCompensationRules(new ArrayList<>(compensationRules));
        closeCompensationEditor();
    }

    // ================== 发布邮箱内嵌标签 ==================

    private void rebuildMailEditorWidgets() {
        if (activeTab != 4) return;
        // 重建前保存当前编辑内容（打开选择弹窗/返回/切换标签时内容不丢失）
        captureMailEditorIntoFields();
        clearWidgets();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int fieldX = px + 90;
        int fieldW = PANEL_W - 110; // 留一点边距
        int rowH = 20;
        int y = py + 34;

        this.mailTitleBox = new EditBox(this.font, fieldX, y, fieldW, 16, Component.literal(""));
        this.mailTitleBox.setMaxLength(64);
        this.mailTitleBox.setValue(mailEditorTitle);
        this.addRenderableWidget(this.mailTitleBox);
        y += rowH;

        this.mailContentBox = new EditBox(this.font, fieldX, y, fieldW, 16, Component.literal(""));
        this.mailContentBox.setMaxLength(256);
        this.mailContentBox.setValue(mailEditorContent);
        this.addRenderableWidget(this.mailContentBox);
        y += rowH;

        // 4种身份卡数量输入框（与投稿奖励一致：杀手/平民/独赢中立/杀手中立）
        for (int i = 0; i < 4; i++) {
            this.mailCardBoxes[i] = new EditBox(this.font, fieldX, y, 60, 16, Component.literal(""));
            this.mailCardBoxes[i].setMaxLength(4);
            this.mailCardBoxes[i].setValue(mailEditorCards[i] == null ? "0" : mailEditorCards[i]);
            this.mailCardBoxes[i].setFilter(s -> s.matches("\\d*"));
            this.addRenderableWidget(this.mailCardBoxes[i]);
            y += rowH;
        }

        this.mailLotteryBox = new EditBox(this.font, fieldX, y, 60, 16, Component.literal(""));
        this.mailLotteryBox.setMaxLength(4);
        this.mailLotteryBox.setValue(mailEditorLottery.isEmpty() ? "0" : mailEditorLottery);
        this.mailLotteryBox.setFilter(s -> s.matches("\\d*"));
        this.addRenderableWidget(this.mailLotteryBox);
        y += rowH;

        this.mailModeButton = new NoShadowButton(fieldX, y, 100, 18,
            Component.literal(mailModeLabel()), btn -> toggleMailSendMode());
        this.addRenderableWidget(this.mailModeButton);
        // 发送模式为“绑定问卷”时，右侧出现选择问卷按键
        this.mailChooseButton = new NoShadowButton(fieldX + 112, y, fieldW - 112, 18,
            Component.literal(""), btn -> openMailBindScreen());
        this.mailChooseButton.visible = mailSendMode == 3;
        this.addRenderableWidget(this.mailChooseButton);
        refreshMailChooseButton();
        y += rowH;

        this.mailNamesBox = new EditBox(this.font, fieldX, y, fieldW, 16, Component.literal(""));
        this.mailNamesBox.setMaxLength(256);
        this.mailNamesBox.setValue(mailEditorNames);
        this.mailNamesBox.setHint(Component.literal("逗号分隔玩家名"));
        this.addRenderableWidget(this.mailNamesBox);
        y += rowH;

        LocalDateTime now = LocalDateTime.now();
        this.mailStartBox = new EditBox(this.font, fieldX, y, 100, 16, Component.literal(""));
        this.mailStartBox.setValue(mailEditorStart.isEmpty() ? now.format(MAIL_TIME_FORMATTER) : mailEditorStart);
        this.addRenderableWidget(this.mailStartBox);

        this.mailEndBox = new EditBox(this.font, fieldX + 120, y, 100, 16, Component.literal(""));
        this.mailEndBox.setValue(mailEditorEnd.isEmpty() ? now.plusDays(7).format(MAIL_TIME_FORMATTER) : mailEditorEnd);
        this.addRenderableWidget(this.mailEndBox);
        y += rowH + 4;

        this.addRenderableWidget(new NoShadowButton(fieldX, y, 70, 20,
            Component.literal("发布"), btn -> submitMail()));
        this.addRenderableWidget(new NoShadowButton(fieldX + 80, y, 70, 20,
            Component.literal("清空"), btn -> resetMailEditor()));
    }

    /** 重建前把编辑区内容存入字段，保证弹窗往返/切换标签后内容不丢失 */
    private void captureMailEditorIntoFields() {
        if (mailTitleBox != null) mailEditorTitle = mailTitleBox.getValue();
        if (mailContentBox != null) mailEditorContent = mailContentBox.getValue();
        for (int i = 0; i < 4; i++) {
            if (mailCardBoxes[i] != null) mailEditorCards[i] = mailCardBoxes[i].getValue();
        }
        if (mailLotteryBox != null) mailEditorLottery = mailLotteryBox.getValue();
        if (mailNamesBox != null) mailEditorNames = mailNamesBox.getValue();
        if (mailStartBox != null) mailEditorStart = mailStartBox.getValue();
        if (mailEndBox != null) mailEditorEnd = mailEndBox.getValue();
    }

    /** 从4个身份卡数量输入框读取数值（解析失败按0） */
    private int[] readMailEditorCards() {
        int[] cards = new int[4];
        for (int i = 0; i < 4; i++) {
            if (mailCardBoxes[i] == null) continue;
            try {
                cards[i] = Math.max(0, Integer.parseInt(mailCardBoxes[i].getValue().trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return cards;
    }

    private void renderMailEditorTab(GuiGraphics graphics, int px, int py) {
        int labelX = px + 10;
        int fieldX = px + 90;
        int rowH = 20;
        int y = py + 34;
        graphics.drawString(this.font, Component.literal("标题"), labelX, y + 4, 0xFFFFFFFF, false);
        y += rowH;
        graphics.drawString(this.font, Component.literal("内容"), labelX, y + 4, 0xFFFFFFFF, false);
        for (int i = 0; i < 4; i++) {
            y += rowH;
            String label = com.kghua.npcai.data.ContributionRewardSettings.CARD_LABELS[i];
            graphics.drawString(this.font, Component.literal(label), labelX, y + 4, 0xFFFFFFFF, false);
            graphics.drawString(this.font, Component.literal("张"), labelX + 62, y + 4, 0xFFAAAAAA, false);
        }
        y += rowH;
        graphics.drawString(this.font, Component.literal("奖励抽奖次数"), labelX, y + 4, 0xFFFFFFFF, false);
        y += rowH;
        graphics.drawString(this.font, Component.literal("发送模式"), labelX, y + 4, 0xFFFFFFFF, false);
        y += rowH;
        graphics.drawString(this.font, Component.literal("玩家名单"), labelX, y + 4, 0xFFFFFFFF, false);
        y += rowH;
        graphics.drawString(this.font, Component.literal("开始"), labelX, y + 4, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("结束"), fieldX + 100 + 6, y + 4, 0xFFFFFFFF, false);
    }

    private void toggleMailSendMode() {
        mailSendMode = (mailSendMode + 1) % 4;
        if (mailModeButton != null) {
            mailModeButton.setMessage(Component.literal(mailModeLabel()));
        }
        refreshMailChooseButton();
    }

    private String mailModeLabel() {
        return switch (mailSendMode) {
            case 0 -> "发送：全部";
            case 1 -> "发送：白名单";
            case 2 -> "发送：黑名单";
            case 3 -> "发送：绑定问卷";
            default -> "发送：全部";
        };
    }

    private void submitMail() {
        if (mailTitleBox == null || mailContentBox == null) return;

        // 绑定问卷模式：不直接发送，仅保存绑定（玩家提交问卷后自动发送）
        if (mailSendMode == 3) {
            if (boundMailQuestionnaireId.isEmpty()) {
                sendHint("§c请先点击“选择问卷”选择一个问卷，才能发布");
                return;
            }
            ClientPlayNetworking.send(captureMailBindSnapshot(boundMailQuestionnaireId));
            sendHint("§a已绑定问卷《" + boundMailQuestionnaireTitle + "》：玩家提交问卷后自动发送邮件");
            resetMailEditor();
            return;
        }

        String title = mailTitleBox.getValue().trim();
        String content = mailContentBox.getValue().trim();
        if (title.isEmpty() || content.isEmpty()) {
            sendHint("§c标题和内容不能为空");
            return;
        }

        int[] cards = readMailEditorCards();

        int lotteryCount;
        try {
            lotteryCount = Integer.parseInt(mailLotteryBox.getValue().trim());
            lotteryCount = Math.max(0, lotteryCount);
        } catch (NumberFormatException e) {
            lotteryCount = 0;
        }

        List<String> names = new ArrayList<>();
        if (mailSendMode != 0) {
            String namesText = mailNamesBox.getValue().trim();
            if (!namesText.isEmpty()) {
                names.addAll(Arrays.asList(namesText.split(",")));
                names.replaceAll(String::trim);
            }
        }

        long startAt = parseMailTime(mailStartBox.getValue().trim());
        long endAt = parseMailTime(mailEndBox.getValue().trim());
        long expiresAt = endAt > startAt ? endAt : 0;

        ClientPlayNetworking.send(new SendMailPacket(title, content, cards, mailSendMode, names, startAt, expiresAt, lotteryCount));
        sendHint("§a邮件已发布");
        resetMailEditor();
    }

    private long parseMailTime(String text) {
        try {
            return LocalDateTime.parse(text, MAIL_TIME_FORMATTER).toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private void resetMailEditor() {
        // 先清空输入框再重建，避免重建前捕获字段把旧值带回来
        if (mailTitleBox != null) mailTitleBox.setValue("");
        if (mailContentBox != null) mailContentBox.setValue("");
        if (mailLotteryBox != null) mailLotteryBox.setValue("0");
        if (mailNamesBox != null) mailNamesBox.setValue("");
        if (mailStartBox != null) mailStartBox.setValue("");
        if (mailEndBox != null) mailEndBox.setValue("");
        mailSendMode = 0;
        mailEditorTitle = "";
        mailEditorContent = "";
        mailEditorCards = new String[]{"0", "0", "0", "0"};
        mailEditorLottery = "0";
        mailEditorNames = "";
        mailEditorStart = "";
        mailEditorEnd = "";
        rebuildWidgets();
    }

    // ================== 反馈设置内嵌标签 ==================

    private void handleFeedbackClick(double mouseX, double mouseY, int px, int py) {
        int contentTop = py + 92;
        int contentX = px + 10;
        int contentW = PANEL_W - 20;
        int contentH = PANEL_H - 102;
        int currentY = contentTop - (int) feedbackScrollOffset;
        for (int i = 0; i < feedbackEntries.size(); i++) {
            FeedbackEntry e = feedbackEntries.get(i);
            int cardY = currentY + i * (FEEDBACK_CARD_H + FEEDBACK_CARD_GAP);
            if (cardY + FEEDBACK_CARD_H < contentTop || cardY > contentTop + contentH) continue;
            if (mouseY >= cardY && mouseY <= cardY + FEEDBACK_CARD_H
                && mouseX >= contentX && mouseX <= contentX + contentW) {
                if (feedbackSelected.contains(e.fileName())) {
                    feedbackSelected.remove(e.fileName());
                } else {
                    feedbackSelected.add(e.fileName());
                }
                return;
            }
        }
    }

    private void renderFeedbackTab(GuiGraphics graphics, int px, int py) {
        // 年月日标签（两行：开始在上，结束在下）
        graphics.drawString(this.font, Component.literal("开始"), px + 10, py + 42, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("年"), px + 97, py + 42, 0xFFAAAAAA, false);
        graphics.drawString(this.font, Component.literal("月"), px + 137, py + 42, 0xFFAAAAAA, false);
        graphics.drawString(this.font, Component.literal("日"), px + 166, py + 42, 0xFFAAAAAA, false);
        graphics.drawString(this.font, Component.literal("结束"), px + 10, py + 66, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.literal("年"), px + 97, py + 66, 0xFFAAAAAA, false);
        graphics.drawString(this.font, Component.literal("月"), px + 137, py + 66, 0xFFAAAAAA, false);
        graphics.drawString(this.font, Component.literal("日"), px + 166, py + 66, 0xFFAAAAAA, false);

        int contentTop = py + 92;
        int contentX = px + 10;
        int contentW = PANEL_W - 20;
        int contentH = PANEL_H - 102;
        int totalHeight = feedbackEntries.size() * (FEEDBACK_CARD_H + FEEDBACK_CARD_GAP);
        if (totalHeight > contentH && feedbackScrollOffset > totalHeight - contentH) {
            feedbackScrollOffset = Math.max(0, totalHeight - contentH);
        }

        int currentY = contentTop - (int) feedbackScrollOffset;
        for (int i = 0; i < feedbackEntries.size(); i++) {
            FeedbackEntry e = feedbackEntries.get(i);
            int cardY = currentY + i * (FEEDBACK_CARD_H + FEEDBACK_CARD_GAP);
            if (cardY + FEEDBACK_CARD_H < contentTop || cardY > contentTop + contentH) continue;

            if (feedbackSelected.contains(e.fileName())) {
                TrainStyleRenderHelper.renderSelectedCard(graphics, contentX, cardY, contentW, FEEDBACK_CARD_H);
            } else {
                TrainStyleRenderHelper.renderCard(graphics, contentX, cardY, contentW, FEEDBACK_CARD_H);
            }

            String name = e.anonymous() ? "匿名玩家" : e.playerName();
            if (this.font.width(name) > contentW - 36) {
                name = this.font.plainSubstrByWidth(name, contentW - 36) + "...";
            }
            graphics.drawString(this.font, Component.literal(name), contentX + 24, cardY + 6, 0x333333, false);
            String time = formatFeedbackTime(e.timestamp());
            if (this.font.width(time) > contentW - 36) {
                time = this.font.plainSubstrByWidth(time, contentW - 36) + "...";
            }
            graphics.drawString(this.font, Component.literal(time), contentX + 24, cardY + 18, 0x666666, false);
            // 内容预览：替换换行 + 限制宽度，绝不超出卡片
            String preview = e.content().replace('\n', ' ').replace('\r', ' ').trim();
            int maxPreviewW = contentW - 36;
            if (this.font.width(preview) > maxPreviewW) {
                preview = this.font.plainSubstrByWidth(preview, maxPreviewW) + "...";
            }
            graphics.drawString(this.font, Component.literal(preview), contentX + 24, cardY + 30, 0x666666, false);

            int checkX = contentX + 6;
            int checkY = cardY + 14;
            graphics.fill(checkX, checkY, checkX + 14, checkY + 14, 0xFFFFFFFF);
            graphics.renderOutline(checkX, checkY, 14, 14, TrainStyleRenderHelper.CARD_BORDER);
            if (feedbackSelected.contains(e.fileName())) {
                graphics.drawString(this.font, Component.literal("✓"), checkX + 3, checkY + 2, TrainStyleRenderHelper.CARD_BORDER, false);
            }
        }
    }

    // ================== 投稿设置标签 ==================

    private List<com.kghua.npcai.data.Contribution> getFilteredContributions() {
        String selected = com.kghua.npcai.data.Contribution.TYPES[contributionSubTab];
        List<com.kghua.npcai.data.Contribution> result = new ArrayList<>();
        for (com.kghua.npcai.data.Contribution c : contributions) {
            if (c.getType().equals(selected) && c.getPeriod() == contributionSelectedPeriod) {
                result.add(c);
            }
        }
        // 按点赞数降序
        result.sort((a, b) -> Integer.compare(b.getLikes(), a.getLikes()));
        return result;
    }

    private void renderContributionPeriodMenu(GuiGraphics graphics, int px, int py, int mouseX, int mouseY) {
        if (!contributionPeriodMenuOpen) return;
        // 期数列表浮层：从大到小，最多显示4期，滚轮滑动
        // 菜单跟随期数按钮（子板块第4位，铺满布局）的位置，与按钮左对齐
        int subBtnW = (PANEL_W - 16 - 3 * 6) / 4;
        int menuX = px + 8 + 3 * (subBtnW + 6);
        int menuY = py + 52;
        int menuW = 76;
        int itemH = 16;
        int maxVisible = 4;

        int maxPeriod = Math.max(contributionMaxPeriod, com.kghua.npcai.data.Contribution.getCurrentPeriod());
        int totalItems = maxPeriod; // 期数1..maxPeriod
        int totalH = totalItems * itemH;
        int visibleH = Math.min(totalH, maxVisible * itemH);
        if (totalH > visibleH && contributionPeriodScroll > totalH - visibleH) {
            contributionPeriodScroll = Math.max(0, totalH - visibleH);
        }

        // 面板背景
        graphics.fill(menuX, menuY, menuX + menuW, menuY + visibleH + 2, 0xFF202020);
        graphics.renderOutline(menuX, menuY, menuW, visibleH + 2, 0xFFCC9900);

        int currentY = menuY + 1 - (int) contributionPeriodScroll;
        // 从大到小：第maxPeriod期在最上面
        for (int p = maxPeriod; p >= 1; p--) {
            int itemY = currentY + (maxPeriod - p) * itemH;
            if (itemY + itemH < menuY || itemY > menuY + visibleH + 2) continue;
            boolean selected = p == contributionSelectedPeriod;
            if (selected) {
                graphics.fill(menuX + 1, itemY, menuX + menuW - 1, itemY + itemH, 0xFFCC9900);
                graphics.drawString(this.font, Component.literal("第" + p + "期"), menuX + 4, itemY + 4, 0xFF333333, false);
            } else {
                boolean hovered = isMouseOver(menuX, itemY, menuW, itemH, mouseX, mouseY);
                if (hovered) graphics.fill(menuX + 1, itemY, menuX + menuW - 1, itemY + itemH, 0xFF444444);
                graphics.drawString(this.font, Component.literal("第" + p + "期"), menuX + 4, itemY + 4, 0xFFFFFFFF, false);
            }
        }
    }

    private void handleContributionPeriodClick(double mouseX, double mouseY, int px, int py) {
        if (!contributionPeriodMenuOpen) return;
        // 与渲染同步：菜单跟随期数按钮（子板块第4位）的位置
        int subBtnW = (PANEL_W - 16 - 3 * 6) / 4;
        int menuX = px + 8 + 3 * (subBtnW + 6);
        int menuY = py + 52;
        int menuW = 76;
        int itemH = 16;
        int maxVisible = 4;
        int maxPeriod = Math.max(contributionMaxPeriod, com.kghua.npcai.data.Contribution.getCurrentPeriod());
        int totalH = maxPeriod * itemH;
        int visibleH = Math.min(totalH, maxVisible * itemH);

        int currentY = menuY + 1 - (int) contributionPeriodScroll;
        for (int p = maxPeriod; p >= 1; p--) {
            int itemY = currentY + (maxPeriod - p) * itemH;
            if (mouseY >= itemY && mouseY <= itemY + itemH
                && mouseX >= menuX && mouseX <= menuX + menuW) {
                contributionSelectedPeriod = p;
                contributionPeriodMenuOpen = false;
                contributionScroll = 0;
                return;
            }
        }
    }

    private void renderContributionTab(GuiGraphics graphics, int px, int py, int mouseX, int mouseY) {
        // 投稿奖励子分区（左右分栏奖励编辑器）
        if (contributionSubTab == 2) {
            renderContributionRewardTab(graphics, px, py);
            renderContributionPeriodMenu(graphics, px, py, mouseX, mouseY);
            return;
        }
        // 子板块按钮背景（在 init 中已添加按钮，这里画卡片区域）
        int contentTop = py + 58 + 20;
        int contentX = px + 10;
        int contentW = PANEL_W - 20;
        int contentH = PANEL_H - 68 - 20;
        List<com.kghua.npcai.data.Contribution> filtered = getFilteredContributions();

        int cardW = (contentW - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        int totalHeight = gridRows(filtered.size()) * (CARD_H + CARD_GAP);
        if (totalHeight > contentH && contributionScroll > totalHeight - contentH) {
            contributionScroll = Math.max(0, totalHeight - contentH);
        }

        int currentY = contentTop - (int) contributionScroll;
        for (int i = 0; i < filtered.size(); i++) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int cardX = contentX + col * (cardW + CARD_GAP);
            int cardY = currentY + row * (CARD_H + CARD_GAP);
            if (cardY + CARD_H < contentTop || cardY > contentTop + contentH) continue;
            com.kghua.npcai.data.Contribution c = filtered.get(i);
            TrainStyleRenderHelper.renderCard(graphics, cardX, cardY, cardW, CARD_H);

            String title = c.getTitle();
            if (this.font.width(title) > cardW - 12) {
                title = this.font.plainSubstrByWidth(title, cardW - 12) + "...";
            }
            graphics.drawString(this.font, Component.literal(title), cardX + 6, cardY + 6, 0x333333, false);
            String author = "by " + c.getAuthorName();
            if (this.font.width(author) > cardW - 12) {
                author = this.font.plainSubstrByWidth(author, cardW - 12) + "...";
            }
            graphics.drawString(this.font, Component.literal(author), cardX + 6, cardY + 20, 0x666666, false);

            // 右下角点赞数
            String likes = "❤ " + c.getLikes();
            graphics.drawString(this.font, Component.literal(likes),
                cardX + cardW - this.font.width(likes) - 4, cardY + CARD_H - 12, 0xCC0000, false);

            // 未审核作品：右上角"审"字小方框
            if (!c.isApproved()) {
                graphics.fill(cardX + cardW - 14, cardY + 2, cardX + cardW - 2, cardY + 14, 0xFFCC00);
                graphics.drawString(this.font, Component.literal("审"),
                    cardX + cardW - 12, cardY + 3, 0x333333, false);
            }
        }

        if (filtered.isEmpty()) {
            graphics.drawString(this.font, Component.literal("暂无投稿"), contentX + 8, contentTop + 10, 0x666666, false);
        }

        // 期数选择菜单浮层（最后绘制在最上层）
        renderContributionPeriodMenu(graphics, px, py, mouseX, mouseY);
    }

    /** 审核操作后刷新投稿列表（重新请求服务端数据） */
    public void requestContributionsRefresh() {
        ClientPlayNetworking.send(new RequestContributionsPacket());
    }

    private void handleContributionClick(double mouseX, double mouseY, int px, int py) {
        // 优先处理期数菜单点击
        if (contributionPeriodMenuOpen) {
            handleContributionPeriodClick(mouseX, mouseY, px, py);
            return;
        }
        // 投稿奖励子分区：编辑框与按钮均由 widget 系统处理
        if (contributionSubTab == 2) return;
        int contentTop = py + 58 + 20;
        int contentX = px + 10;
        int contentW = PANEL_W - 20;
        int contentH = PANEL_H - 68 - 20;
        int cardW = (contentW - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        List<com.kghua.npcai.data.Contribution> filtered = getFilteredContributions();
        int currentY = contentTop - (int) contributionScroll;
        for (int i = 0; i < filtered.size(); i++) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int cardX = contentX + col * (cardW + CARD_GAP);
            int cardY = currentY + row * (CARD_H + CARD_GAP);
            if (mouseY >= cardY && mouseY <= cardY + CARD_H
                && mouseX >= cardX && mouseX <= cardX + cardW) {
                // 打开投稿详情页（返回时回到管理端）
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new ContributionDetailScreen(filtered.get(i), this));
                }
                return;
            }
        }
    }

    // ================== 投稿奖励子分区（tab7第三个子分区） ==================

    /** 右侧栏总高度（第一/二/三名3组）；左侧每次投稿固定不滚动 */
    private int contributionRewardTotalHeight() {
        return 3 * REWARD_SECTION_H + 2 * REWARD_SECTION_GAP;
    }

    /** 当前编辑数值（section 0=每次投稿；1~3=第一/二/三名） */
    private int currentRewardValue(int section, int row) {
        if (section == 0) {
            return row < 4 ? editingContributionRewards.getPerSubmitCard(row)
                : editingContributionRewards.getPerSubmitLottery();
        }
        int place = section - 1;
        return row < 4 ? editingContributionRewards.getPlaceCard(place, row)
            : editingContributionRewards.getPlaceLottery(place);
    }

    /** 写入编辑数值（含边界检查） */
    private void setRewardValue(int section, int row, int value) {
        if (section == 0) {
            if (row < 4) {
                editingContributionRewards.setPerSubmitCard(row, value);
            } else {
                editingContributionRewards.setPerSubmitLottery(value);
            }
        } else {
            int place = section - 1;
            if (row < 4) {
                editingContributionRewards.setPlaceCard(place, row, value);
            } else {
                editingContributionRewards.setPlaceLottery(place, value);
            }
        }
    }

    /** 行标签：前4行为身份卡名，第5行为抽奖次数 */
    private String rewardRowLabel(int row) {
        return row < 4 ? ContributionRewardSettings.CARD_LABELS[row] : "抽奖次数";
    }

    /** 构建投稿奖励编辑框（20个：左1组5项 + 右3组各5项）+ 底部操作按钮 */
    private void rebuildContributionRewardWidgets() {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        for (EditBox box : contributionRewardBoxes) {
            this.removeWidget(box);
        }
        contributionRewardBoxes.clear();
        contributionRewardBaseYs.clear();
        int contentTop = py + REWARD_CONTENT_TOP_OFFSET;
        for (int section = 0; section < 4; section++) {
            int boxX = section == 0 ? px + REWARD_LEFT_BOX_X : px + REWARD_RIGHT_BOX_X;
            int boxW = section == 0
                ? REWARD_DIVIDER_X - REWARD_LEFT_BOX_X - 8
                : PANEL_W - 8 - REWARD_RIGHT_BOX_X;
            // 左侧（每次投稿）顶格固定；右侧（前三名）与左栏顶格对齐，独立滚动
            int baseY = section == 0
                ? contentTop + 16
                : contentTop + (section - 1) * (REWARD_SECTION_H + REWARD_SECTION_GAP) + 16;
            for (int row = 0; row < 5; row++) {
                final int sec = section;
                final int r = row;
                int boxY = baseY + row * REWARD_ROW_H;
                EditBox box = new EditBox(this.font, boxX, boxY, boxW, 14, Component.literal(""));
                box.setMaxLength(4);
                box.setFilter(s -> s.matches("\\d*"));
                box.setValue(String.valueOf(currentRewardValue(sec, r)));
                box.setResponder(value -> {
                    int parsed = 0;
                    try {
                        parsed = Integer.parseInt(value.isEmpty() ? "0" : value);
                    } catch (NumberFormatException ignored) {
                    }
                    setRewardValue(sec, r, Math.max(0, parsed));
                });
                contributionRewardBoxes.add(box);
                contributionRewardBaseYs.add(boxY);
                this.addRenderableWidget(box);
            }
        }
        applyContributionRewardScroll();
        // 底部操作按钮（结算已改为每期到期自动执行，无需手动按钮）
        this.addRenderableWidget(new NoShadowButton(px + 12, py + PANEL_H - 32, 80, 20,
            Component.literal("保存奖励"), btn -> {
                ClientPlayNetworking.send(new SaveContributionRewardsPacket(editingContributionRewards));
                sendHint("§a投稿奖励设置已保存");
            }));
    }

    /** 根据滚动偏移刷新编辑框位置与可见性（内容不出边框；左侧固定、右侧独立滚动） */
    private void applyContributionRewardScroll() {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int contentTop = py + REWARD_CONTENT_TOP_OFFSET;
        int contentBottom = py + PANEL_H - REWARD_CONTENT_BOTTOM_OFFSET;
        for (int i = 0; i < contributionRewardBoxes.size(); i++) {
            EditBox box = contributionRewardBoxes.get(i);
            if (i < 5) {
                // 左侧每次投稿：固定顶部（高度在可见区内，无需滚动）
                box.setY(contributionRewardBaseYs.get(i));
                box.visible = true;
            } else {
                box.setY(contributionRewardBaseYs.get(i) - (int) contributionScroll);
                box.visible = box.getY() >= contentTop && box.getY() + 14 <= contentBottom;
            }
        }
    }

    /** 绘制投稿奖励设置区（左：每次投稿；右：前三名；上下滚动，内容不出边框） */
    private void renderContributionRewardTab(GuiGraphics graphics, int px, int py) {
        int contentTop = py + REWARD_CONTENT_TOP_OFFSET;
        int contentBottom = py + PANEL_H - REWARD_CONTENT_BOTTOM_OFFSET;
        int totalH = contributionRewardTotalHeight();
        int visibleH = contentBottom - contentTop;
        if (totalH > visibleH && contributionScroll > totalH - visibleH) {
            contributionScroll = Math.max(0, totalH - visibleH);
            applyContributionRewardScroll();
        }
        int offset = (int) contributionScroll;
        // 左右分栏分隔线
        graphics.fill(px + REWARD_DIVIDER_X, contentTop, px + REWARD_DIVIDER_X + 1, contentBottom, 0xFF666666);
        // 裁剪区：内容组件不能超出面板边框
        graphics.enableScissor(px + 4, contentTop, px + PANEL_W - 4, contentBottom);
        // 左栏：每次投稿（顶格固定，不随右侧滚动）
        drawRewardSection(graphics, px + REWARD_LEFT_X, px + REWARD_LEFT_BOX_X, contentTop, "每次投稿奖励");
        // 右栏：前三名（与左栏顶格对齐，独立滚动，各栏分别成页）
        drawRewardSection(graphics, px + REWARD_RIGHT_X, px + REWARD_RIGHT_BOX_X, contentTop - offset, "第一名奖励");
        drawRewardSection(graphics, px + REWARD_RIGHT_X, px + REWARD_RIGHT_BOX_X,
            contentTop - offset + REWARD_SECTION_H + REWARD_SECTION_GAP, "第二名奖励");
        drawRewardSection(graphics, px + REWARD_RIGHT_X, px + REWARD_RIGHT_BOX_X,
            contentTop - offset + 2 * (REWARD_SECTION_H + REWARD_SECTION_GAP), "第三名奖励");
        graphics.disableScissor();
    }

    /** 绘制一组奖励：金色标题 + 5行白标签（数值框由 EditBox widget 绘制） */
    private void drawRewardSection(GuiGraphics graphics, int labelX, int boxX, int topY, String title) {
        graphics.drawString(this.font, Component.literal(title), labelX, topY, 0xFFCC00, false);
        for (int row = 0; row < 5; row++) {
            int y = topY + 16 + row * REWARD_ROW_H;
            graphics.drawString(this.font, Component.literal(rewardRowLabel(row)), labelX, y + 2, 0xFFFFFFFF, false);
        }
    }

    // ================== 小脑设置内嵌标签 ==================

    private void renderCerebellumTab(GuiGraphics graphics, int px, int py) {
        String[] labels = {
            "小脑（错杀好人）",
            "狼人互杀（不含手雷）",
            "狼人手雷互杀"
        };
        boolean[] values = {
            editingCerebellumSettings.isWrongKillInnocentEnabled(),
            editingCerebellumSettings.isKillerTeamKillNoGrenadeEnabled(),
            editingCerebellumSettings.isKillerTeamKillGrenadeOnlyEnabled()
        };

        for (int i = 0; i < 3; i++) {
            int boxX = cerebellumCheckboxX[i];
            int boxY = cerebellumCheckboxY[i];
            graphics.fill(boxX, boxY, boxX + 12, boxY + 12, 0xFFFFFFFF);
            graphics.renderOutline(boxX, boxY, 12, 12, TrainStyleRenderHelper.CARD_BORDER);
            if (values[i]) {
                graphics.drawString(this.font, Component.literal("✓"), boxX + 3, boxY + 2, TrainStyleRenderHelper.CARD_BORDER, false);
            }
            graphics.drawString(this.font, Component.literal(labels[i]), boxX + 18, boxY + 2, 0xFFFFFFFF, false);
        }

        int deathLabelY = cerebellumCheckboxY[2] + 22;
        graphics.drawString(this.font, Component.literal("小脑次数"), px + 10, deathLabelY, 0xFFFFFFFF, false);

        // 惩罚修饰符勾选框（替代原惩罚指令编辑器）
        String[] modLabels = {"惩罚-诅咒", "惩罚-高大", "惩罚-晕血症", "惩罚-纳税", "惩罚-偏执", "惩罚-沙哑"};
        boolean[] modValues = {
            editingCerebellumSettings.isCursedEnabled(),
            editingCerebellumSettings.isTallEnabled(),
            editingCerebellumSettings.isHemophobiaEnabled(),
            editingCerebellumSettings.isTaxedEnabled(),
            editingCerebellumSettings.isParanoidEnabled(),
            editingCerebellumSettings.isHoarseEnabled()
        };
        for (int i = 0; i < 6; i++) {
            int boxX = cerebellumModifierCheckboxX[i];
            int boxY = cerebellumModifierCheckboxY[i];
            graphics.fill(boxX, boxY, boxX + 12, boxY + 12, 0xFFFFFFFF);
            graphics.renderOutline(boxX, boxY, 12, 12, TrainStyleRenderHelper.CARD_BORDER);
            if (modValues[i]) {
                graphics.drawString(this.font, Component.literal("✓"), boxX + 3, boxY + 2, TrainStyleRenderHelper.CARD_BORDER, false);
            }
            graphics.drawString(this.font, Component.literal(modLabels[i]), boxX + 18, boxY + 2, 0xFFFFFFFF, false);
        }

        // 右侧预览面板
        int previewX = px + PANEL_W * 3 / 5;
        int previewW = PANEL_W * 2 / 5 - 10;
        int previewY = py + 58;
        int previewH = PANEL_H - 90;
        renderCerebellumPreview(graphics, previewX, previewY, previewW, previewH);
    }

    private void renderCerebellumPreview(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFFFFF0CC);
        graphics.renderOutline(x, y, w, h, 0xFFCC9900);

        // 右上角导出按钮
        int expX = x + w - 44;
        int expY = y + 2;
        TrainStyleRenderHelper.renderInlineButton(graphics, this.font, "导出", expX, expY, 40, 14,
            true, isMouseOver(expX, expY, 40, 14, lastMouseX, lastMouseY));
        cerebellumExportBtnX = expX;
        cerebellumExportBtnY = expY;

        if (cerebellumLeaderboard.isEmpty()) {
            graphics.drawString(this.font, Component.literal("暂无数据"), x + 4, y + 20, 0x666666, false);
            return;
        }

        int lineH = 12;
        int headerY = y + 18;
        int reqDeaths = ClientCache.getCerebellumSettings().getRequiredDeaths();
        graphics.drawString(this.font, Component.literal("玩家 | 小脑次数(" + reqDeaths + "次) | 惩罚次数 | 待执行"), x + 2, headerY, 0xFF333333, false);
        int contentY = headerY + lineH + 2;

        int totalH = cerebellumLeaderboard.size() * lineH;
        if (totalH > h - lineH - 2) {
            cerebellumPreviewScroll = Math.max(0, Math.min(cerebellumPreviewScroll, totalH - (h - lineH - 2)));
        }
        int drawY = contentY - (int) cerebellumPreviewScroll;
        for (SyncCerebellumSettingsPacket.CerebellumEntry e : cerebellumLeaderboard) {
            if (drawY + lineH < y || drawY > y + h) { drawY += lineH; continue; }
            String line = e.playerName() + " | " + e.currentCount() + " | " + e.punishmentCount() + " | " + e.pendingCount();
            if (this.font.width(line) > w - 4) {
                line = this.font.plainSubstrByWidth(line, w - 4);
            }
            graphics.drawString(this.font, Component.literal(line), x + 2, drawY, 0x333333, false);
            drawY += lineH;
        }

        if (totalH > h - lineH - 2) {
            int scrollBarX = x + w - 4;
            int thumbH = Math.max(8, (h - lineH - 2) * (h - lineH - 2) / totalH);
            int thumbY = y + lineH + 2 + (int)(cerebellumPreviewScroll * (h - lineH - 2 - thumbH) / Math.max(1, totalH - (h - lineH - 2)));
            graphics.fill(scrollBarX, thumbY, scrollBarX + 2, thumbY + thumbH, 0xFFCC9900);
        }
    }

    private void rebuildCerebellumEditorWidgets() {
        if (activeTab != 6) return;
        clearWidgets();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        int checkY = py + 58;
        for (int i = 0; i < 3; i++) {
            int boxX = px + 12;
            cerebellumCheckboxX[i] = boxX;
            cerebellumCheckboxY[i] = checkY;
            cerebellumCheckboxBaseY[i] = checkY;
            cerebellumCheckboxW[i] = 12;
            cerebellumCheckboxH[i] = 12;
            checkY += 18;
        }

        this.cerebellumRequiredDeathsBox = new EditBox(this.font, px + 100, checkY + 4, 50, 16, Component.literal(""));
        this.cerebellumRequiredDeathsBox.setMaxLength(4);
        this.cerebellumRequiredDeathsBox.setValue(String.valueOf(editingCerebellumSettings.getRequiredDeaths()));
        this.cerebellumRequiredDeathsBox.setFilter(s -> s.matches("\\d*"));
        this.cerebellumRequiredDeathsBox.setResponder(s -> {
            try {
                int val = Integer.parseInt(s);
                if (val < 1) val = 1;
                editingCerebellumSettings.setRequiredDeaths(val);
                saveCerebellumSettings();
            } catch (NumberFormatException ignored) {}
        });
        this.addRenderableWidget(this.cerebellumRequiredDeathsBox);
        this.cerebellumRequiredDeathsBaseY = this.cerebellumRequiredDeathsBox.getY();

        // 惩罚修饰符勾选框（诅咒/高大/晕血症/纳税/偏执/沙哑），替代原惩罚指令编辑器
        int modY = checkY + 30;
        for (int i = 0; i < 6; i++) {
            cerebellumModifierCheckboxX[i] = px + 12;
            cerebellumModifierCheckboxY[i] = modY;
            cerebellumModifierCheckboxBaseY[i] = modY;
            cerebellumModifierCheckboxW[i] = 12;
            cerebellumModifierCheckboxH[i] = 12;
            modY += 18;
        }

        // 自动保存：修改复选框、死亡次数后立即保存，无需手动保存按钮

        applyCerebellumEditorScroll();
    }

    private void applyCerebellumEditorScroll() {
        if (cerebellumRequiredDeathsBox == null) return;
        int py = (this.height - PANEL_H) / 2;
        int scrollTop = py + 58;
        int scrollBottom = py + PANEL_H - 28;
        int visibleHeight = scrollBottom - scrollTop;
        int totalHeight = Math.max(0, cerebellumModifierCheckboxBaseY[5] + 12 - scrollTop);

        if (totalHeight <= visibleHeight) {
            scrollOffset = 0;
        } else {
            scrollOffset = Math.max(0, Math.min(scrollOffset, totalHeight - visibleHeight));
        }

        for (int i = 0; i < 3; i++) {
            cerebellumCheckboxY[i] = cerebellumCheckboxBaseY[i] - (int) scrollOffset;
        }
        for (int i = 0; i < 6; i++) {
            cerebellumModifierCheckboxY[i] = cerebellumModifierCheckboxBaseY[i] - (int) scrollOffset;
        }
        setWidgetScroll(cerebellumRequiredDeathsBox, cerebellumRequiredDeathsBaseY, scrollTop, scrollBottom);
    }

    private void rebuildFeedbackWidgets(int px, int py) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDefault = now.minusDays(7);

        // 开始日期：年月日三格（下移避免与顶部板块重叠）
        this.feedbackStartYearBox = createDateBox(px + 60, py + 38, 40, String.valueOf(startDefault.getYear()));
        this.feedbackStartMonthBox = createDateBox(px + 104, py + 38, 30, String.format("%02d", startDefault.getMonthValue()));
        this.feedbackStartDayBox = createDateBox(px + 138, py + 38, 30, String.format("%02d", startDefault.getDayOfMonth()));

        // 结束日期：年月日三格
        this.feedbackEndYearBox = createDateBox(px + 60, py + 62, 40, String.valueOf(now.getYear()));
        this.feedbackEndMonthBox = createDateBox(px + 104, py + 62, 30, String.format("%02d", now.getMonthValue()));
        this.feedbackEndDayBox = createDateBox(px + 138, py + 62, 30, String.format("%02d", now.getDayOfMonth()));

        this.addRenderableWidget(new NoShadowButton(px + 300, py + 32, 50, 20,
            Component.literal("查询"), btn -> requestFeedback()));
        this.addRenderableWidget(new NoShadowButton(px + 360, py + 32, 50, 20,
            Component.literal("全选"), btn -> toggleSelectAllFeedback()));
        this.addRenderableWidget(new NoShadowButton(px + 420, py + 32, 50, 20,
            Component.literal("导出"), btn -> exportSelectedFeedback()));
    }

    private EditBox createDateBox(int x, int y, int w, String value) {
        EditBox box = new EditBox(this.font, x, y, w, 16, Component.literal(""));
        box.setMaxLength(4);
        box.setFilter(s -> s.matches("\\d*"));
        box.setValue(value);
        this.addRenderableWidget(box);
        return box;
    }

    private void requestFeedback() {
        try {
            int sy = Integer.parseInt(feedbackStartYearBox.getValue().trim());
            int sm = Integer.parseInt(feedbackStartMonthBox.getValue().trim());
            int sd = Integer.parseInt(feedbackStartDayBox.getValue().trim());
            int ey = Integer.parseInt(feedbackEndYearBox.getValue().trim());
            int em = Integer.parseInt(feedbackEndMonthBox.getValue().trim());
            int ed = Integer.parseInt(feedbackEndDayBox.getValue().trim());
            LocalDateTime start = LocalDateTime.of(sy, sm, sd, 0, 0);
            LocalDateTime end = LocalDateTime.of(ey, em, ed, 23, 59, 59);
            long startMs = start.toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
            long endMs = end.toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
            ClientPlayNetworking.send(new RequestFeedbackPacket(startMs, endMs));
        } catch (Exception e) {
            sendHint("§c日期格式错误，请检查年月日");
        }
    }

    private void toggleSelectAllFeedback() {
        if (feedbackSelected.size() == feedbackEntries.size()) {
            feedbackSelected.clear();
        } else {
            feedbackSelected.clear();
            for (FeedbackEntry e : feedbackEntries) {
                feedbackSelected.add(e.fileName());
            }
        }
    }

    private void exportSelectedFeedback() {
        if (feedbackSelected.isEmpty()) {
            sendHint("§c未选择任何反馈");
            return;
        }
        ClientPlayNetworking.send(new ExportFeedbackPacket(new ArrayList<>(feedbackSelected)));
        sendHint("§a已导出 " + feedbackSelected.size() + " 条反馈");
    }

    private String formatFeedbackTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.ofHours(8)).format(FEEDBACK_FORMATTER);
    }

    private String formatQuestionnaireTime(long millis) {
        if (millis <= 0) return "无";
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.ofHours(8)).format(FEEDBACK_FORMATTER);
    }

    private void toggleCerebellumMode(int index) {
        switch (index) {
            case 0 -> editingCerebellumSettings.setWrongKillInnocentEnabled(!editingCerebellumSettings.isWrongKillInnocentEnabled());
            case 1 -> editingCerebellumSettings.setKillerTeamKillNoGrenadeEnabled(!editingCerebellumSettings.isKillerTeamKillNoGrenadeEnabled());
            case 2 -> editingCerebellumSettings.setKillerTeamKillGrenadeOnlyEnabled(!editingCerebellumSettings.isKillerTeamKillGrenadeOnlyEnabled());
            case 3 -> editingCerebellumSettings.setCursedEnabled(!editingCerebellumSettings.isCursedEnabled());
            case 4 -> editingCerebellumSettings.setTallEnabled(!editingCerebellumSettings.isTallEnabled());
            case 5 -> editingCerebellumSettings.setHemophobiaEnabled(!editingCerebellumSettings.isHemophobiaEnabled());
            case 6 -> editingCerebellumSettings.setTaxedEnabled(!editingCerebellumSettings.isTaxedEnabled());
            case 7 -> editingCerebellumSettings.setParanoidEnabled(!editingCerebellumSettings.isParanoidEnabled());
            case 8 -> editingCerebellumSettings.setHoarseEnabled(!editingCerebellumSettings.isHoarseEnabled());
        }
        saveCerebellumSettings();
        rebuildCerebellumEditorWidgets();
    }

    private void saveCerebellumSettings() {
        if (cerebellumRequiredDeathsBox != null) {
            try {
                editingCerebellumSettings.setRequiredDeaths(Integer.parseInt(cerebellumRequiredDeathsBox.getValue().trim()));
            } catch (NumberFormatException ignored) {
                editingCerebellumSettings.setRequiredDeaths(1);
            }
        }

        // 每次都发送（包很小），确保开关/次数修改必定保存生效
        ClientPlayNetworking.send(new SaveCerebellumSettingsPacket(editingCerebellumSettings));
        ClientCache.setCerebellumSettings(editingCerebellumSettings);
        cerebellumSettings = editingCerebellumSettings;
    }

    private void resetCerebellumSettings() {
        editingCerebellumSettings = new CerebellumSettings();
        editingCerebellumSettings.setWrongKillInnocentEnabled(cerebellumSettings.isWrongKillInnocentEnabled());
        editingCerebellumSettings.setKillerTeamKillNoGrenadeEnabled(cerebellumSettings.isKillerTeamKillNoGrenadeEnabled());
        editingCerebellumSettings.setKillerTeamKillGrenadeOnlyEnabled(cerebellumSettings.isKillerTeamKillGrenadeOnlyEnabled());
        editingCerebellumSettings.setRequiredDeaths(cerebellumSettings.getRequiredDeaths());
        editingCerebellumSettings.setCursedEnabled(cerebellumSettings.isCursedEnabled());
        editingCerebellumSettings.setTallEnabled(cerebellumSettings.isTallEnabled());
        editingCerebellumSettings.setHemophobiaEnabled(cerebellumSettings.isHemophobiaEnabled());
        editingCerebellumSettings.setTaxedEnabled(cerebellumSettings.isTaxedEnabled());
        editingCerebellumSettings.setParanoidEnabled(cerebellumSettings.isParanoidEnabled());
        editingCerebellumSettings.setHoarseEnabled(cerebellumSettings.isHoarseEnabled());
        rebuildCerebellumEditorWidgets();
    }

    private void handleCerebellumClick(double mouseX, double mouseY, int px, int py) {
        // 小脑预览导出按钮
        if (cerebellumExportBtnX >= 0 && cerebellumExportBtnY >= 0
            && isMouseOver(cerebellumExportBtnX, cerebellumExportBtnY, 40, 14, (int) mouseX, (int) mouseY)) {
            ClientPlayNetworking.send(new com.kghua.npcai.network.ExportCerebellumPacket());
            return;
        }
        for (int i = 0; i < 3; i++) {
            if (isMouseOver(cerebellumCheckboxX[i], cerebellumCheckboxY[i], cerebellumCheckboxW[i], cerebellumCheckboxH[i], (int) mouseX, (int) mouseY)) {
                toggleCerebellumMode(i);
                return;
            }
        }
        // 惩罚修饰符勾选框（index 3-8）
        for (int i = 0; i < 6; i++) {
            if (isMouseOver(cerebellumModifierCheckboxX[i], cerebellumModifierCheckboxY[i],
                cerebellumModifierCheckboxW[i], cerebellumModifierCheckboxH[i], (int) mouseX, (int) mouseY)) {
                toggleCerebellumMode(3 + i);
                return;
            }
        }
    }

    // ================== 通用辅助 ==================

    private void clampScroll(int totalHeight, int visibleHeight) {
        if (totalHeight > visibleHeight && scrollOffset > totalHeight - visibleHeight) {
            scrollOffset = Math.max(0, totalHeight - visibleHeight);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeTab == 0 || activeTab == 1 || activeTab == 2 || activeTab == 3 || activeTab == 4 || activeTab == 5 || activeTab == 6 || activeTab == 7 || activeTab == 8) {
            if (activeTab == 0) {
                int py = (this.height - PANEL_H) / 2;
                int scrollTop = py + 48;
                int scrollBottom = py + PANEL_H - 10;
                int totalHeight = Math.max(0, deleteBtnBaseY + 20 - scrollTop);
                if (totalHeight > scrollBottom - scrollTop) {
                    settingsScrollOffset -= scrollY * 15;
                    settingsScrollOffset = Math.max(0, Math.min(settingsScrollOffset, totalHeight - (scrollBottom - scrollTop)));
                    applySettingsScroll();
                }
                return true;
            }
            if (activeTab == 5 && editingCompensationRule != null) {
                int py = (this.height - PANEL_H) / 2;
                int visibleHeight = PANEL_H - 80;
                int totalHeight = Math.max(0, compensationAddCommandBaseY + 18 - (py + 52));
                if (totalHeight > visibleHeight) {
                    scrollOffset -= scrollY * 15;
                    applyCompensationEditorScroll();
                }
                return true;
            }
            if (activeTab == 6) {
                int px = (this.width - PANEL_W) / 2;
                int py = (this.height - PANEL_H) / 2;
                // 鼠标在右侧小脑榜预览面板内 → 滚动预览列表
                int previewX = px + PANEL_W * 3 / 5;
                int previewW = PANEL_W * 2 / 5 - 10;
                int previewY = py + 58;
                int previewH = PANEL_H - 90;
                if (mouseX >= previewX && mouseX <= previewX + previewW
                    && mouseY >= previewY && mouseY <= previewY + previewH) {
                    int lineH = 12;
                    int totalH = cerebellumLeaderboard.size() * lineH;
                    if (totalH > previewH - lineH - 2) {
                        cerebellumPreviewScroll -= scrollY * 12;
                        cerebellumPreviewScroll = Math.max(0,
                            Math.min(cerebellumPreviewScroll, totalH - (previewH - lineH - 2)));
                    }
                    return true;
                }
                // 左侧编辑器区域滚动
                int visibleHeight = PANEL_H - 86;
                int totalHeight = Math.max(0, cerebellumModifierCheckboxBaseY[5] + 12 - (py + 58));
                if (totalHeight > visibleHeight) {
                    scrollOffset -= scrollY * 15;
                    applyCerebellumEditorScroll();
                }
                return true;
            }
            if (activeTab == 4) {
                // 邮箱编辑器为固定布局，无需滚动
                return true;
            }
            int totalHeight = 0;
            int visibleHeight = PANEL_H - 68;
            if (activeTab == 1) {
                totalHeight = gridRows(points.size()) * (CARD_H + CARD_GAP);
                visibleHeight = PANEL_H - 108; // 子板块栏+搜索栏额外占40px
            }
            else if (activeTab == 2) totalHeight = players.size() * 28;
            else if (activeTab == 3) totalHeight = gridRows(questionnaires.size()) * (CARD_H + CARD_GAP);
            else if (activeTab == 5) totalHeight = compensationRules.size() * (CARD_H + CARD_GAP) + 10;
            else if (activeTab == 7) {
                // 期数菜单打开时优先滚动期数列表
                if (contributionPeriodMenuOpen) {
                    int maxPeriod = Math.max(contributionMaxPeriod, com.kghua.npcai.data.Contribution.getCurrentPeriod());
                    int totalH = maxPeriod * 16;
                    int visibleH = Math.min(totalH, 4 * 16);
                    if (totalH > visibleH) {
                        contributionPeriodScroll -= scrollY * 15;
                        contributionPeriodScroll = Math.max(0, Math.min(contributionPeriodScroll, totalH - visibleH));
                    }
                    return true;
                }
                if (contributionSubTab == 2) {
                    // 投稿奖励编辑区滚动
                    int totalH = contributionRewardTotalHeight();
                    int visibleH = PANEL_H - REWARD_CONTENT_TOP_OFFSET - REWARD_CONTENT_BOTTOM_OFFSET;
                    if (totalH > visibleH) {
                        contributionScroll -= scrollY * 15;
                        contributionScroll = Math.max(0, Math.min(contributionScroll, totalH - visibleH));
                    }
                    applyContributionRewardScroll();
                    return true;
                }
                int total = getFilteredContributions().size() * (CARD_H + CARD_GAP);
                int vis = PANEL_H - 108;
                if (total > vis) {
                    contributionScroll -= scrollY * 15;
                    contributionScroll = Math.max(0, Math.min(contributionScroll, total - vis));
                }
                return true;
            }
            else if (activeTab == 8) {
                totalHeight = feedbackEntries.size() * (FEEDBACK_CARD_H + FEEDBACK_CARD_GAP);
                int feedbackVisible = PANEL_H - 102; // 与反馈内容区实际高度一致
                if (totalHeight > feedbackVisible) {
                    feedbackScrollOffset -= scrollY * 15;
                    feedbackScrollOffset = Math.max(0, Math.min(feedbackScrollOffset, totalHeight - feedbackVisible));
                }
                return true;
            }
            if (totalHeight > visibleHeight) {
                scrollOffset -= scrollY * 15;
                scrollOffset = Math.max(0, Math.min(scrollOffset, totalHeight - visibleHeight));
            }
        }
        return true;
    }

    private void sendHint(String msg) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal(msg));
        }
    }

    private int gridRows(int count) {
        return (count + COLUMNS - 1) / COLUMNS;
    }

    private boolean isMouseOver(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private ResourceLocation getPlayerSkinTexture(SyncPlayerListPacket.PlayerInfo p) {
        GameProfile profile = new GameProfile(p.id(), p.name());
        var playerSkin = Minecraft.getInstance().getSkinManager().getInsecureSkin(profile);
        if (playerSkin != null) {
            return playerSkin.texture();
        }
        return DefaultPlayerSkin.getDefaultTexture();
    }

    private int parseColor(String color) {
        if (color == null || color.isEmpty()) return 0xFFFFFFFF;
        String c = color;
        if (c.startsWith("#")) c = c.substring(1);
        try {
            return 0xFF000000 | Integer.parseInt(c, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFFFF;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
