package xiao.hua.init;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import xiao.hua.Huarolemods;
import xiao.hua.item.VengeanceLensItem;
import xiao.hua.item.VengeanceKnifeItem;

public class HuaItems {
    public static final Item TEST_ITEM = new Item(new Item.Properties());
    public static final Item VENGEANCE_LENS = new VengeanceLensItem();
    public static final Item VENGEANCE_KNIFE = new VengeanceKnifeItem(new Item.Properties().stacksTo(1));

    public static void register() {
        Huarolemods.LOGGER.info("Registering HuaRoleMods items...");
        registerItem("test_item", TEST_ITEM);
        registerItem("vengeance_lens", VENGEANCE_LENS);
        registerItem("vengeance_knife", VENGEANCE_KNIFE);
        Huarolemods.LOGGER.info("HuaRoleMods items registered successfully!");
    }

    private static void registerItem(String name, Item item) {
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("huarolemods", name), item);
    }
}