package com.kghua.npcai.client.screen;

import com.kghua.npcai.client.ClientCache;
import com.kghua.npcai.data.CompensationRule;
import com.kghua.npcai.network.SaveCompensationRulesPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 补偿机制设置页面。
 */
public class CompensationSettingsScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 220;
    private static final int CARD_H = 44;
    private static final int CARD_GAP = 6;

    public static final String[] DEATH_REASONS = {
        "noellesroles:voodoo",
        "noellesroles:shot_innocent",
        "western_cowboy:teammate_kill",
        "sre:fell_out_of_train",
        "sre:poison",
        "sre:grenade",
        "sre:bat_hit",
        "sre:gun_shot",
        "sre:knife_stab",
        "sre:generic"
    };

    public static final java.util.Map<String, String> DEATH_REASON_NAMES = java.util.Map.ofEntries(
        java.util.Map.entry("noellesroles:voodoo", "巫毒"),
        java.util.Map.entry("noellesroles:shot_innocent", "误杀好人"),
        java.util.Map.entry("western_cowboy:teammate_kill", "被队友误杀"),
        java.util.Map.entry("sre:fell_out_of_train", "掉出列车"),
        java.util.Map.entry("sre:poison", "中毒"),
        java.util.Map.entry("sre:grenade", "手雷"),
        java.util.Map.entry("sre:bat_hit", "球棒击打"),
        java.util.Map.entry("sre:gun_shot", "枪击"),
        java.util.Map.entry("sre:knife_stab", "刀刺"),
        java.util.Map.entry("sre:generic", "通用")
    );

    public static String translateDeathReason(String reason) {
        return DEATH_REASON_NAMES.getOrDefault(reason, reason);
    }

    private final List<CompensationRule> rules = new ArrayList<>();
    private CompensationRule editingRule = null;
    private boolean isAdding = false;

    private EditBox titleBox;
    private Button reasonButton;
    private EditBox requiredDeathsBox;
    private final List<EditBox> commandNameBoxes = new ArrayList<>();
    private final List<EditBox> commandCmdBoxes = new ArrayList<>();
    private final List<Button> commandDeleteButtons = new ArrayList<>();

    private double scrollOffset = 0;

    public CompensationSettingsScreen() {
        super(Component.literal("补偿机制设置"));
        this.rules.addAll(ClientCache.getCompensationRules());
    }

    @Override
    protected void init() {
        super.init();
        rebuildEditorWidgets();
    }

    private void rebuildEditorWidgets() {
        clearWidgets();
        commandNameBoxes.clear();
        commandCmdBoxes.clear();
        commandDeleteButtons.clear();

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        if (editingRule == null) {
            this.addRenderableWidget(new NoShadowButton(px + PANEL_W - 110, py + 36, 100, 18,
                Component.literal("+ 添加补偿机制"), btn -> startAdd()));
        } else {
            this.titleBox = new EditBox(this.font, px + 70, py + 32, 200, 16, Component.literal(""));
            this.titleBox.setMaxLength(64);
            this.titleBox.setValue(editingRule.getTitle());
            this.addRenderableWidget(this.titleBox);

            this.reasonButton = new NoShadowButton(px + 70, py + 52, 200, 18,
                Component.literal(editingRule.getDeathReason()), btn -> cycleReason());
            this.addRenderableWidget(this.reasonButton);

            this.requiredDeathsBox = new EditBox(this.font, px + 120, py + 74, 50, 16, Component.literal(""));
            this.requiredDeathsBox.setMaxLength(4);
            this.requiredDeathsBox.setValue(String.valueOf(editingRule.getRequiredDeaths()));
            this.requiredDeathsBox.setFilter(s -> s.matches("\\d*"));
            this.addRenderableWidget(this.requiredDeathsBox);

            int cmdY = py + 96;
            List<CompensationRule.CommandEntry> entries = editingRule.getCommands();
            for (int i = 0; i < entries.size(); i++) {
                CompensationRule.CommandEntry entry = entries.get(i);
                EditBox nameBox = new EditBox(this.font, px + 10, cmdY, 90, 16, Component.literal(""));
                nameBox.setMaxLength(64);
                nameBox.setValue(entry.name);
                nameBox.setHint(Component.literal("名称"));
                this.commandNameBoxes.add(nameBox);
                this.addRenderableWidget(nameBox);

                EditBox cmdBox = new EditBox(this.font, px + 104, cmdY, 150, 16, Component.literal(""));
                cmdBox.setMaxLength(256);
                cmdBox.setValue(entry.command);
                cmdBox.setHint(Component.literal("指令"));
                this.commandCmdBoxes.add(cmdBox);
                this.addRenderableWidget(cmdBox);

                final int index = i;
                Button delBtn = new NoShadowButton(px + 258, cmdY, 18, 16,
                    Component.literal("×"), btn -> deleteCommand(index));
                this.commandDeleteButtons.add(delBtn);
                this.addRenderableWidget(delBtn);

                cmdY += 22;
            }

            this.addRenderableWidget(new NoShadowButton(px + 10, cmdY, 80, 18,
                Component.literal("+ 添加补偿"), btn -> addCommand()));

            int bottomY = py + PANEL_H - 28;
            this.addRenderableWidget(new NoShadowButton(px + 60, bottomY, 60, 20,
                Component.literal("保存"), btn -> saveRule()));
            this.addRenderableWidget(new NoShadowButton(px + 130, bottomY, 60, 20,
                Component.literal("取消"), btn -> closeEditor()));
            if (!isAdding) {
                this.addRenderableWidget(new NoShadowButton(px + 200, bottomY, 60, 20,
                    Component.literal("删除"), btn -> deleteRule()));
            }
        }
    }

    private void startAdd() {
        this.editingRule = new CompensationRule(UUID.randomUUID());
        this.editingRule.setTitle("");
        this.editingRule.setDeathReason(DEATH_REASONS[0]);
        this.editingRule.setRequiredDeaths(1);
        this.isAdding = true;
        this.scrollOffset = 0;
        rebuildEditorWidgets();
    }

    private void startEdit(CompensationRule rule) {
        this.editingRule = rule;
        this.isAdding = false;
        this.scrollOffset = 0;
        rebuildEditorWidgets();
    }

    private void closeEditor() {
        this.editingRule = null;
        this.isAdding = false;
        this.scrollOffset = 0;
        rebuildEditorWidgets();
    }

    private void cycleReason() {
        if (editingRule == null) return;
        String current = editingRule.getDeathReason();
        int index = 0;
        for (int i = 0; i < DEATH_REASONS.length; i++) {
            if (DEATH_REASONS[i].equals(current)) {
                index = i;
                break;
            }
        }
        editingRule.setDeathReason(DEATH_REASONS[(index + 1) % DEATH_REASONS.length]);
        if (reasonButton != null) {
            reasonButton.setMessage(Component.literal(editingRule.getDeathReason()));
        }
    }

    private void addCommand() {
        if (editingRule == null) return;
        editingRule.getCommands().add(new CompensationRule.CommandEntry());
        rebuildEditorWidgets();
    }

    private void deleteCommand(int index) {
        if (editingRule == null || index < 0 || index >= editingRule.getCommands().size()) return;
        editingRule.getCommands().remove(index);
        rebuildEditorWidgets();
    }

    private void saveRule() {
        if (editingRule == null) return;
        String title = titleBox.getValue().trim();
        editingRule.setTitle(title);
        try {
            editingRule.setRequiredDeaths(Integer.parseInt(requiredDeathsBox.getValue().trim()));
        } catch (NumberFormatException ignored) {
            editingRule.setRequiredDeaths(1);
        }

        List<CompensationRule.CommandEntry> entries = editingRule.getCommands();
        for (int i = 0; i < entries.size(); i++) {
            CompensationRule.CommandEntry entry = entries.get(i);
            entry.name = commandNameBoxes.get(i).getValue().trim();
            entry.command = commandCmdBoxes.get(i).getValue().trim();
        }

        if (isAdding) {
            rules.add(editingRule);
        } else {
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).getId().equals(editingRule.getId())) {
                    rules.set(i, editingRule);
                    break;
                }
            }
        }

        ClientPlayNetworking.send(new SaveCompensationRulesPacket(new ArrayList<>(rules)));
        ClientCache.setCompensationRules(new ArrayList<>(rules));
        closeEditor();
    }

    private void deleteRule() {
        if (editingRule == null) return;
        rules.removeIf(r -> r.getId().equals(editingRule.getId()));
        ClientPlayNetworking.send(new SaveCompensationRulesPacket(new ArrayList<>(rules)));
        ClientCache.setCompensationRules(new ArrayList<>(rules));
        closeEditor();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        TrainStyleRenderHelper.renderPanel(graphics, px, py, PANEL_W, PANEL_H);
        graphics.drawString(this.font, Component.literal("补偿机制设置"), px + 10, py + 10, 0xFFFFFFFF, false);

        if (editingRule == null) {
            renderRuleList(graphics, px + 10, py + 36, PANEL_W - 20, PANEL_H - 46);
        } else {
            graphics.drawString(this.font, Component.literal("标题"), px + 10, py + 36, 0xFFFFFFFF, false);
            graphics.drawString(this.font, Component.literal("死亡原因"), px + 10, py + 56, 0xFFFFFFFF, false);
            graphics.drawString(this.font, Component.literal("需要死亡次数"), px + 10, py + 76, 0xFFFFFFFF, false);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderRuleList(GuiGraphics graphics, int x, int y, int w, int h) {
        int totalHeight = rules.size() * (CARD_H + CARD_GAP) + 10;
        if (totalHeight > h && scrollOffset > totalHeight - h) {
            scrollOffset = Math.max(0, totalHeight - h);
        }

        int currentY = y - (int) scrollOffset;
        for (int i = 0; i < rules.size(); i++) {
            CompensationRule rule = rules.get(i);
            if (currentY + CARD_H >= y && currentY <= y + h) {
                TrainStyleRenderHelper.renderCard(graphics, x, currentY, w, CARD_H);
                String title = rule.getTitle().isEmpty() ? "未命名规则" : rule.getTitle();
                if (this.font.width(title) > w - 12) {
                    title = this.font.plainSubstrByWidth(title, w - 12) + "...";
                }
                graphics.drawString(this.font, Component.literal(title), x + 6, currentY + 6, 0x333333, false);
                graphics.drawString(this.font, Component.literal(rule.getDeathReason() + " / 需 " + rule.getRequiredDeaths() + " 次"),
                    x + 6, currentY + 22, 0x666666, false);
            }
            currentY += CARD_H + CARD_GAP;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && editingRule == null) {
            int px = (this.width - PANEL_W) / 2;
            int py = (this.height - PANEL_H) / 2;
            int contentTop = py + 36;
            int contentX = px + 10;
            int contentW = PANEL_W - 20;
            int currentY = contentTop - (int) scrollOffset;
            for (int i = 0; i < rules.size(); i++) {
                if (mouseY >= currentY && mouseY <= currentY + CARD_H
                    && mouseX >= contentX && mouseX <= contentX + contentW) {
                    startEdit(rules.get(i));
                    return true;
                }
                currentY += CARD_H + CARD_GAP;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (editingRule == null) {
            int totalHeight = rules.size() * (CARD_H + CARD_GAP) + 10;
            int visibleHeight = PANEL_H - 46;
            if (totalHeight > visibleHeight) {
                scrollOffset -= scrollY * 15;
                scrollOffset = Math.max(0, Math.min(scrollOffset, totalHeight - visibleHeight));
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
