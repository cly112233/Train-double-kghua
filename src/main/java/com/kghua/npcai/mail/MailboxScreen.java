package com.kghua.npcai.mail;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;

public class MailboxScreen extends Screen {
   private static final int BG_TOP = -669380600;
   private static final int BG_BOTTOM = -668986358;
   private static final int BORDER = -7640812;
   private static final int DECOR_LINE = 872409280;
   private static final int GOLD = -2838729;
   private static final int TEXT = -2852;
   private static final int MUTED = -6386834;
   private static final int BODY = -3622760;
   private static final int BLUE = -10569768;
   private static final int GREEN = -9256581;
   private static final int RED = -2069659;
   private static final int HOVER_BG = 587202559;
   private static final int DIVIDER_LINE = 553648127;
   private static final int HEADER_BG = -870707192;
   private static final int FOOTER_BG = -1156445692;
   private static final int ROW_SEL = -12701662;
   private static final int ROW_UNREAD = -1154868208;
   private static final int ROW_READ = -2011557880;
   private static final int ROW_CLAIMED = -2011952122;
   private static final int HDR_H = 34;
   private static final int FOOTER_H = 42;
   private static final int ROW_H = 42;
   private static final int ROW_GAP = 4;
   private static final int ROW_STRIDE = 46;
   private static final int MAX_PANEL_W = 700;
   private static final int MAX_PANEL_H = 450;
   private static final int MIN_PANEL_H = 320;
   private static final SimpleDateFormat DATE_LONG = new SimpleDateFormat("yyyy-MM-dd HH:mm");
   private static final SimpleDateFormat DATE_SHORT = new SimpleDateFormat("MM-dd HH:mm");
   private final MailboxComponent mailbox;
   private List<Mail> cachedMails = new ArrayList<>();
   private int selectedIdx = -1;
   private int page = 0;
   private int panelX;
   private int panelY;
   private int panelW;
   private int panelH;
   private int leftW;
   private int listAreaH;
   private int rowsPerPage;
   private int openTick = 0;
   private static final int OPEN_TICKS = 14;
   private int selectTick = 0;
   private static final int SELECT_TICKS = 8;
   private float[] rowHoverAnims = new float[0];
   private float claimFeedbackTimer = 0.0F;
   private String claimFeedbackText = "";
   private int claimFeedbackColor = -9256581;

   public MailboxScreen() {
      super(Component.translatable("gui.sre.mailbox.title"));
      LocalPlayer player = Minecraft.getInstance().player;
      this.mailbox = (MailboxComponent)MailboxComponent.KEY.get(player);
   }

   private void computeLayout() {
      this.panelW = Math.min(700, (int)((float)this.width * 0.9F));
      this.panelH = Mth.clamp((int)((float)this.height * 0.85F), 320, 450);
      this.panelX = (this.width - this.panelW) / 2;
      this.panelY = (this.height - this.panelH) / 2;
      this.leftW = (int)((float)this.panelW * 0.3F);
      this.listAreaH = this.panelH - 34 - 42;
      this.rowsPerPage = Math.max(1, this.listAreaH / 46);
      if (this.rowHoverAnims.length != this.rowsPerPage) {
         this.rowHoverAnims = new float[this.rowsPerPage];
      }
   }

   private int lx() {
      return this.panelX;
   }

   private int rx() {
      return this.panelX + this.leftW;
   }

   private int rw() {
      return this.panelW - this.leftW;
   }

   protected void init() {
      this.clearWidgets();
      this.computeLayout();
      this.refreshMails();
      this.clampSelectionAndPage();
      this.initButtons();
   }

   private void refreshMails() {
      List<Mail> visible = new ArrayList<>();

      for (Mail m : this.mailbox.getMails()) {
         if (!m.isExpired()) {
            visible.add(m);
         }
      }

      visible.sort((a, b) -> {
         if (a.read != b.read) {
            return a.read ? 1 : -1;
         } else {
            return Long.compare(b.sentAt, a.sentAt);
         }
      });
      this.cachedMails = visible;
   }

   private void clampSelectionAndPage() {
      if (this.cachedMails.isEmpty()) {
         this.selectedIdx = -1;
         this.page = 0;
      } else {
         this.selectedIdx = Mth.clamp(this.selectedIdx, -1, this.cachedMails.size() - 1);
         int maxPage = (this.cachedMails.size() - 1) / this.rowsPerPage;
         this.page = Mth.clamp(this.page, 0, maxPage);
         if (this.selectedIdx >= 0) {
            int selPage = this.selectedIdx / this.rowsPerPage;
            if (selPage != this.page) {
               this.page = selPage;
            }
         }
      }
   }

   private void initButtons() {
      int fy = this.panelY + this.panelH - 42;
      int btnH = 20;
      int btnY = fy + (42 - btnH) / 2;
      int bw1 = 18;
      int bw2 = 68;
      int sp = 3;
      int totalW = bw1 + sp + bw2 + sp + bw2 + sp + bw1;
      int bx = this.lx() + (this.leftW - totalW) / 2;
      int maxPage = this.cachedMails.isEmpty() ? 0 : (this.cachedMails.size() - 1) / this.rowsPerPage;
      if (this.page > 0) {
         this.addRenderableWidget(ModernButton.builder(Component.literal("‹"), btn -> {
            this.page--;
            this.init();
         }).bounds(bx, btnY, bw1, btnH).build());
      }

      bx += bw1 + sp;
      this.addRenderableWidget(ModernButton.builder(Component.translatable("gui.sre.mailbox.claim_all"), btn -> {
         int count = this.mailbox.getClaimableCount();
         ClientPlayNetworking.send(MailClaimAllC2SPayload.INSTANCE);
         if (count > 0) {
            this.showFeedback(Component.translatable("gui.sre.mailbox.feedback_claimed_all", new Object[]{count}).getString(), -2838729);
         }
      }).accentBar(ModernButton.AccentSide.TOP).bounds(bx, btnY, bw2, btnH).accentColor(-2838729).build());
      bx += bw2 + sp;
      this.addRenderableWidget(ModernButton.builder(Component.translatable("gui.sre.mailbox.delete_read"), btn -> {
         ClientPlayNetworking.send(MailDeleteAllReadC2SPayload.INSTANCE);
         this.selectedIdx = -1;
         this.init();
      }).accentBar(ModernButton.AccentSide.TOP).bounds(bx, btnY, bw2, btnH).accentColor(-2069659).build());
      bx += bw2 + sp;
      if (this.page < maxPage) {
         this.addRenderableWidget(ModernButton.builder(Component.literal("›"), btn -> {
            this.page++;
            this.init();
         }).bounds(bx, btnY, bw1, btnH).build());
      }

      Mail sel = this.getSelectedMail();
      if (sel != null) {
         int rbx = this.rx() + 8;
         if (sel.hasRewards() && !sel.claimed && !sel.isExpired()) {
            this.addRenderableWidget(ModernButton.builder(Component.translatable("gui.sre.mailbox.claim"), btn -> {
               ClientPlayNetworking.send(new MailClaimC2SPayload(sel.id));
               sel.claimed = true;
               sel.read = true;
               this.showFeedback(Component.translatable("gui.sre.mailbox.feedback_claimed").getString(), -9256581);
               this.init();
            }).accentBar(ModernButton.AccentSide.TOP).bounds(rbx, btnY, 80, btnH).accentColor(-2838729).build());
            rbx += 84;
         }

         if (sel.canDelete()) {
            this.addRenderableWidget(ModernButton.builder(Component.translatable("gui.sre.mailbox.delete"), btn -> {
               ClientPlayNetworking.send(new MailDeleteC2SPayload(sel.id));
               this.selectedIdx = -1;
               this.init();
            }).accentBar(ModernButton.AccentSide.TOP).bounds(rbx, btnY, 80, btnH).accentColor(-2069659).build());
         }
      }
   }

   private Mail getSelectedMail() {
      return this.selectedIdx >= 0 && this.selectedIdx < this.cachedMails.size() ? this.cachedMails.get(this.selectedIdx) : null;
   }

   public void tick() {
      super.tick();
      if (this.openTick < 14) {
         this.openTick++;
      }

      if (this.selectTick < 8) {
         this.selectTick++;
      }

      if (this.claimFeedbackTimer > 0.0F) {
         this.claimFeedbackTimer--;
      }
   }

   public void renderBackground(GuiGraphics g, int mx, int my, float f) {
      super.renderBackground(g, mx, my, f);
      float ease = easeOutCubic((float)this.openTick / 14.0F);
      int px = this.panelX;
      int py = this.panelY;
      int pw = this.panelW;
      int ph = this.panelH;
      int lw = this.leftW;
      g.fill(0, 0, this.width, this.height, (int)(136.0F * ease) << 24);
      g.fillGradient(px, py, px + pw, py + ph, withAlpha(-669380600, ease), withAlpha(-668986358, ease));
      g.renderOutline(px, py, pw, ph, withAlpha(-7640812, ease));
      g.fill(px + 1, py + 1, px + pw - 1, py + 2, withAlpha(872409280, ease));
      g.fill(px + lw - 1, py + 1, px + lw, py + ph - 1, withAlpha(553648127, ease));
      int hy = py + 34;
      g.fill(px + 1, hy - 1, px + pw - 1, hy, withAlpha(553648127, ease));
      int fy = py + ph - 42;
      g.fill(px + 1, fy, px + pw - 1, fy + 1, withAlpha(553648127, ease));
      g.fill(px + 1, py + 2, px + pw - 1, py + 34, withAlpha(-870707192, ease));
      g.fill(px + 1, fy + 1, px + pw - 1, fy + 42, withAlpha(-1156445692, ease));
   }

   public void render(GuiGraphics g, int mx, int my, float delta) {
      this.updateHoverAnims(mx, my);
      super.render(g, mx, my, delta);
      float ease = easeOutCubic((float)this.openTick / 14.0F);
      float selEase = easeOutCubic((float)this.selectTick / 8.0F);
      this.renderHeader(g, ease, selEase);
      this.renderMailList(g, mx, my, ease, selEase);
      this.renderMailContent(g, mx, my, ease);
      this.renderFooterInfo(g, ease);
      this.renderClaimFeedback(g);
   }

   private void renderHeader(GuiGraphics g, float ease, float selEase) {
      int lx = this.lx();
      int rx = this.rx();
      int titleY = this.panelY + 12;
      g.drawCenteredString(this.font, Component.translatable("gui.sre.mailbox.title"), lx + this.leftW / 2, titleY, withAlpha(-2852, ease));
      int unread = this.mailbox.getUnreadCount();
      if (unread > 0) {
         String badge = String.valueOf(unread);
         int bw = this.font.width(badge) + 6;
         int bx = rx - bw - 6;
         int by = this.panelY + 11;
         g.fill(bx - 1, by - 1, bx + bw + 1, by + 13, withAlpha(-2838729, ease));
         g.fill(bx, by, bx + bw, by + 12, withAlpha(-15069176, ease));
         g.drawString(this.font, badge, bx + 3, by + 2, withAlpha(-2852, ease));
      }

      Mail sel = this.getSelectedMail();
      if (sel != null) {
         String title = sel.title;
         int maxTW = this.rw() - 80;
         if (this.font.width(title) > maxTW) {
            title = this.font.plainSubstrByWidth(title, maxTW - 6) + "…";
         }

         g.drawString(this.font, title, rx + 8, titleY, withAlpha(-2852, ease));
         int tagColor;
         String tag;
         if (sel.claimed) {
            tag = Component.translatable("gui.sre.mailbox.tag_claimed").getString();
            tagColor = -9256581;
         } else if (sel.isExpired()) {
            tag = Component.translatable("gui.sre.mailbox.status_expired").getString();
            tagColor = -2069659;
         } else if (sel.hasRewards()) {
            tag = Component.translatable("gui.sre.mailbox.tag_reward").getString();
            tagColor = -2838729;
         } else {
            tag = null;
            tagColor = 0;
         }

         if (tag != null) {
            g.drawString(this.font, tag, rx + this.rw() - this.font.width(tag) - 8, titleY, withAlpha(tagColor, ease));
         }
      } else {
         g.drawCenteredString(this.font, Component.translatable("gui.sre.mailbox.select_hint"), rx + this.rw() / 2, titleY, withAlpha(-6386834, ease));
      }
   }

   private void updateHoverAnims(int mx, int my) {
      int lx = this.lx();
      int listY = this.panelY + 34;
      int start = this.page * this.rowsPerPage;
      int end = Math.min(start + this.rowsPerPage, this.cachedMails.size());

      for (int i = start; i < end; i++) {
         int rowIdx = i - start;
         int rowY = listY + rowIdx * 46 + 1;
         boolean hov = mx >= lx + 2 && mx < lx + this.leftW - 2 && my >= rowY && my < rowY + 42;
         if (rowIdx < this.rowHoverAnims.length) {
            this.rowHoverAnims[rowIdx] = Mth.lerp(0.22F, this.rowHoverAnims[rowIdx], hov ? 1.0F : 0.0F);
         }
      }
   }

   private void renderMailList(GuiGraphics g, int mx, int my, float ease, float selEase) {
      int lx = this.lx();
      int listY = this.panelY + 34;
      if (this.cachedMails.isEmpty()) {
         g.drawCenteredString(
            this.font, Component.translatable("gui.sre.mailbox.empty"), lx + this.leftW / 2, listY + this.listAreaH / 2 - 4, withAlpha(-6386834, ease)
         );
      } else {
         g.enableScissor(lx + 1, listY, lx + this.leftW - 1, listY + this.listAreaH);
         int start = this.page * this.rowsPerPage;
         int end = Math.min(start + this.rowsPerPage, this.cachedMails.size());

         for (int i = start; i < end; i++) {
            int rowIdx = i - start;
            int rowY = listY + rowIdx * 46 + 1;
            boolean selected = i == this.selectedIdx;
            float hov = rowIdx < this.rowHoverAnims.length ? this.rowHoverAnims[rowIdx] : 0.0F;
            float sa = selected ? selEase : 0.0F;
            this.renderMailRow(g, this.cachedMails.get(i), lx + 2, rowY, this.leftW - 4, 42, selected, hov, sa, ease);
         }

         g.disableScissor();
      }
   }

   private void renderMailRow(GuiGraphics g, Mail mail, int x, int y, int w, int h, boolean selected, float hoverAnim, float selAnim, float ease) {
      int baseBg = mail.claimed ? -2011952122 : (mail.read ? -2011557880 : -1154868208);
      int bg = selected ? blendColors(baseBg, -12701662, selAnim) : baseBg;
      g.fill(x, y, x + w, y + h, withAlpha(bg, ease));
      if (hoverAnim > 0.01F) {
         g.fill(x, y, x + w, y + h, withAlpha(587202559, hoverAnim * ease));
      }

      g.fill(x + 4, y + h - 1, x + w - 4, y + h, withAlpha(553648127, ease));
      if (selected) {
         int ba = (int)(204.0F * selAnim * ease);
         g.fill(x, y, x + 2, y + h, ba << 24 | 13938487);
      } else if (!mail.read) {
         g.fill(x, y, x + 2, y + h, withAlpha(-1144412084, ease));
      } else if (mail.claimed) {
         g.fill(x, y, x + 2, y + h, (int)(136.0F * ease) << 24 | 7520635);
      }

      int titleColor = !selected && mail.read ? -6386834 : -2852;
      String title = mail.title;
      int maxTW = w - 18;
      if (this.font.width(title) > maxTW) {
         title = this.font.plainSubstrByWidth(title, maxTW - 6) + "…";
      }

      g.drawString(this.font, title, x + 6, y + 4, withAlpha(titleColor, ease));
      String sender = mail.sender;
      if (this.font.width(sender) > w - 18) {
         sender = this.font.plainSubstrByWidth(sender, w - 24) + "…";
      }

      g.drawString(this.font, sender, x + 6, y + 15, withAlpha(-6386834, ease));
      String dateStr = DATE_LONG.format(new Date(mail.sentAt));
      if (this.font.width(dateStr) > w - 18) {
         dateStr = DATE_SHORT.format(new Date(mail.sentAt));
      }

      g.drawString(this.font, dateStr, x + 6, y + 27, withAlpha(-6386834, ease));
      if (mail.hasRewards()) {
         String badge = mail.claimed ? "✓" : "★";
         int bc = mail.claimed ? -9256581 : -2838729;
         g.drawString(this.font, badge, x + w - 11, y + 5, withAlpha(bc, ease));
      }
   }

   private void renderMailContent(GuiGraphics g, int mx, int my, float ease) {
      int rx = this.rx();
      int rw = this.rw();
      int cy = this.panelY + 34;
      int ch = this.listAreaH;
      Mail sel = this.getSelectedMail();
      if (sel == null) {
         g.drawCenteredString(this.font, Component.translatable("gui.sre.mailbox.no_selection"), rx + rw / 2, cy + ch / 2 - 4, withAlpha(-6386834, ease));
      } else {
         g.enableScissor(rx + 1, cy, rx + rw - 1, cy + ch);
         String meta = sel.sender + "   " + DATE_LONG.format(new Date(sel.sentAt));
         g.drawString(this.font, meta, rx + 8, cy + 6, withAlpha(-6386834, ease));
         g.fill(rx + 6, cy + 18, rx + rw - 6, cy + 19, withAlpha(553648127, ease));
         int maxTW = rw - 18;
         int lineY = cy + 24;
         int maxBodyY = cy + ch - (sel.attachments.isEmpty() ? 8 : 72);

         for (String line : this.wrapText(sel.content, maxTW)) {
            if (lineY + 10 > maxBodyY) {
               g.drawString(this.font, "…", rx + 8, lineY, withAlpha(-6386834, ease));
               break;
            }

            g.drawString(this.font, line, rx + 8, lineY, withAlpha(-3622760, ease));
            lineY += 11;
         }

         if (!sel.attachments.isEmpty()) {
            int attachY = cy + ch - 68;
            g.fill(rx + 6, attachY, rx + rw - 6, attachY + 1, withAlpha(553648127, ease));
            g.drawString(this.font, Component.translatable("gui.sre.mailbox.attachments"), rx + 8, attachY + 4, withAlpha(-2838729, ease));
            int itemX = rx + 8;
            int itemY = attachY + 16;
            int maxItems = Math.min(sel.attachments.size(), 12);

            for (int i = 0; i < maxItems; i++) {
               ItemStack stack = sel.attachments.get(i);
               int ix = itemX + i % 10 * 20;
               int iy = itemY + i / 10 * 20;
               g.renderItem(stack, ix, iy);
               g.renderItemDecorations(this.font, stack, ix, iy);
               if (mx >= ix && mx < ix + 16 && my >= iy && my < iy + 16) {
                  g.renderTooltip(this.font, stack, mx, my);
               }
            }

            if (sel.attachments.size() > 12) {
               g.drawString(this.font, "+" + (sel.attachments.size() - 12), itemX + 200, itemY + 4, withAlpha(-6386834, ease));
            }
         }

         g.disableScissor();
      }
   }

   private void renderFooterInfo(GuiGraphics g, float ease) {
      int fy = this.panelY + this.panelH - 42;
      int maxPage = this.cachedMails.isEmpty() ? 0 : (this.cachedMails.size() - 1) / this.rowsPerPage;
      g.drawCenteredString(this.font, Component.literal(this.page + 1 + " / " + (maxPage + 1)), this.lx() + this.leftW / 2, fy + 4, withAlpha(-6386834, ease));
      g.drawString(
         this.font,
         Component.translatable("gui.sre.mailbox.stats", new Object[]{this.cachedMails.size(), this.mailbox.getClaimableCount()}),
         this.rx() + 8,
         fy + 4,
         withAlpha(-6386834, ease)
      );
   }

   private void renderClaimFeedback(GuiGraphics g) {
      if (!(this.claimFeedbackTimer <= 0.0F)) {
         float t = this.claimFeedbackTimer / 70.0F;
         float alpha;
         if (t > 0.85F) {
            alpha = (1.0F - t) / 0.15F;
         } else if (t < 0.25F) {
            alpha = t / 0.25F;
         } else {
            alpha = 1.0F;
         }

         float offsetY = (1.0F - t) * 24.0F;
         int tw = this.font.width(this.claimFeedbackText);
         int tx = this.width / 2 - tw / 2;
         int ty = (int)((float)(this.panelY - 14) - offsetY);
         int bgAlpha = (int)(alpha * 187.0F);
         g.fill(tx - 7, ty - 4, tx + tw + 7, ty + 14, bgAlpha << 24 | 1050628);
         g.fill(tx - 6, ty - 3, tx + tw + 6, ty + 13, bgAlpha << 24 | 1708040);
         int glowC = (int)(alpha * 60.0F) << 24 | this.claimFeedbackColor & 16777215;
         g.drawString(this.font, this.claimFeedbackText, tx - 1, ty, glowC);
         g.drawString(this.font, this.claimFeedbackText, tx + 1, ty, glowC);
         g.drawString(this.font, this.claimFeedbackText, tx, ty - 1, glowC);
         g.drawString(this.font, this.claimFeedbackText, tx, ty + 1, glowC);
         int fgAlpha = (int)(alpha * 255.0F);
         g.drawString(this.font, this.claimFeedbackText, tx, ty, fgAlpha << 24 | this.claimFeedbackColor & 16777215);
      }
   }

   private void showFeedback(String text, int color) {
      this.claimFeedbackText = text;
      this.claimFeedbackColor = color;
      this.claimFeedbackTimer = 70.0F;
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (super.mouseClicked(mx, my, button)) {
         return true;
      } else {
         if (button == 0) {
            int relX = (int)mx - this.lx();
            int relY = (int)my - (this.panelY + 34);
            if (relX >= 2 && relX < this.leftW - 2 && relY >= 0 && relY < this.listAreaH) {
               int rowIdx = relY / 46;
               int globalIdx = this.page * this.rowsPerPage + rowIdx;
               if (rowIdx < this.rowsPerPage && globalIdx < this.cachedMails.size()) {
                  this.selectMail(globalIdx);
                  return true;
               }
            }
         }

         return false;
      }
   }

   private void selectMail(int idx) {
      if (this.selectedIdx != idx) {
         this.selectedIdx = idx;
         this.selectTick = 0;
         Mail mail = this.cachedMails.get(idx);
         if (!mail.read) {
            ClientPlayNetworking.send(new MailMarkReadC2SPayload(mail.id));
            mail.read = true;
         }

         this.init();
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 256) {
         this.onClose();
         return true;
      } else {
         if (!this.cachedMails.isEmpty()) {
            if (keyCode == 265 && this.selectedIdx > 0) {
               this.selectMail(this.selectedIdx - 1);
               return true;
            }

            if (keyCode == 264 && this.selectedIdx < this.cachedMails.size() - 1) {
               this.selectMail(this.selectedIdx + 1);
               return true;
            }
         }

         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static float easeOutCubic(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
   }

   private static int withAlpha(int argb, float ease) {
      int a = (int)((float)(argb >> 24 & 0xFF) * ease);
      return a << 24 | argb & 16777215;
   }

   private static int blendColors(int c1, int c2, float t) {
      if (t <= 0.0F) {
         return c1;
      } else if (t >= 1.0F) {
         return c2;
      } else {
         int a1 = c1 >> 24 & 0xFF;
         int r1 = c1 >> 16 & 0xFF;
         int g1 = c1 >> 8 & 0xFF;
         int b1 = c1 & 0xFF;
         int a2 = c2 >> 24 & 0xFF;
         int r2 = c2 >> 16 & 0xFF;
         int g2 = c2 >> 8 & 0xFF;
         int b2 = c2 & 0xFF;
         return (int)((float)a1 + (float)(a2 - a1) * t) << 24
            | (int)((float)r1 + (float)(r2 - r1) * t) << 16
            | (int)((float)g1 + (float)(g2 - g1) * t) << 8
            | (int)((float)b1 + (float)(b2 - b1) * t);
      }
   }

   private List<String> wrapText(String text, int maxWidth) {
      List<String> lines = new ArrayList<>();
      if (text != null && !text.isEmpty()) {
         for (String paragraph : text.split("\n")) {
            if (paragraph.isEmpty()) {
               lines.add("");
            } else {
               StringBuilder cur = new StringBuilder();
               int curW = 0;

               for (char c : paragraph.toCharArray()) {
                  int cw = this.font.width(String.valueOf(c));
                  if (curW + cw > maxWidth && !cur.isEmpty()) {
                     lines.add(cur.toString());
                     cur = new StringBuilder();
                     curW = 0;
                  }

                  cur.append(c);
                  curW += cw;
               }

               if (!cur.isEmpty()) {
                  lines.add(cur.toString());
               }
            }
         }

         return lines;
      } else {
         return lines;
      }
   }
}
