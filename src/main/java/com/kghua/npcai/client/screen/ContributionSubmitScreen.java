package com.kghua.npcai.client.screen;

import com.kghua.npcai.data.Contribution;
import com.kghua.npcai.data.ContributionStorage;
import com.kghua.npcai.network.SubmitContributionPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 投稿表单页。
 * 角色：名称/小字描述/角色描述/物品+金币动态行/角色背景（必填：名称、小字描述、角色描述）
 * 修饰符：名称/修饰符描述/修饰符背景（必填：名称、描述）
 * 超出面板滚动+裁剪。
 */
public class ContributionSubmitScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 300;

    private final int entityId;
    private final String npcName;
    private final String type; // 角色 / 修饰符
    private final Screen parentScreen;

    private EditBox titleBox;
    private EditBox shortDescBox;
    private final List<MultiLineEditBox> lines = new ArrayList<>();
    // 角色商店：物品+金币动态行
    private final List<EditBox> shopItemBoxes = new ArrayList<>();
    private final List<EditBox> shopCoinBoxes = new ArrayList<>();
    private Button addShopButton;
    private double scrollOffset = 0;

    // 角色投稿：选择阵营（必选）
    private String selectedFaction = "";
    private final List<Button> factionButtons = new ArrayList<>();
    private final List<Integer> factionButtonBaseYs = new ArrayList<>();
    // 本期已投稿数量统计（服务端同步）
    private final List<Contribution> contributions = new ArrayList<>();

    public ContributionSubmitScreen(int entityId, String npcName, String type) {
        this(entityId, npcName, type, null);
    }

    public ContributionSubmitScreen(int entityId, String npcName, String type, Screen parentScreen) {
        super(Component.literal(type + "投稿"));
        this.entityId = entityId;
        this.npcName = npcName;
        this.type = type;
        this.parentScreen = parentScreen;
    }

    private void backToParent() {
        if (this.minecraft != null && parentScreen != null) {
            this.minecraft.setScreen(parentScreen);
        } else {
            onClose();
        }
    }

    @Override
    protected void init() {
        super.init();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int fieldX = px + 110;
        int fieldW = PANEL_W - 130;

        this.addRenderableWidget(new NoShadowButton(px + 8, py + 10, 50, 18,
            Component.literal("← 返回"), btn -> backToParent()));

        // 标题
        this.titleBox = new EditBox(this.font, fieldX, py + 34, fieldW, 16, Component.literal(""));
        this.titleBox.setMaxLength(64);
        this.addRenderableWidget(this.titleBox);

        // 选择阵营（仅角色）：角色名称下方一个按键，点击循环切换阵营（必选），与输入框左侧对齐
        if (Contribution.TYPE_ROLE.equals(type)) {
            int factionY = py + 58;
            Button btn = new NoShadowButton(fieldX, factionY, 150, 16,
                Component.literal(""), b -> {
                    // 点击切换到下一个阵营（平民→警长→独赢中立→杀手中立→杀手→回到平民）
                    int idx = java.util.Arrays.asList(Contribution.FACTIONS).indexOf(selectedFaction);
                    selectedFaction = Contribution.FACTIONS[(idx + 1) % Contribution.FACTIONS.length];
                    refreshFactionButtons();
                });
            this.addRenderableWidget(btn);
            this.factionButtons.clear();
            this.factionButtons.add(btn);
            this.factionButtonBaseYs.clear();
            this.factionButtonBaseYs.add(factionY);
            refreshFactionButtons();
        }

        // 小字描述（仅角色）
        if (Contribution.TYPE_ROLE.equals(type)) {
            this.shortDescBox = new EditBox(this.font, fieldX, py + 82, fieldW, 16, Component.literal(""));
            this.shortDescBox.setMaxLength(20);
            this.addRenderableWidget(this.shortDescBox);
        }

        // 多行字段：角色描述 / 修饰符描述
        lineLabels.clear();
        if (Contribution.TYPE_ROLE.equals(type)) {
            lineLabels.add("角色描述");
        } else {
            lineLabels.add("修饰符描述");
        }
        // 角色类型因阵营行整体下移 24px
        int baseY = Contribution.TYPE_ROLE.equals(type) ? py + 108 : py + 84;
        for (int i = 0; i < lineLabels.size(); i++) {
            MultiLineEditBox box = new MultiLineEditBox(this.font, fieldX, baseY + i * 82, fieldW, 60,
                Component.literal("多行内容，自动换行"), Component.literal(""));
            this.addRenderableWidget(box);
            this.lines.add(box);
            lineBaseYs.add(box.getY());
            lineLabelBaseYs.add(baseY + i * 84 - 4);
        }

        if (Contribution.TYPE_ROLE.equals(type)) {
            // 角色背景（商店上方）
            int bgY = baseY + 78;
            bgBox = new MultiLineEditBox(this.font, fieldX, bgY, fieldW, 60,
                Component.literal("多行内容，自动换行"), Component.literal(""));
            this.addRenderableWidget(bgBox);
            bgBaseY = bgY;
            bgLabelBaseY = bgY - 4;

            // 商店（最下方）：物品+金币动态行，添加按钮跟随最后一行
            shopAreaTop = bgY + 64;
            addShopRow(shopAreaTop);
            rebuildShopLayout();
        } else {
            // 修饰符背景
            bgBox = new MultiLineEditBox(this.font, fieldX, baseY + 82, fieldW, 68,
                Component.literal("多行内容，自动换行"), Component.literal(""));
            this.addRenderableWidget(bgBox);
            bgBaseY = bgBox.getY();
        }

        this.addRenderableWidget(new NoShadowButton(px + 100, py + PANEL_H - 32, 80, 20,
            Component.literal("提交投稿"), btn -> submit()));
        this.addRenderableWidget(new NoShadowButton(px + 200, py + PANEL_H - 32, 60, 20,
            Component.literal("取消"), btn -> backToParent()));

        applyScroll();
    }

    public void setContributions(List<Contribution> list) {
        this.contributions.clear();
        this.contributions.addAll(list);
    }

    /** 本期两个分区（角色+修饰符）合计已投稿数量 */
    private int myPeriodSubmissionCount() {
        if (this.minecraft == null || this.minecraft.player == null) return 0;
        java.util.UUID me = this.minecraft.player.getUUID();
        int currentPeriod = Contribution.getCurrentPeriod();
        int count = 0;
        for (Contribution c : contributions) {
            if (c.getPeriod() == currentPeriod && me.equals(c.getAuthorId())) count++;
        }
        return count;
    }

    /** 刷新阵营按钮显示（未选显示提示，已选显示绿色✓+阵营名） */
    private void refreshFactionButtons() {
        for (int i = 0; i < factionButtons.size(); i++) {
            factionButtons.get(i).setMessage(Component.literal(
                selectedFaction.isEmpty() ? "§7点击选择阵营" : "§a✓" + selectedFaction));
        }
    }

    /** 添加一行物品+金币（带叉号删除按钮） */
    private void addShopRow(int y) {
        int px = (this.width - PANEL_W) / 2;
        int fieldX = px + 110;
        EditBox itemBox = new EditBox(this.font, fieldX, y, 140, 16, Component.literal(""));
        itemBox.setMaxLength(64);
        itemBox.setHint(Component.literal("物品"));
        this.addRenderableWidget(itemBox);
        this.shopItemBoxes.add(itemBox);
        this.shopItemBaseYs.add(y);

        EditBox coinBox = new EditBox(this.font, fieldX + 148, y, 60, 16, Component.literal(""));
        coinBox.setMaxLength(8);
        coinBox.setFilter(s -> s.matches("\\d*"));
        coinBox.setHint(Component.literal("金币"));
        this.addRenderableWidget(coinBox);
        this.shopCoinBoxes.add(coinBox);
        this.shopCoinBaseYs.add(y);

        // 右侧叉号删除按钮
        int index = this.shopItemBoxes.size() - 1;
        Button delBtn = new NoShadowButton(fieldX + 216, y, 16, 16,
            Component.literal("×"), btn -> deleteShopRow(index));
        this.addRenderableWidget(delBtn);
        this.shopDelButtons.add(delBtn);
        this.shopDelBaseYs.add(y);
    }

    /** 删除一行物品 */
    private void deleteShopRow(int index) {
        if (index < 0 || index >= shopItemBoxes.size()) return;
        this.removeWidget(shopItemBoxes.get(index));
        this.removeWidget(shopCoinBoxes.get(index));
        this.removeWidget(shopDelButtons.get(index));
        shopItemBoxes.remove(index);
        shopCoinBoxes.remove(index);
        shopDelButtons.remove(index);
        shopItemBaseYs.remove(index);
        shopCoinBaseYs.remove(index);
        shopDelBaseYs.remove(index);
        rebuildShopLayout();
    }

    /** 重建商店布局：重排所有行位置，添加按钮跟随最后一行 */
    private void rebuildShopLayout() {
        int px = (this.width - PANEL_W) / 2;
        int fieldX = px + 110;
        int y = shopAreaTop;
        for (int i = 0; i < shopItemBoxes.size(); i++) {
            shopItemBaseYs.set(i, y);
            shopCoinBaseYs.set(i, y);
            shopDelBaseYs.set(i, y);
            shopItemBoxes.get(i).setY(y);
            shopCoinBoxes.get(i).setY(y);
            shopDelButtons.get(i).setY(y);
            y += 20;
        }
        // 添加按钮跟随最后一行下方
        if (this.addShopButton == null) {
            this.addShopButton = new NoShadowButton(fieldX, y + 2, 90, 16,
                Component.literal("+ 添加物品"), btn -> {
                    addShopRow(shopAreaTop + shopItemBoxes.size() * 20);
                    rebuildShopLayout();
                    applyScroll();
                });
            this.addRenderableWidget(this.addShopButton);
        }
        this.addShopButton.setY(y + 2);
        addShopBaseY = this.addShopButton.getY();
        applyScroll();
    }

    private void applyScroll() {
        int py = (this.height - PANEL_H) / 2;
        int scrollTop = py + 30;
        int scrollBottom = py + PANEL_H - 36;
        // 内容总高度（用 baseY 计算，不受已偏移的 getY() 影响）：
        // 商店区域底部（物品行 + 添加按钮），背景框底取两者较大
        int shopBottom = shopAreaTop + shopItemBoxes.size() * 20 + 22;
        int bgBottom = bgBox != null ? bgBaseY + bgBox.getHeight() : 0;
        int lastBottom = Math.max(bgBottom, shopBottom);
        int totalH = Math.max(0, lastBottom - scrollTop);
        int visibleH = scrollBottom - scrollTop;
        if (totalH <= visibleH) {
            scrollOffset = 0;
        } else {
            scrollOffset = Math.max(0, Math.min(scrollOffset, totalH - visibleH));
        }

        setYWithScroll(titleBox, titleBaseY(), scrollTop, scrollBottom);
        for (int i = 0; i < factionButtons.size(); i++) {
            setYWithScroll(factionButtons.get(i), factionButtonBaseYs.get(i), scrollTop, scrollBottom);
        }
        if (shortDescBox != null) setYWithScroll(shortDescBox, shortDescBaseY(), scrollTop, scrollBottom);
        for (int i = 0; i < lines.size(); i++) {
            setYWithScroll(lines.get(i), lineBaseYs.get(i), scrollTop, scrollBottom);
        }
        for (int i = 0; i < shopItemBoxes.size(); i++) {
            setYWithScroll(shopItemBoxes.get(i), shopItemBaseYs.get(i), scrollTop, scrollBottom);
            setYWithScroll(shopCoinBoxes.get(i), shopCoinBaseYs.get(i), scrollTop, scrollBottom);
            setYWithScroll(shopDelButtons.get(i), shopDelBaseYs.get(i), scrollTop, scrollBottom);
        }
        if (addShopButton != null) setYWithScroll(addShopButton, addShopBaseY, scrollTop, scrollBottom);
        if (bgBox != null) setYWithScroll(bgBox, bgBaseY, scrollTop, scrollBottom);
    }

    private int titleBaseY() {
        return this.height / 2 - PANEL_H / 2 + 34;
    }

    private int shortDescBaseY() {
        // 角色类型因阵营行下移（阵营行 py+58，小字描述 py+82）
        return this.height / 2 - PANEL_H / 2 + (Contribution.TYPE_ROLE.equals(type) ? 82 : 58);
    }

    private int factionBaseY() {
        return this.height / 2 - PANEL_H / 2 + 58;
    }

    private void setYWithScroll(AbstractWidget box, int baseY, int scrollTop, int scrollBottom) {
        int y = baseY - (int) scrollOffset;
        box.setY(y);
        box.visible = y + box.getHeight() > scrollTop && y < scrollBottom;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= scrollY * 15;
        applyScroll();
        return true;
    }

    private void submit() {
        // 每期两个分区合计最多投稿5个内容（服务端同样校验）
        if (myPeriodSubmissionCount() >= ContributionStorage.MAX_SUBMISSIONS_PER_PERIOD) {
            sendHint("§c本期两个分区合计最多投稿" + ContributionStorage.MAX_SUBMISSIONS_PER_PERIOD + "个内容");
            return;
        }
        String title = titleBox.getValue().trim();
        if (title.isEmpty()) {
            sendHint("§c" + (Contribution.TYPE_ROLE.equals(type) ? "角色名称" : "修饰符名称") + "不能为空");
            return;
        }
        if (Contribution.TYPE_ROLE.equals(type)) {
            if (selectedFaction.isEmpty()) {
                sendHint("§c请选择阵营");
                return;
            }
            String shortDesc = shortDescBox.getValue().trim();
            if (shortDesc.isEmpty()) {
                sendHint("§c小字描述不能为空（1-20字）");
                return;
            }
            String description = lines.size() > 0 ? lines.get(0).getValue().trim() : "";
            if (description.isEmpty()) {
                sendHint("§c角色描述不能为空");
                return;
            }
            // 商店：打包物品|金币;物品|金币
            StringBuilder shop = new StringBuilder();
            for (int i = 0; i < shopItemBoxes.size(); i++) {
                String item = shopItemBoxes.get(i).getValue().trim();
                String coin = shopCoinBoxes.get(i).getValue().trim();
                if (!item.isEmpty()) {
                    if (shop.length() > 0) shop.append(";");
                    shop.append(item).append("|").append(coin);
                }
            }
            ClientPlayNetworking.send(new SubmitContributionPacket(
                type, title, shortDesc, description, shop.toString(),
                bgBox != null ? bgBox.getValue().trim() : "", selectedFaction));
        } else {
            String description = lines.size() > 0 ? lines.get(0).getValue().trim() : "";
            if (description.isEmpty()) {
                sendHint("§c修饰符描述不能为空");
                return;
            }
            ClientPlayNetworking.send(new SubmitContributionPacket(
                type, title, "", description, "",
                bgBox != null ? bgBox.getValue().trim() : "", ""));
        }
        sendHint("§a请等待审核通过！奖励会以邮箱形式发送！");
        backToParent();
    }

    private void sendHint(String msg) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal(msg));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        graphics.drawString(this.font, Component.literal(type + "投稿"), px + 66, py + 12, 0xFFFFFFFF, false);

        // 右上角显示本期已投稿数量（达到上限变金色提示）
        int count = myPeriodSubmissionCount();
        String countText = "本期 " + count + "/" + ContributionStorage.MAX_SUBMISSIONS_PER_PERIOD;
        graphics.drawString(this.font, Component.literal(countText),
            px + PANEL_W - this.font.width(countText) - 8, py + 13,
            count >= ContributionStorage.MAX_SUBMISSIONS_PER_PERIOD ? 0xFFCC00 : 0xFFFFFF, false);

        // 内容区结界：组件渲染裁剪在面板内，超出即消失（顶部从标题下方开始）
        graphics.enableScissor(px + 4, py + 24, px + PANEL_W - 4, py + PANEL_H - 4);

        int labelX = px + 10;
        int scrollTop = py + 30;
        int scrollBottom = py + PANEL_H - 36;
        drawLabel(graphics, Contribution.TYPE_ROLE.equals(type) ? "角色名称" : "修饰符名称", labelX, titleBaseY(), scrollTop, scrollBottom);
        if (Contribution.TYPE_ROLE.equals(type)) {
            drawLabel(graphics, "选择阵营", labelX, factionBaseY(), scrollTop, scrollBottom);
        }
        if (shortDescBox != null) {
            drawLabel(graphics, "小字描述(1-20字)", labelX, shortDescBaseY(), scrollTop, scrollBottom);
        }
        for (int i = 0; i < lineLabels.size(); i++) {
            drawLabel(graphics, lineLabels.get(i), labelX, lineLabelBaseYs.get(i), scrollTop, scrollBottom);
        }
        if (Contribution.TYPE_ROLE.equals(type)) {
            drawLabel(graphics, "商店物品", labelX, shopAreaTop - 6, scrollTop, scrollBottom);
            drawLabel(graphics, "角色背景", labelX, bgBaseY - 4, scrollTop, scrollBottom);
        } else {
            drawLabel(graphics, "修饰符背景", labelX, bgBaseY - 4, scrollTop, scrollBottom);
        }

        graphics.disableScissor();

        // widgets（含左上角返回按钮）在 scissor 之外渲染，完整显示不被裁
        super.render(graphics, mouseX, mouseY, delta);
    }

    private void drawLabel(GuiGraphics graphics, String text, int x, int baseY, int scrollTop, int scrollBottom) {
        int y = baseY - (int) scrollOffset;
        if (y > scrollTop && y < scrollBottom) {
            graphics.drawString(this.font, Component.literal(text), x, y, 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // 滚动基准字段
    private final List<Integer> lineBaseYs = new ArrayList<>();
    private final List<Integer> lineLabelBaseYs = new ArrayList<>();
    private final List<String> lineLabels = new ArrayList<>();
    private final List<Integer> shopItemBaseYs = new ArrayList<>();
    private final List<Integer> shopCoinBaseYs = new ArrayList<>();
    private final List<Button> shopDelButtons = new ArrayList<>();
    private final List<Integer> shopDelBaseYs = new ArrayList<>();
    private int shopAreaTop;
    private int addShopBaseY;
    private MultiLineEditBox bgBox;
    private int bgBaseY;
    private int bgLabelBaseY;
}
