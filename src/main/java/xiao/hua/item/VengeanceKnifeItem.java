package xiao.hua.item;

import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class VengeanceKnifeItem extends KnifeItem implements SREItemProperties.LeftClickHurtable {
    public VengeanceKnifeItem(Properties settings) {
        super(settings);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext tooltipContext, List<Component> list,
            TooltipFlag tooltipFlag) {
        list.add(Component.translatable("item.huarolemods.vengeance_knife.desc").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(itemStack, tooltipContext, list, tooltipFlag);
    }
}
